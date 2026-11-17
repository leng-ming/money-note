package com.local.moneynote.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * 账目方向。
 *
 * TRANSFER 是「账户之间挪钱」（比如微信 → 银行卡），它既不是支出也不是收入，
 * 所以所有统计查询都按 kind 过滤、天然把它排除在外 —— 转账不该影响收支报表。
 */
enum class TxKind { EXPENSE, INCOME, TRANSFER }

/**
 * 借还的方向。
 *
 * - LENT：我借给别人的钱 → 对我而言是**应收**（对方欠我）
 * - BORROWED：我向别人借的钱 → 是**应付**（我欠对方）
 */
enum class LoanDirection { LENT, BORROWED }

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

/**
 * 分类。支持两级：
 * - parentId == null → 一级分类（餐饮、交通…）
 * - parentId != null → 二级分类（奶茶、咖啡…），挂在某个一级分类下
 *
 * isSystem 的预置分类不允许删除，只能改图标/颜色
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val kind: TxKind,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
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
    /** 支出/收入的所属账户；转账时表示**转出**账户 */
    @ColumnInfo(name = "account_id") val accountId: Long,
    /** 转账的**转入**账户，非转账时为 null */
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    /** 转账手续费。实际到账 = amountCents - feeCents */
    @ColumnInfo(name = "fee_cents") val feeCents: Long = 0L,
    /** 转账没有分类，所以这里允许为 null */
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val note: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "exclude_from_stats") val excludeFromStats: Boolean = false,
    /**
     * 这笔账单由哪条借还记录自动生成（指向 loans.id），普通账单为 null。
     *
     * 有了它才能做到：删除一条借还记录时，把它连带产生的那几笔流水一起撤掉。
     * 否则删掉记录、流水还在，账户余额就跟"看起来什么都没有"的状态对不上了。
     */
    @ColumnInfo(name = "loan_id") val loanId: Long? = null
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

/**
 * 预算转结记录：把某个自然月没用完的预算挪到下个月。
 *
 * 单独建表而不是给 BudgetEntity 加字段，是因为两者生命周期不同：
 * 预算是「每月复用的一份设置」，而转结是逐月累积的历史，需要记住每一笔的来源月份。
 */
@Entity(tableName = "budget_carryovers")
data class BudgetCarryoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** null = 总预算；非 null = 该分类的预算 */
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "from_year") val fromYear: Int,
    @ColumnInfo(name = "from_month") val fromMonth: Int,
    @ColumnInfo(name = "to_year") val toYear: Int,
    @ColumnInfo(name = "to_month") val toMonth: Int,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * 借还记录：我借出去的钱（应收），或我向别人借的钱（应付）。
 *
 * 为什么不直接记成一笔支出？
 * 借给朋友 1000 块，钱确实离开了账户，但**它不是消费** ——
 * 记成支出会让当月支出虚高、预算当场爆掉，可这笔钱将来还会回来。
 *
 * 所以一次「借出」同时做两件事：
 * 1. 往账户记一笔真实流水 → 余额才是对的（借出的钱确实少了）
 * 2. 把这笔流水标成 excludeFromStats → 不进收支统计、不吃预算
 *
 * 真正的业务状态（谁欠我、还了多少、还欠多少）存在这张表里。
 * 金额恒为正，方向由 [direction] 决定。
 */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val direction: LoanDirection,
    /** 对方是谁（朋友/同事的名字），用来回答「谁欠我钱」 */
    val person: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    /** 已经收回（或已经还掉）的金额，累积到 amountCents 就算结清 */
    @ColumnInfo(name = "settled_cents") val settledCents: Long = 0L,
    /** 这笔钱从哪个账户出/进 */
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    /** 约定还钱的日子，可空；只用来提示，不参与任何计算 */
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    /** 还没收回/还掉的金额。计算属性，不落库 */
    val openCents: Long get() = (amountCents - settledCents).coerceAtLeast(0L)

    /** 是否已经结清 */
    val isSettled: Boolean get() = settledCents >= amountCents
}

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
    /**
     * 下一期临时改用这个金额（比如这期房租多收了 200、话费套餐变了）。
     * 只在**下一次**生成时生效，用掉就清空，不会影响之后的每期。
     */
    @ColumnInfo(name = "next_amount_cents") val nextAmountCents: Long? = null,
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

    @TypeConverter fun directionToString(v: LoanDirection): String = v.name
    @TypeConverter fun stringToDirection(v: String): LoanDirection = LoanDirection.valueOf(v)
}
