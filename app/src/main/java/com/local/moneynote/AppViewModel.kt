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
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = MoneyRepository(AppDatabase.get(app))

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }

    /* ---------------- 当前查看的月份 ---------------- */

    private val _year = MutableStateFlow(LocalDate.now().year)
    private val _month = MutableStateFlow(LocalDate.now().monthValue)
    val year: StateFlow<Int> = _year.asStateFlow()
    val month: StateFlow<Int> = _month.asStateFlow()

    private val monthRange: Flow<LongRange> =
        combine(_year, _month) { y, m -> Dates.monthRange(y, m) }.distinctUntilChanged()

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

    /* ---------------- 当月账单与统计 ---------------- */

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthTransactions: StateFlow<List<TransactionRow>> = monthRange
        .flatMapLatest { r -> repo.observeTransactions(r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthExpense: StateFlow<Long> = monthRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthIncome: StateFlow<Long> = monthRange
        .flatMapLatest { r -> repo.observeTotal(TxKind.INCOME, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseByCategory: StateFlow<List<CategorySum>> = monthRange
        .flatMapLatest { r -> repo.observeSumByCategory(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val incomeByCategory: StateFlow<List<CategorySum>> = monthRange
        .flatMapLatest { r -> repo.observeSumByCategory(TxKind.INCOME, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseByDay: StateFlow<List<DaySum>> = monthRange
        .flatMapLatest { r -> repo.observeSumByDay(TxKind.EXPENSE, r.first, r.last + 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    init {
        // 启动时把到期的周期账单补记成真实账单（纯本地计算）
        viewModelScope.launch {
            runCatching { repo.materializeDueRecurring() }
        }
    }

    /* ---------------- 操作 ---------------- */

    fun shiftMonth(delta: Int) {
        val (y, m) = Dates.shiftMonth(_year.value, _month.value, delta)
        _year.value = y
        _month.value = m
    }

    fun goToToday() {
        val now = LocalDate.now()
        _year.value = now.year
        _month.value = now.monthValue
    }

    fun isCurrentMonth(): Boolean {
        val now = LocalDate.now()
        return now.year == _year.value && now.monthValue == _month.value
    }

    fun currentMonthLabel(): String = Dates.labelMonth(_year.value, _month.value)

    fun currentRange(): LongRange = Dates.monthRange(_year.value, _month.value)

    suspend fun deleteTransactionById(id: Long) {
        repo.transactionById(id)?.let { repo.deleteTransaction(it) }
    }
}
