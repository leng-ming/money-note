package com.local.moneynote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.moneynote.core.Dates
import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.AppDatabase
import com.local.moneynote.data.BudgetEntity
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.CategorySum
import com.local.moneynote.data.DaySum
import com.local.moneynote.data.MoneyRepository
import com.local.moneynote.data.MonthSum
import com.local.moneynote.data.RecurringEntity
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 全局唯一的 ViewModel。数据全部来自本地 Room，任何操作都不涉及网络，
 * 所以不存在"转圈等服务器"的中间态。
 */
/** 明细页与图表页的浏览粒度。预算页固定按月，不受它影响。 */
enum class Granularity { YEAR, MONTH, DAY }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = MoneyRepository(AppDatabase.get(app))

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }

    /* ---------------- 当前浏览的区间 ---------------- */

    private val _granularity = MutableStateFlow(Granularity.MONTH)
    val granularity: StateFlow<Granularity> = _granularity.asStateFlow()

    /** 当前定位到的日期。年/月/日 三种粒度都基于它推算区间 */
    private val _anchor = MutableStateFlow(LocalDate.now())
    val anchor: StateFlow<LocalDate> = _anchor.asStateFlow()

    val year: StateFlow<Int> = _anchor.map { it.year }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LocalDate.now().year)

    val month: StateFlow<Int> = _anchor.map { it.monthValue }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LocalDate.now().monthValue)

    /** 明细/图表用：区间随粒度变化 */
    private val periodRange: Flow<LongRange> =
        combine(_granularity, _anchor) { g, d ->
            when (g) {
                Granularity.YEAR -> Dates.yearRange(d.year)
                Granularity.MONTH -> Dates.monthRange(d.year, d.monthValue)
                Granularity.DAY -> Dates.dayRange(d)
            }
        }.distinctUntilChanged()

    /** 预算用：永远是锚点所在的自然月，不随粒度变化 */
    private val anchorMonthRange: Flow<LongRange> =
        _anchor.map { Dates.monthRange(it.year, it.monthValue) }.distinctUntilChanged()

    /* ---------------- 基础字典 ---------------- */

    val accounts: StateFlow<List<AccountEntity>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repo.observeAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repo.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val netByAccount: StateFlow<Map<Long, Long>> = repo.observeNetByAccount()
        .map { list -> list.associate { it.accountId to it.netCents } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyMap())

    val budgets: StateFlow<List<BudgetEntity>> = repo.observeBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val recurringRules: StateFlow<List<RecurringEntity>> = repo.observeRecurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /* ---------------- 区间内的账单与统计 ---------------- */

    @OptIn(ExperimentalCoroutinesApi::class)
    val periodTransactions: StateFlow<List<TransactionRow>> = periodRange
        .flatMapLatest { r -> repo.observeTransactions(r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val periodExpense: StateFlow<Long> = periodRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val periodIncome: StateFlow<Long> = periodRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.INCOME, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    /** 锚点所在自然月的支出，预算页专用 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpense: StateFlow<Long> = anchorMonthRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthIncome: StateFlow<Long> = anchorMonthRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.INCOME, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    /** 锚点自然月的分类汇总，预算页专用（预算的分类已用不该跟着年/日视图变） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpenseByCategory: StateFlow<List<CategorySum>> = anchorMonthRange
        .flatMapLatest { r -> repo.observeSumByCategory(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /**
     * 同上，但**不把二级分类归并到一级**。
     *
     * 分类预算必须用这份数据：给「购物 → 数码」这种二级分类设预算时，
     * 归并版里根本没有「数码」这个 key，查出来永远是 0（用户实际踩到的 bug）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpenseByExactCategory: StateFlow<List<CategorySum>> = anchorMonthRange
        .flatMapLatest { r -> repo.observeSumByExactCategory(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /* ---------------- 预算转结 ---------------- */

    /** 本月从以前月份转进来的预算总额 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val carryoverIn: StateFlow<Long> = _anchor
        .flatMapLatest { a -> repo.observeCarryoverIncoming(null, a.year, a.monthValue) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    /** 本月是否已经把剩余转给下月了（按钮据此显示「已转结」） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val carryoverDone: StateFlow<Boolean> = _anchor
        .flatMapLatest { a -> repo.observeCarryoverOutgoingExists(null, a.year, a.monthValue) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseByCategory: StateFlow<List<CategorySum>> = periodRange
        .flatMapLatest { r -> repo.observeSumByCategory(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomeByCategory: StateFlow<List<CategorySum>> = periodRange
        .flatMapLatest { r -> repo.observeSumByCategory(TxKind.INCOME, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** 月视图的每日趋势 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseByDay: StateFlow<List<DaySum>> = periodRange
        .flatMapLatest { r -> repo.observeSumByDay(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** 年视图的每月趋势 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseByMonth: StateFlow<List<MonthSum>> = periodRange
        .flatMapLatest { r -> repo.observeSumByMonth(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    init {
        // 启动时把到期的周期账单补记成真实账单（纯本地计算）
        viewModelScope.launch {
            runCatching { repo.materializeDueRecurring() }
        }
    }

    /* ---------------- 操作 ---------------- */

    fun setGranularity(g: Granularity) {
        _granularity.value = g
    }

    /** 按当前粒度前后翻页：年视图翻一年，月视图翻一月，日视图翻一天 */
    fun shiftPeriod(delta: Int) {
        val cur = _anchor.value
        _anchor.value = when (_granularity.value) {
            Granularity.YEAR -> cur.plusYears(delta.toLong())
            Granularity.MONTH -> cur.plusMonths(delta.toLong())
            Granularity.DAY -> cur.plusDays(delta.toLong())
        }
    }

    /** 预算页专用：只按月翻 */
    fun shiftMonth(delta: Int) {
        _anchor.value = _anchor.value.plusMonths(delta.toLong())
    }

    fun goToToday() {
        _anchor.value = LocalDate.now()
    }

    /**
     * 当前区间是否就是"今天所在的区间"。
     *
     * 这里必须是 StateFlow，不能是普通函数：普通函数读的是 `_anchor.value`，
     * 界面无从感知它的变化。当连续两个月都没有账单时，账单列表和合计值都不变，
     * Compose 就不会重组，标题会一直卡在上一个月份（实际状态早就切过去了）。
     */
    val isCurrentPeriod: StateFlow<Boolean> =
        combine(_granularity, _anchor) { g, a ->
            val now = LocalDate.now()
            when (g) {
                Granularity.YEAR -> now.year == a.year
                Granularity.MONTH -> now.year == a.year && now.monthValue == a.monthValue
                Granularity.DAY -> now == a
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), true)

    /** 区间标题：2026年 / 2026年9月 / 2026年9月26日（同样做成 State，保证标题能刷新） */
    val periodLabel: StateFlow<String> =
        combine(_granularity, _anchor) { g, a ->
            when (g) {
                Granularity.YEAR -> "${a.year}年"
                Granularity.MONTH -> Dates.labelMonth(a.year, a.monthValue)
                Granularity.DAY -> Dates.labelDayFull(Dates.toMillis(a))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), "")

    /** 预算页用（预算固定按月） */
    fun isCurrentMonth(): Boolean {
        val now = LocalDate.now()
        val a = _anchor.value
        return now.year == a.year && now.monthValue == a.monthValue
    }

    fun currentMonthLabel(): String = Dates.labelMonth(_anchor.value.year, _anchor.value.monthValue)

    fun currentRange(): LongRange = Dates.monthRange(_anchor.value.year, _anchor.value.monthValue)

    suspend fun deleteTransactionById(id: Long) {
        repo.transactionById(id)?.let { repo.deleteTransaction(it) }
    }
}
