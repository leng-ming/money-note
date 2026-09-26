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
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY kind, sort_order, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE kind = :kind AND archived = 0 ORDER BY sort_order, id")
    fun observeByKind(kind: TxKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind, archived, sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

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
            "c.name AS categoryName, c.icon_key AS categoryIcon, c.color_hex AS categoryColor, " +
            "a.name AS accountName " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
            "JOIN accounts a ON a.id = t.account_id " +
            "WHERE t.occurred_at >= :start AND t.occurred_at < :end " +
            "ORDER BY t.occurred_at DESC, t.id DESC"
    )
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionRow>>

    @Query(
        "SELECT t.id AS id, t.amount_cents AS amountCents, t.kind AS kind, " +
            "t.account_id AS accountId, t.category_id AS categoryId, t.note AS note, " +
            "t.occurred_at AS occurredAt, t.exclude_from_stats AS excludeFromStats, " +
            "c.name AS categoryName, c.icon_key AS categoryIcon, c.color_hex AS categoryColor, " +
            "a.name AS accountName " +
            "FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id " +
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
        "SELECT c.id AS categoryId, c.name AS name, c.icon_key AS iconKey, c.color_hex AS colorHex, " +
            "SUM(t.amount_cents) AS totalCents, COUNT(t.id) AS cnt " +
            "FROM transactions t JOIN categories c ON c.id = t.category_id " +
            "WHERE t.kind = :kind AND t.exclude_from_stats = 0 " +
            "AND t.occurred_at >= :start AND t.occurred_at < :end " +
            "GROUP BY c.id ORDER BY totalCents DESC"
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
