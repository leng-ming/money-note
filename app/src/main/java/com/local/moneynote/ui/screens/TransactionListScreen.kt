package com.local.moneynote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.Granularity
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.components.HOME_CARD_HEIGHT
import com.local.moneynote.ui.components.HOME_PAGE_ASSETS
import com.local.moneynote.ui.components.HOME_PAGE_BUDGET
import com.local.moneynote.ui.components.HOME_PAGE_COUNT
import com.local.moneynote.ui.components.HOME_PAGE_SUMMARY
import com.local.moneynote.ui.components.HomeAssetsCard
import com.local.moneynote.ui.components.HomeBudgetCard
import com.local.moneynote.ui.components.HomeCardIndicator
import com.local.moneynote.ui.components.HomeSummaryCard
import com.local.moneynote.ui.components.PeriodBar
import com.local.moneynote.ui.components.TransactionRowCard
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.BrandGreenLight
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun TransactionListScreen(
    vm: AppViewModel,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit
) {
    val granularity by vm.granularity.collectAsState()
    val periodLabel by vm.periodLabel.collectAsState()
    val isCurrentPeriod by vm.isCurrentPeriod.collectAsState()
    val rows by vm.periodTransactions.collectAsState()
    val expense by vm.periodExpense.collectAsState()
    val income by vm.periodIncome.collectAsState()

    // 主页卡片还要用到预算与资产数据
    val year by vm.year.collectAsState()
    val month by vm.month.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val carryoverIn by vm.carryoverIn.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val netByAccount by vm.netByAccount.collectAsState()

    var deleteTarget by remember { mutableStateOf<TransactionRow?>(null) }
    val scope = rememberCoroutineScope()

    // 三张卡片：[资产] [收支] [预算]，默认停在中间的收支
    val pagerState = rememberPagerState(initialPage = HOME_PAGE_SUMMARY) { HOME_PAGE_COUNT }
    val totalBudget = budgets.firstOrNull { it.categoryId == null }
    val remainingDays = Dates.remainingDaysInMonth(year, month)
    // 剩余日均 = (预算 + 上月转结 - 已用) ÷ 剩余天数
    val dailyBudget = totalBudget?.let { b ->
        val left = b.amountCents + carryoverIn - expense
        if (remainingDays > 0 && left > 0) left / remainingDays else null
    }
    val totalAssets = accounts.sumOf { it.initialBalanceCents + (netByAccount[it.id] ?: 0L) }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodBar(
            granularity = granularity,
            label = periodLabel,
            isCurrent = isCurrentPeriod,
            onGranularityChange = { vm.setGranularity(it) },
            onPrev = { vm.shiftPeriod(-1) },
            onNext = { vm.shiftPeriod(1) },
            onTitleClick = { vm.goToToday() },
            trailing = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }
        )

        // 左右滑动切换：往左滑看预算，往右滑看总资产
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(HOME_CARD_HEIGHT),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                HOME_PAGE_ASSETS -> HomeAssetsCard(
                    totalCents = totalAssets,
                    accounts = accounts.map { acc ->
                        acc.name to (acc.initialBalanceCents + (netByAccount[acc.id] ?: 0L))
                    }
                )

                HOME_PAGE_BUDGET -> HomeBudgetCard(
                    budgetCents = totalBudget?.amountCents ?: 0L,
                    spentCents = expense,
                    carryoverIn = carryoverIn,
                    remainingDays = remainingDays
                )

                else -> HomeSummaryCard(
                    title = when (granularity) {
                        Granularity.YEAR -> "本年支出"
                        Granularity.MONTH -> "本月支出"
                        Granularity.DAY -> "本日支出"
                    },
                    expense = expense,
                    income = income,
                    dailyBudget = dailyBudget
                )
            }
        }
        HomeCardIndicator(
            currentPage = pagerState.currentPage,
            modifier = Modifier.padding(top = 5.dp, bottom = 2.dp)
        )

        val groups = remember(rows, granularity) { groupTransactions(rows, granularity) }

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "这段时间还没有账",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点右下角的 + 记一笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp)
            ) {
                groups.forEach { (title, groupRows) ->
                    if (title.isNotEmpty()) {
                        item(key = "h-$title") {
                            GroupHeader(title = title, rows = groupRows)
                        }
                    }
                    items(items = groupRows, key = { it.id }) { row ->
                        TransactionRowCard(
                            row = row,
                            onClick = { onEdit(row.id) },
                            onLongClick = { deleteTarget = row }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这笔账？") },
            text = {
                Text(
                    buildString {
                        append(target.categoryName)
                        append("  ¥")
                        append(Money.format(target.amountCents))
                        if (target.note.isNotBlank()) {
                            append("\n")
                            append(target.note)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    deleteTarget = null
                    scope.launch { vm.deleteTransactionById(id) }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PeriodSummaryCard(expense: Long, income: Long, title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(BrandGreen, BrandGreenLight)))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                title,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "¥ " + Money.format(expense),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Column {
                    Text(
                        "收入",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "¥ " + Money.format(income),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.width(32.dp))
                Column {
                    Text(
                        "结余",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "¥ " + Money.format(income - expense),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

/** 分组头：年视图按月分组、月视图按日分组，显示该组小计 */
@Composable
private fun GroupHeader(title: String, rows: List<TransactionRow>) {
    val groupExpense = rows.filter { it.kind == TxKind.EXPENSE }.sumOf { it.amountCents }
    val groupIncome = rows.filter { it.kind == TxKind.INCOME }.sumOf { it.amountCents }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Row {
            if (groupExpense > 0) {
                Text(
                    "支出 " + Money.format(groupExpense),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            if (groupExpense > 0 && groupIncome > 0) Spacer(Modifier.width(10.dp))
            if (groupIncome > 0) {
                Text(
                    "收入 " + Money.format(groupIncome),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * 按粒度把账单分组。
 * 年视图按月分，月视图按日分；日视图只有一天，不需要分组头。
 * rows 已按时间倒序，groupBy 保持插入顺序，所以分组顺序也是倒序的。
 */
private fun groupTransactions(
    rows: List<TransactionRow>,
    granularity: Granularity
): List<Pair<String, List<TransactionRow>>> = when (granularity) {
    Granularity.YEAR ->
        rows.groupBy { tx ->
            val d = Dates.toLocalDate(tx.occurredAt)
            d.year * 100 + d.monthValue
        }.map { (yearMonth, list) ->
            Dates.labelMonth(yearMonth / 100, yearMonth % 100) to list
        }

    Granularity.MONTH ->
        rows.groupBy { Dates.startOfDay(it.occurredAt) }
            .map { (dayStart, list) ->
                "${Dates.labelDay(dayStart)}  ${Dates.labelWeekday(dayStart)}" to list
            }

    Granularity.DAY ->
        if (rows.isEmpty()) emptyList() else listOf("" to rows)
}
