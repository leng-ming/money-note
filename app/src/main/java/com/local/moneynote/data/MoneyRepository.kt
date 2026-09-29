package com.local.moneynote.data

import com.local.moneynote.core.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 备份用的整库快照 */
data class BackupPayload(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>
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

    fun observeSumByDay(kind: TxKind, start: Long, end: Long): Flow<List<DaySum>> =
        transactions.observeSumByDay(kind, start, end)

    fun observeSumByMonth(kind: TxKind, start: Long, end: Long): Flow<List<MonthSum>> =
        transactions.observeSumByMonth(kind, start, end)

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
        amountCents: Long, kind: TxKind, accountId: Long, categoryId: Long,
        note: String, occurredAt: Long, excludeFromStats: Boolean = false
    ): Long = transactions.insert(
        TransactionEntity(
            amountCents = amountCents,
            kind = kind,
            accountId = accountId,
            categoryId = categoryId,
            note = note.trim(),
            occurredAt = occurredAt,
            excludeFromStats = excludeFromStats
        )
    )

    suspend fun updateTransaction(e: TransactionEntity) =
        transactions.update(e.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteTransaction(e: TransactionEntity) = transactions.delete(e)

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

    /* ---------------- 周期账单 ---------------- */

    fun observeRecurring(): Flow<List<RecurringEntity>> = recurring.observeAll()
    suspend fun addRecurring(e: RecurringEntity): Long = recurring.insert(e)
    suspend fun updateRecurring(e: RecurringEntity) = recurring.update(e)
    suspend fun deleteRecurring(e: RecurringEntity) = recurring.delete(e)

    /**
     * 把所有已到期的周期规则补记成真实账单。
     * guard 用来防止某条规则的 nextOccurAt 因数据异常停留在远古时间而陷入死循环。
     */
    suspend fun materializeDueRecurring(now: Long = System.currentTimeMillis()): Int {
        var created = 0
        for (rule in recurring.due(now)) {
            var next = rule.nextOccurAt
            var last = rule.lastGeneratedAt
            var guard = 0
            while (next <= now && guard < 400) {
                transactions.insert(
                    TransactionEntity(
                        amountCents = rule.amountCents,
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
            recurring.update(rule.copy(nextOccurAt = next, lastGeneratedAt = last))
        }
        return created
    }

    /* ---------------- 备份 ---------------- */

    suspend fun snapshotForBackup(): BackupPayload = BackupPayload(
        accounts = accounts.allOnce(),
        categories = categories.allOnce(),
        transactions = transactions.all(),
        budgets = budgets.allOnce(),
        recurring = recurring.allOnce()
    )

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
        // clearAllTables 必须在 IO 线程；清空后按原 id 回填，保持账单与账户/分类的引用关系
        db.clearAllTables()
        payload.accounts.forEach { accounts.insert(it) }
        payload.categories.forEach { categories.insert(it) }
        payload.transactions.forEach { transactions.insert(it) }
        payload.budgets.forEach { budgets.insert(it) }
        payload.recurring.forEach { recurring.insert(it) }
    }
}
