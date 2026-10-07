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
    val categoryId: Long,
    val note: String,
    val occurredAt: Long,
    val excludeFromStats: Boolean,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val accountName: String
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

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :id")
    suspend fun txCount(id: Long): Int

    @Query("SELECT * FROM accounts")
    suspend fun allOnce(): List<AccountEntity>

    @Query(
        "SELECT account_id AS accountId, " +
            "COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amount_cents ELSE -amount_cents END), 0) AS netCents " +
            "FROM transactions GROUP BY account_id"
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
            "t.account_id AS accountId, t.category_id AS categoryId, t.note AS note, " +
            "t.occurred_at AS occurredAt, t.exclude_from_stats AS excludeFromStats, " +
            "c.name AS categoryName, " +
            "COALESCE(p.icon_key, c.icon_key) AS categoryIcon, " +
            "COALESCE(p.color_hex, c.color_hex) AS categoryColor, " +
            "a.name AS accountName " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories p ON p.id = c.parent_id " +
            "JOIN accounts a ON a.id = t.account_id " +
            "WHERE t.occurred_at >= :start AND t.occurred_at < :end " +
            "ORDER BY t.occurred_at DESC, t.id DESC"
    )
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionRow>>

    @Query(
        "SELECT t.id AS id, t.amount_cents AS amountCents, t.kind AS kind, " +
            "t.account_id AS accountId, t.category_id AS categoryId, t.note AS note, " +
            "t.occurred_at AS occurredAt, t.exclude_from_stats AS excludeFromStats, " +
            "c.name AS categoryName, " +
            "COALESCE(p.icon_key, c.icon_key) AS categoryIcon, " +
            "COALESCE(p.color_hex, c.color_hex) AS categoryColor, " +
            "a.name AS accountName " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories p ON p.id = c.parent_id " +
            "JOIN accounts a ON a.id = t.account_id " +
            "WHERE t.occurred_at >= :start AND t.occurred_at < :end " +
            "AND (:keyword = '' OR t.note LIKE '%' || :keyword || '%' OR c.name LIKE '%' || :keyword || '%') " +
            "AND (:categoryId IS NULL OR t.category_id = :categoryId) " +
            "AND (:accountId IS NULL OR t.account_id = :accountId) " +
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
