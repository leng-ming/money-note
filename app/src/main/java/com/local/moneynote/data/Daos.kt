package com.local.moneynote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/* ------------------------------------------------------------------ */
/*  投影模型（查询结果，不是表）                                        */
/* ------------------------------------------------------------------ */

data class AccountNet(val accountId: Long, val netCents: Long)

/** 账单列表用的完整行（已 join 分类与账户名称） */
data class TransactionRow(
    val id: Long,
    val amountCents: Long,
    val kind: TxKind,
    val accountId: Long,
    /** 转账的转入账户，非转账为 null */
    val toAccountId: Long?,
    /** 转账手续费 */
    val feeCents: Long,
    /** 转账没有分类，所以可空 */
    val categoryId: Long?,
    val note: String,
    val occurredAt: Long,
    val excludeFromStats: Boolean,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val accountName: String,
    /** 转入账户名，非转账为空串 */
    val toAccountName: String
)

data class CategorySum(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val totalCents: Long,
    val cnt: Int
)

data class DaySum(val day: String, val totalCents: Long)

/** 按「年-月」汇总，年视图的趋势图用 */
data class MonthSum(val month: String, val totalCents: Long)

/* ------------------------------------------------------------------ */
/*  DAO                                                                */
/* ------------------------------------------------------------------ */

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort_order, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Insert
    suspend fun insert(e: AccountEntity): Long

    @Update
    suspend fun update(e: AccountEntity)

    @Delete
    suspend fun delete(e: AccountEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :id OR to_account_id = :id")
    suspend fun txCount(id: Long): Int

    @Query("SELECT * FROM accounts")
    suspend fun allOnce(): List<AccountEntity>

    /**
     * 每个账户的净变动（初始余额之外的流水合计）。
     *
     * 转账要算两次：转出账户扣全额，转入账户加「金额 − 手续费」。
     * 用 UNION ALL 拼成「账户 → 变动额」的长表再 GROUP BY，
     * 比在一个 CASE 里塞进所有情况清楚得多，也不容易写漏。
     */
    @Query(
        "SELECT acc AS accountId, COALESCE(SUM(delta), 0) AS netCents FROM (" +
            "SELECT account_id AS acc, -amount_cents AS delta FROM transactions WHERE kind = 'EXPENSE' " +
            "UNION ALL " +
            "SELECT account_id AS acc, amount_cents AS delta FROM transactions WHERE kind = 'INCOME' " +
            "UNION ALL " +
            "SELECT account_id AS acc, -amount_cents AS delta FROM transactions WHERE kind = 'TRANSFER' " +
            "UNION ALL " +
            "SELECT to_account_id AS acc, amount_cents - fee_cents AS delta FROM transactions " +
            "WHERE kind = 'TRANSFER' AND to_account_id IS NOT NULL" +
            ") GROUP BY acc"
    )
    fun observeNetByAccount(): Flow<List<AccountNet>>
}

