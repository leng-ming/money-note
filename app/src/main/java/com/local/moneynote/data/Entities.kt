package com.local.moneynote.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** 收支方向 */
enum class TxKind { EXPENSE, INCOME }

/** 账户类型 */
enum class AccountType { CASH, WECHAT, ALIPAY, BANK, CREDIT, OTHER }

/** 预算周期 */
enum class BudgetPeriod { MONTHLY, YEARLY }

/** 周期账单频率 */
enum class RecurFreq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * 账户。余额不落库，一律由「初始余额 + 流水汇总」实时算出，
 * 避免出现余额与账单对不上的经典脏数据问题。
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: AccountType = AccountType.CASH,
    @ColumnInfo(name = "initial_balance_cents") val initialBalanceCents: Long = 0L,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#FF607D8B",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** 分类。isSystem 的预置分类不允许删除，只能改图标/颜色 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val kind: TxKind,
    @ColumnInfo(name = "icon_key") val iconKey: String = "more",
    @ColumnInfo(name = "color_hex") val colorHex: String = "#FF607D8B",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false
)

/** 一笔账单。金额恒为正，方向由 kind 决定 —— 避免正负号散落各处导致统计出错 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("occurred_at"),
        Index("category_id"),
        Index("account_id")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val kind: TxKind,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val note: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "exclude_from_stats") val excludeFromStats: Boolean = false
)

/** 预算。categoryId 为 null 表示「总预算」 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** 周期账单规则：到点自动生成一笔真实账单 */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val kind: TxKind,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val note: String = "",
    val freq: RecurFreq = RecurFreq.MONTHLY,
    @ColumnInfo(name = "interval_count") val intervalCount: Int = 1,
    @ColumnInfo(name = "next_occur_at") val nextOccurAt: Long,
    @ColumnInfo(name = "last_generated_at") val lastGeneratedAt: Long? = null,
    val enabled: Boolean = true
)

/** Room 的枚举/可空转换器 */
class Converters {
    @TypeConverter fun kindToString(v: TxKind): String = v.name
    @TypeConverter fun stringToKind(v: String): TxKind = TxKind.valueOf(v)

    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun periodToString(v: BudgetPeriod): String = v.name
    @TypeConverter fun stringToPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)

    @TypeConverter fun freqToString(v: RecurFreq): String = v.name
    @TypeConverter fun stringToFreq(v: String): RecurFreq = RecurFreq.valueOf(v)
}
