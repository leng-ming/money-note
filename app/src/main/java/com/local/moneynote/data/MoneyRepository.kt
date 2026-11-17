package com.local.moneynote.data

import androidx.room.withTransaction
import com.local.moneynote.core.Dates
import com.local.moneynote.core.loanNote
import com.local.moneynote.core.loanSettleNote
import com.local.moneynote.core.settleAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 备份用的整库快照 */
data class BackupPayload(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>,
    /** 给默认值，这样 v1.1 之前导出的老备份文件仍然能正常解析 */
    val budgetCarryovers: List<BudgetCarryoverEntity> = emptyList(),
    /** 备份格式 v2 才有借还记录；老备份里缺这一段，当成没有即可 */
    val loans: List<LoanEntity> = emptyList()
)

/**
 * 唯一的本地数据入口。全部数据只存在这台手机的 SQLite 文件里，
 * 不存在任何网络调用 —— 飞行模式下功能完全一致。
 */
class MoneyRepository(private val db: AppDatabase) {

    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val budgets = db.budgetDao()
    private val recurring = db.recurringDao()
    private val carryovers = db.budgetCarryoverDao()
    private val loans = db.loanDao()

    /* ---------------- 账户 ---------------- */

    fun observeAccounts(): Flow<List<AccountEntity>> = accounts.observeActive()
    fun observeAllAccounts(): Flow<List<AccountEntity>> = accounts.observeAll()
    fun observeNetByAccount(): Flow<List<AccountNet>> = accounts.observeNetByAccount()