@Dao
interface CategoryDao {
    /** 只返回一级分类：筛选器、预算、统计归并都按一级走，避免几十个细分把界面撑爆 */
    @Query("SELECT * FROM categories WHERE archived = 0 AND parent_id IS NULL ORDER BY kind, sort_order, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE kind = :kind AND archived = 0 AND parent_id IS NULL ORDER BY sort_order, id")
    fun observeByKind(kind: TxKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind, archived, parent_id IS NOT NULL, sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parent_id = :parentId AND archived = 0 ORDER BY sort_order, id")
    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(e: CategoryEntity): Long

    @Update
    suspend fun update(e: CategoryEntity)

    @Delete
    suspend fun delete(e: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun txCount(id: Long): Int

    @Query("SELECT * FROM categories")
    suspend fun allOnce(): List<CategoryEntity>
}

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(e: TransactionEntity): Long

    @Update
    suspend fun update(e: TransactionEntity)

    @Delete
    suspend fun delete(e: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE account_id = :accountId")
    suspend fun byAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurred_at ASC, id ASC")
    suspend fun all(): List<TransactionEntity>

    @Query(
        "SELECT t.id AS id, t.amount_cents AS amountCents, t.kind AS kind, " +
            "t.account_id AS accountId, t.to_account_id AS toAccountId, t.fee_cents AS feeCents, " +
            "t.category_id AS categoryId, t.note AS note, " +
            "t.occurred_at AS occurredAt, t.exclude_from_stats AS excludeFromStats, " +
            "COALESCE(c.name, '') AS categoryName, " +
            "COALESCE(p.icon_key, c.icon_key, 'more') AS categoryIcon, " +
            "COALESCE(p.color_hex, c.color_hex, '#FF607D8B') AS categoryColor, " +
            "a.name AS accountName, COALESCE(ta.name, '') AS toAccountName " +
            "FROM transactions t " +
            // 转账没有分类，所以这里必须是 LEFT JOIN，否则转账记录整条都查不出来
            "LEFT JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories p ON p.id = c.parent_id " +
            "JOIN accounts a ON a.id = t.account_id " +
            "LEFT JOIN accounts ta ON ta.id = t.to_account_id " +
            "WHERE t.occurred_at >= :start AND t.occurred_at < :end " +
            "ORDER BY t.occurred_at DESC, t.id DESC"
    )
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionRow>>

    @Query(
        "SELECT t.id AS id, t.amount_cents AS amountCents, t.kind AS kind, " +
            "t.account_id AS accountId, t.to_account_id AS toAccountId, t.fee_cents AS feeCents, " +
            "t.category_id AS categoryId, t.note AS note, " +
            "t.occurred_at AS occurredAt, t.exclude_from_stats AS excludeFromStats, " +
            "COALESCE(c.name, '') AS categoryName, " +
            "COALESCE(p.icon_key, c.icon_key, 'more') AS categoryIcon, " +
            "COALESCE(p.color_hex, c.color_hex, '#FF607D8B') AS categoryColor, " +
            "a.name AS accountName, COALESCE(ta.name, '') AS toAccountName " +
            "FROM transactions t " +
            "LEFT JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories p ON p.id = c.parent_id " +
            "JOIN accounts a ON a.id = t.account_id " +
            "LEFT JOIN accounts ta ON ta.id = t.to_account_id " +
            "WHERE t.occurred_at >= :start AND t.occurred_at < :end " +
            "AND (:keyword = '' OR t.note LIKE '%' || :keyword || '%' OR c.name LIKE '%' || :keyword || '%') " +
            "AND (:categoryId IS NULL OR t.category_id = :categoryId) " +
            "AND (:accountId IS NULL OR t.account_id = :accountId OR t.to_account_id = :accountId) " +
            "AND (:kind IS NULL OR t.kind = :kind) " +
            "AND t.amount_cents >= :minCents " +
            "AND (:maxCents IS NULL OR t.amount_cents <= :maxCents) " +
            "ORDER BY t.occurred_at DESC, t.id DESC"
    )
    fun search(
        start: Long,
        end: Long,
        keyword: String,
        categoryId: Long?,
        accountId: Long?,
        kind: TxKind?,
        minCents: Long,
        maxCents: Long?
    ): Flow<List<TransactionRow>>

    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM transactions " +
            "WHERE kind = :kind AND exclude_from_stats = 0 " +
            "AND occurred_at >= :start AND occurred_at < :end"
    )
    fun observeTotal(kind: TxKind, start: Long, end: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(p.id, c.id) AS categoryId, " +
            "COALESCE(p.name, c.name) AS name, " +
            "COALESCE(p.icon_key, c.icon_key) AS iconKey, " +
            "COALESCE(p.color_hex, c.color_hex) AS colorHex, " +
            "SUM(t.amount_cents) AS totalCents, COUNT(t.id) AS cnt " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories p ON p.id = c.parent_id " +
            "WHERE t.kind = :kind AND t.exclude_from_stats = 0 " +
            "AND t.occurred_at >= :start AND t.occurred_at < :end " +
            "GROUP BY COALESCE(p.id, c.id) ORDER BY totalCents DESC"
    )
    fun observeSumByCategory(kind: TxKind, start: Long, end: Long): Flow<List<CategorySum>>

    @Query(
        "SELECT strftime('%Y-%m-%d', occurred_at / 1000, 'unixepoch', 'localtime') AS day, " +
            "SUM(amount_cents) AS totalCents " +
            "FROM transactions " +
            "WHERE kind = :kind AND exclude_from_stats = 0 " +
            "AND occurred_at >= :start AND occurred_at < :end " +
            "GROUP BY day ORDER BY day ASC"
    )
    fun observeSumByDay(kind: TxKind, start: Long, end: Long): Flow<List<DaySum>>

    /**
     * 按「精确分类」汇总，**不归并到一级**。
     *
     * 分类预算必须用这个：上面的 [observeSumByCategory] 把二级分类的账都算到一级头上，
     * 于是给二级分类（例如「购物 → 数码」）设的预算，已用永远显示 0。
     * 这里保留每个分类自己的金额，由界面按「自己 + 所有子分类」合成，
     * 这样无论预算挂在一级还是二级都算得对。
     */
    @Query(
        "SELECT t.category_id AS categoryId, c.name AS name, " +
            "c.icon_key AS iconKey, c.color_hex AS colorHex, " +
            "SUM(t.amount_cents) AS totalCents, COUNT(t.id) AS cnt " +
            "FROM transactions t JOIN categories c ON c.id = t.category_id " +
            "WHERE t.kind = :kind AND t.exclude_from_stats = 0 " +
            "AND t.occurred_at >= :start AND t.occurred_at < :end " +
            "GROUP BY t.category_id ORDER BY totalCents DESC"
    )
    fun observeSumByExactCategory(kind: TxKind, start: Long, end: Long): Flow<List<CategorySum>>

    /** 某分类（或全部分类）在区间内的已用支出，预算用 */
    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM transactions " +
            "WHERE kind = 'EXPENSE' AND exclude_from_stats = 0 " +
            "AND (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND occurred_at >= :start AND occurred_at < :end"
    )
    fun observeSpent(categoryId: Long?, start: Long, end: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    /**
     * 某分类最近一次使用的账户。
     * 记账时用它自动带出账户，省得每次重复选择（午饭用微信，晚饭大概率还是微信）。
     */
    @Query(
        "SELECT account_id FROM transactions WHERE category_id = :categoryId " +
            "ORDER BY occurred_at DESC, id DESC LIMIT 1"
    )
    suspend fun lastAccountIdForCategory(categoryId: Long): Long?

    /** 全局最近一次使用的账户，作为分类没有历史记录时的兜底 */
    @Query("SELECT account_id FROM transactions ORDER BY occurred_at DESC, id DESC LIMIT 1")
    suspend fun lastAccountId(): Long?

    /** 某年各月的支出/收入汇总，供「年」视图的趋势图使用 */
    @Query(
        "SELECT strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime') AS month, " +
            "SUM(amount_cents) AS totalCents " +
            "FROM transactions " +
            "WHERE kind = :kind AND exclude_from_stats = 0 " +
            "AND occurred_at >= :start AND occurred_at < :end " +
            "GROUP BY month ORDER BY month ASC"
    )
    fun observeSumByMonth(kind: TxKind, start: Long, end: Long): Flow<List<MonthSum>>

    /**
     * 单个账户在某一年里各月的支出/收入汇总。
     * 账户详情页用它回答「这张卡今年每个月花掉多少」。
     */
    @Query(
        "SELECT strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime') AS month, " +
            "SUM(amount_cents) AS totalCents " +
            "FROM transactions " +
            "WHERE account_id = :accountId AND kind = :kind AND exclude_from_stats = 0 " +
            "AND occurred_at >= :start AND occurred_at < :end " +
            "GROUP BY month ORDER BY month ASC"
    )
    fun observeSumByMonthForAccount(
        accountId: Long,
        kind: TxKind,
        start: Long,
        end: Long
    ): Flow<List<MonthSum>>

    /** 单个账户有史以来的收支合计，账户详情页顶部用 */
    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM transactions " +
            "WHERE account_id = :accountId AND kind = :kind AND exclude_from_stats = 0"
    )
    fun observeTotalForAccount(accountId: Long, kind: TxKind): Flow<Long>