    suspend fun addAccount(name: String, type: AccountType, initialCents: Long): Long {
        val nextOrder = (accounts.allOnce().maxOfOrNull { it.sortOrder } ?: 0) + 1
        return accounts.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                initialBalanceCents = initialCents,
                sortOrder = nextOrder
            )
        )
    }

    suspend fun updateAccount(e: AccountEntity) = accounts.update(e)
    suspend fun accountById(id: Long) = accounts.byId(id)
    suspend fun accountTxCount(id: Long) = accounts.txCount(id)
    /** 账户被历史账单或周期规则引用时不能物理删除。 */
    suspend fun accountReferenceCount(id: Long): Int =
        accounts.txCount(id) + recurring.allOnce().count { it.accountId == id }
    suspend fun deleteAccount(e: AccountEntity) = accounts.delete(e)

    /* ---------------- 分类 ---------------- */

    fun observeCategories(): Flow<List<CategoryEntity>> = categories.observeActive()
    fun observeCategoriesOf(kind: TxKind): Flow<List<CategoryEntity>> = categories.observeByKind(kind)
    fun observeAllCategories(): Flow<List<CategoryEntity>> = categories.observeAll()

    suspend fun addCategory(name: String, kind: TxKind, iconKey: String, colorHex: String): Long {
        val nextOrder = (categories.allOnce().filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return categories.insert(
            CategoryEntity(
                name = name.trim(), kind = kind, iconKey = iconKey,
                colorHex = colorHex, sortOrder = nextOrder
            )
        )
    }

    suspend fun updateCategory(e: CategoryEntity) = categories.update(e)
    suspend fun categoryById(id: Long) = categories.byId(id)
    suspend fun categoryTxCount(id: Long) = categories.txCount(id)
    /** 收集一个分类及其全部后代的 id（含自身）。眼下最多两级，但按通用写法处理，将来加层级不用改这里。 */
    private fun subtreeIds(rootId: Long, all: List<CategoryEntity>): Set<Long> {
        val ids = mutableSetOf(rootId)
        var added = true
        while (added) {
            added = false
            all.forEach { if (it.parentId in ids && ids.add(it.id)) added = true }
        }
        return ids
    }

    /** 检查父分类整棵子树及周期账单引用，避免删除后留下孤儿账单。 */
    suspend fun categoryReferenceCount(id: Long): Int {
        val ids = subtreeIds(id, categories.allOnce())
        val transactionRefs = ids.sumOf { categories.txCount(it) }
        val recurringRefs = recurring.allOnce().count { it.categoryId in ids }
        return transactionRefs + recurringRefs
    }

    /**
     * 删除一个分类及其整棵子树，并连带清掉挂在这些分类上的预算与预算转结。
     *
     * 引用检查只看账单和周期规则，但那两样之外还有预算：
     * 预算行存的是 category_id，分类删了它就指向一个不存在的分类 ——
     * 预算页查不到名字，表现出来的就是一行空白。所以这里必须连带动手。
     * 整个过程放在一个事务里，避免删到一半留下半棵树。
     */
    suspend fun deleteCategoryTree(rootId: Long) = db.withTransaction {
        val all = categories.allOnce()
        val ids = subtreeIds(rootId, all)
        budgets.allOnce().filter { it.categoryId?.let(ids::contains) == true }
            .forEach { budgets.delete(it) }
        carryovers.allOnce().filter { it.categoryId?.let(ids::contains) == true }
            .forEach { carryovers.delete(it) }
        all.filter { it.id in ids }.forEach { categories.delete(it) }
    }

    suspend fun deleteCategory(e: CategoryEntity) = categories.delete(e)

    /** 在某个一级分类下新增二级细分；kind 与图标配色都继承父分类 */
    suspend fun addSubCategory(parentId: Long, name: String, iconKey: String, colorHex: String): Long {
        val all = categories.allOnce()
        val parent = all.firstOrNull { it.id == parentId } ?: return -1L
        val siblings = all.filter { it.parentId == parentId }
        val nextOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return categories.insert(
            CategoryEntity(
                name = name.trim(),
                kind = parent.kind,
                parentId = parentId,
                iconKey = iconKey,
                colorHex = colorHex,
                sortOrder = nextOrder
            )
        )
    }

    /* ---------------- 账单 ---------------- */

    fun observeTransactions(start: Long, end: Long): Flow<List<TransactionRow>> =
        transactions.observeBetween(start, end)

    fun search(
        start: Long, end: Long, keyword: String,
        categoryId: Long?, accountId: Long?, kind: TxKind?,
        minCents: Long, maxCents: Long?
    ): Flow<List<TransactionRow>> =
        transactions.search(start, end, keyword, categoryId, accountId, kind, minCents, maxCents)

    fun observeTotal(kind: TxKind, start: Long, end: Long): Flow<Long> =
        transactions.observeTotal(kind, start, end)

    fun observeSumByCategory(kind: TxKind, start: Long, end: Long): Flow<List<CategorySum>> =
        transactions.observeSumByCategory(kind, start, end)

    /** 精确到每个分类、不归并到一级。分类预算判定已用要用它 */
    fun observeSumByExactCategory(kind: TxKind, start: Long, end: Long): Flow<List<CategorySum>> =
        transactions.observeSumByExactCategory(kind, start, end)

    fun observeSumByDay(kind: TxKind, start: Long, end: Long): Flow<List<DaySum>> =
        transactions.observeSumByDay(kind, start, end)

    fun observeSumByMonth(kind: TxKind, start: Long, end: Long): Flow<List<MonthSum>> =
        transactions.observeSumByMonth(kind, start, end)

    /* ---------------- 账户详情 ---------------- */

    fun observeSumByMonthForAccount(
        accountId: Long,
        kind: TxKind,
        start: Long,
        end: Long
    ): Flow<List<MonthSum>> =
        transactions.observeSumByMonthForAccount(accountId, kind, start, end)

    fun observeTotalForAccount(accountId: Long, kind: TxKind): Flow<Long> =
        transactions.observeTotalForAccount(accountId, kind)

    fun observeEarliestForAccount(accountId: Long): Flow<Long?> =
        transactions.observeEarliestForAccount(accountId)

    fun observeSpent(categoryId: Long?, start: Long, end: Long): Flow<Long> =
        transactions.observeSpent(categoryId, start, end)

    suspend fun transactionById(id: Long) = transactions.byId(id)

    /**
     * 记账时预选账户用：优先返回该分类上次使用的账户。
     * 没有任何历史时返回 null，由界面退回第一个账户。
     */
    suspend fun lastAccountIdForCategory(categoryId: Long): Long? =
        transactions.lastAccountIdForCategory(categoryId)

    suspend fun lastAccountId(): Long? = transactions.lastAccountId()

    suspend fun addTransaction(
        amountCents: Long, kind: TxKind, accountId: Long, categoryId: Long?,
        note: String, occurredAt: Long, excludeFromStats: Boolean = false,
        // 仅转账用：转入账户与手续费。实际到账 = amountCents - feeCents
        toAccountId: Long? = null, feeCents: Long = 0L
    ): Long = transactions.insert(
        TransactionEntity(
            amountCents = amountCents,
            kind = kind,
            accountId = accountId,
            toAccountId = if (kind == TxKind.TRANSFER) toAccountId else null,
            feeCents = if (kind == TxKind.TRANSFER) feeCents.coerceAtLeast(0L) else 0L,
            categoryId = categoryId,
            note = note.trim(),
            occurredAt = occurredAt,
            excludeFromStats = excludeFromStats
        )
    )

    suspend fun updateTransaction(e: TransactionEntity) {
        // 借还流水必须通过借还页修改，否则本金/已还金额会和账户流水分离。
        if (e.loanId != null) return
        transactions.update(e.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(e: TransactionEntity) = db.withTransaction {
        if (e.loanId != null) {
            transactions.deleteByLoanId(e.loanId)
            loans.byId(e.loanId)?.let { loans.delete(it) }
        } else {
            transactions.delete(e)
        }
    }

    /* ---------------- 预算 ---------------- */

    fun observeBudgets(): Flow<List<BudgetEntity>> = budgets.observeAll()
    fun observeEnabledBudgets(): Flow<List<BudgetEntity>> = budgets.observeEnabled()

    /** 同一分类只保留一条预算；已存在则覆盖金额 */
    suspend fun upsertBudget(categoryId: Long?, amountCents: Long, period: BudgetPeriod) {
        val existing = budgets.allOnce().firstOrNull { it.categoryId == categoryId }
        if (existing == null) {
            budgets.insert(BudgetEntity(categoryId = categoryId, amountCents = amountCents, period = period))
        } else {
            budgets.update(existing.copy(amountCents = amountCents, period = period, enabled = true))
        }
    }

    suspend fun deleteBudget(e: BudgetEntity) = budgets.delete(e)

    /* ---------------- 预算转结 ---------------- */

    /** 某月（某分类）从以前月份转进来的预算总额 */
    fun observeCarryoverIncoming(categoryId: Long?, year: Int, month: Int): Flow<Long> =
        carryovers.observeIncoming(categoryId, year, month)

    /** 某月是否已经往外转过（按钮据此显示「已转结」） */
    fun observeCarryoverOutgoingExists(categoryId: Long?, year: Int, month: Int): Flow<Boolean> =
        carryovers.observeOutgoingExists(categoryId, year, month)

    /**
     * 把某月剩余的预算转结到下个月。
     *
     * 同一来源月份只允许转一次，否则用户连点几下就会把同一笔剩余重复叠加。
     * 返回 false 表示没转（金额为 0 或之前已经转过）。
     */
    suspend fun carryOverBudget(
        categoryId: Long?,
        fromYear: Int,
        fromMonth: Int,
        amountCents: Long
    ): Boolean {
        if (amountCents <= 0L) return false
        val already = carryovers.allOnce().any {
            it.categoryId == categoryId && it.fromYear == fromYear && it.fromMonth == fromMonth
        }
        if (already) return false

        val (toYear, toMonth) = Dates.shiftMonth(fromYear, fromMonth, 1)
        carryovers.insert(
            BudgetCarryoverEntity(
                categoryId = categoryId,
                fromYear = fromYear,
                fromMonth = fromMonth,
                toYear = toYear,
                toMonth = toMonth,
                amountCents = amountCents
            )
        )
        return true
    }

    /** 撤销某月往外转的记录（转错了可以撤回） */
    suspend fun undoCarryOver(categoryId: Long?, fromYear: Int, fromMonth: Int) {
        carryovers.allOnce()
            .filter {
                it.categoryId == categoryId && it.fromYear == fromYear && it.fromMonth == fromMonth
            }
            .forEach { carryovers.delete(it) }
    }

    /* ---------------- 周期账单 ---------------- */

    fun observeRecurring(): Flow<List<RecurringEntity>> = recurring.observeAll()
    suspend fun addRecurring(e: RecurringEntity): Long = recurring.insert(e)
    suspend fun updateRecurring(e: RecurringEntity) = recurring.update(e)
    suspend fun deleteRecurring(e: RecurringEntity) = recurring.delete(e)

    /**
     * 把所有已到期的周期规则补记成真实账单。
     * guard 用来防止某条规则的 nextOccurAt 因数据异常停留在远古时间而陷入死循环。
     */
    suspend fun materializeDueRecurring(now: Long = System.currentTimeMillis()): Int = db.withTransaction {
        var created = 0
        for (rule in recurring.due(now)) {
            var next = rule.nextOccurAt
            var last = rule.lastGeneratedAt
            var guard = 0
            while (next <= now && guard < 400) {
                // 「这期临时改金额」只对最靠前的那一期生效，之后的期恢复原金额
                val amount = if (next == rule.nextOccurAt) {
                    rule.nextAmountCents ?: rule.amountCents
                } else {
                    rule.amountCents
                }
                transactions.insert(
                    TransactionEntity(
                        amountCents = amount,
                        kind = rule.kind,
                        accountId = rule.accountId,
                        categoryId = rule.categoryId,
                        note = rule.note.ifBlank { rule.name },
                        occurredAt = next
                    )
                )
                created++
                last = next
                next = Dates.nextOccurrence(next, rule.freq, rule.intervalCount)
                guard++
            }
            recurring.update(
                rule.copy(
                    nextOccurAt = next,
                    lastGeneratedAt = last,
                    // 一次性金额用掉就清空，别污染后面的每一期
                    nextAmountCents = null
                )
            )
        }
        created
    }

    /**
     * 跳过下一期：不生成账单，直接把下次时间推到再下一期。
     *
     * 用处是「这个月不住」「已经退订但懒得改规则」这种 ——
     * 与其让它冒出一笔还得事后删掉的账，不如直接跳过去。
     */
    suspend fun skipNextRecurring(rule: RecurringEntity) {
        val next = Dates.nextOccurrence(rule.nextOccurAt, rule.freq, rule.intervalCount)
        recurring.update(rule.copy(nextOccurAt = next, nextAmountCents = null))
    }

    /** 只改下一期的金额；传 null 表示恢复原金额 */
    suspend fun setRecurringNextAmount(rule: RecurringEntity, cents: Long?) {
        recurring.update(rule.copy(nextAmountCents = cents))
    }

    /* ---------------- 借还 ---------------- */

    fun observeLoans(): Flow<List<LoanEntity>> = loans.observeAll()

    /**
     * 新增一条借还记录，并生成对应的真实流水。
     *
     * 两件事必须在同一个事务里：只插记录不插流水，账面上钱没动（余额就错了）；
     * 只插流水不插记录，就成了一笔「莫名其妙的大额支出」，将来也不知道该找谁要。
     */
    suspend fun addLoan(
        direction: LoanDirection,
        person: String,
        amountCents: Long,
        accountId: Long,
        occurredAt: Long,
        dueAt: Long? = null,
        note: String = ""
    ): Long = db.withTransaction {
        val cleanPerson = person.trim()
        val id = loans.insert(
            LoanEntity(
                direction = direction,
                person = cleanPerson,
                amountCents = amountCents,
                accountId = accountId,
                occurredAt = occurredAt,
                dueAt = dueAt,
                note = note.trim()
            )
        )
        transactions.insert(
            TransactionEntity(
                amountCents = amountCents,
                // 借出 = 我的钱出去了；借入 = 我的钱进来了
                kind = if (direction == LoanDirection.LENT) TxKind.EXPENSE else TxKind.INCOME,
                accountId = accountId,
                categoryId = null,
                note = loanNote(direction, cleanPerson),
                occurredAt = occurredAt,
                // 关键：既不算收入也不算支出。否则一笔「借出去、将来还回来」的钱
                // 会把当月支出顶高、让预算当场爆掉，可它根本不是消费。
                excludeFromStats = true,
                loanId = id
            )
        )
        id
    }

    /**
     * 收回欠款 / 还掉欠账，按实际结算金额生成一笔流水。支持部分结算。
     *
     * @return 实际结算了多少（已夹到未还上限）；记录不存在或金额为 0 时返回 0
     */
    suspend fun settleLoan(loanId: Long, requestedCents: Long, occurredAt: Long): Long =
        db.withTransaction {
            val loan = loans.byId(loanId) ?: return@withTransaction 0L
            val actual = settleAmount(loan, requestedCents)
            if (actual <= 0L) return@withTransaction 0L

            loans.update(loan.copy(settledCents = loan.settledCents + actual))
            transactions.insert(
                TransactionEntity(
                    amountCents = actual,
                    // 别人还我钱 = 我进账；我还别人钱 = 我出账
                    kind = if (loan.direction == LoanDirection.LENT) TxKind.INCOME else TxKind.EXPENSE,
                    accountId = loan.accountId,
                    categoryId = null,
                    note = loanSettleNote(loan.direction, loan.person),
                    occurredAt = occurredAt,
                    excludeFromStats = true,
                    loanId = loan.id
                )
            )
            actual
        }

    /**
     * 撤销最近一次收回/还款。
     *
     * 记错金额时的补救办法：撤掉最后一笔结算，金额退回去，再按正确数字重记。
     * 只允许撤最后一笔 —— 从中间随便撤一笔，"已还多少"就跟流水对不上了。
     *
     * @return 撤销掉的金额；没有可撤的（只剩最开始那笔本金）时返回 0
     */
    suspend fun undoLastSettle(loanId: Long): Long = db.withTransaction {
        val loan = loans.byId(loanId) ?: return@withTransaction 0L
        val rows = transactions.byLoan(loanId)
        // 第一笔永远是创建记录时记的本金流水，它不代表"还款"，撤了账就乱了
        if (rows.size <= 1) return@withTransaction 0L

        val last = rows.last()
        transactions.delete(last)
        loans.update(
            loan.copy(settledCents = (loan.settledCents - last.amountCents).coerceAtLeast(0L))
        )
        last.amountCents
    }

    /**
     * 删除一条借还记录，连带撤掉它产生的全部流水。
     *
     * 不连带撤的话会出现「记录没了、可账户里那笔钱还是少的」——
     * 用户删掉记录，期望的是这件事当作没发生过。
     */
    suspend fun deleteLoan(loanId: Long) = db.withTransaction {
        transactions.deleteByLoanId(loanId)
        loans.byId(loanId)?.let { loans.delete(it) }
    }

    /* ---------------- 备份 ---------------- */

    suspend fun snapshotForBackup(): BackupPayload = db.withTransaction {
        BackupPayload(
            accounts = accounts.allOnce(),
            categories = categories.allOnce(),
            transactions = transactions.all(),
            budgets = budgets.allOnce(),
            recurring = recurring.allOnce(),
            budgetCarryovers = carryovers.allOnce(),
            loans = loans.allOnce()
        )
    }

    /**
     * 清空全部数据。
     *
     * 注意：`clearAllTables()` 是阻塞方法，Room 内部有 `assertNotMainThread()`，
     * 在主线程调用会直接抛 IllegalStateException。所以必须显式切到 IO 线程 ——
     * 调用方常来自 Compose 的 rememberCoroutineScope()，那是主线程调度器。
     */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    /** 用备份内容整体替换本地数据（导入恢复） */
    suspend fun restoreFrom(payload: BackupPayload) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // 清空与回填必须在同一个事务里，失败时自动回滚到恢复前的数据。
            db.clearAllTables()
            payload.accounts.forEach { accounts.insert(it) }
            payload.categories.forEach { categories.insert(it) }
            // 借还记录要在账单之前回填，账单的 loan_id 才有意义
            payload.loans.forEach { loans.insert(it) }
            payload.transactions.forEach { transactions.insert(it) }
            payload.budgets.forEach { budgets.insert(it) }
            payload.recurring.forEach { recurring.insert(it) }
            payload.budgetCarryovers.forEach { carryovers.insert(it) }
        }
    }
}