    /** 某账户交易里最早的一笔时间，用来决定年份选择器的起始年 */
    @Query("SELECT MIN(occurred_at) FROM transactions WHERE account_id = :accountId")
    fun observeEarliestForAccount(accountId: Long): Flow<Long?>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY category_id IS NOT NULL, id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun allOnce(): List<BudgetEntity>

    @Insert
    suspend fun insert(e: BudgetEntity): Long

    @Update
    suspend fun update(e: BudgetEntity)

    @Delete
    suspend fun delete(e: BudgetEntity)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY enabled DESC, next_occur_at ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE enabled = 1 AND next_occur_at <= :now ORDER BY next_occur_at ASC")
    suspend fun due(now: Long): List<RecurringEntity>

    @Query("SELECT * FROM recurring")
    suspend fun allOnce(): List<RecurringEntity>

    @Insert
    suspend fun insert(e: RecurringEntity): Long

    @Update
    suspend fun update(e: RecurringEntity)

    @Delete
    suspend fun delete(e: RecurringEntity)
}

@Dao
interface BudgetCarryoverDao {
    @Query("SELECT * FROM budget_carryovers ORDER BY to_year DESC, to_month DESC, id DESC")
    fun observeAll(): Flow<List<BudgetCarryoverEntity>>

    /** 某月（某分类）从别处转进来的总额 */
    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM budget_carryovers " +
            "WHERE to_year = :year AND to_month = :month " +
            "AND ((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId)"
    )
    fun observeIncoming(categoryId: Long?, year: Int, month: Int): Flow<Long>

    /** 某月是否已经往外转过 —— 防止把同一笔剩余重复转两次 */
    @Query(
        "SELECT COUNT(*) > 0 FROM budget_carryovers " +
            "WHERE from_year = :year AND from_month = :month " +
            "AND ((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId)"
    )
    fun observeOutgoingExists(categoryId: Long?, year: Int, month: Int): Flow<Boolean>

    @Query("SELECT * FROM budget_carryovers")
    suspend fun allOnce(): List<BudgetCarryoverEntity>

    @Insert
    suspend fun insert(e: BudgetCarryoverEntity): Long

    @Delete
    suspend fun delete(e: BudgetCarryoverEntity)
}
