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
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.components.MonthSwitcher
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
    val year by vm.year.collectAsState()
    val month by vm.month.collectAsState()
    val rows by vm.monthTransactions.collectAsState()
    val expense by vm.monthExpense.collectAsState()
    val income by vm.monthIncome.collectAsState()

    var deleteTarget by remember { mutableStateOf<TransactionRow?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSwitcher(
            label = Dates.labelMonth(year, month),
            isCurrent = vm.isCurrentMonth(),
            onPrev = { vm.shiftMonth(-1) },
            onNext = { vm.shiftMonth(1) },
            onTitleClick = { vm.goToToday() },
            trailing = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }
        )

        MonthSummaryCard(expense = expense, income = income)

        val grouped = remember(rows) { rows.groupBy { Dates.startOfDay(it.occurredAt) } }

        if (grouped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "这个月还没有账",
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
                grouped.forEach { (dayStart, dayRows) ->
                    item(key = "day-$dayStart") {
                        DayHeader(dayStart = dayStart, rows = dayRows)
                    }
                    items(items = dayRows, key = { it.id }) { row ->
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
private fun MonthSummaryCard(expense: Long, income: Long) {
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
                "本月支出",
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

@Composable
private fun DayHeader(dayStart: Long, rows: List<TransactionRow>) {
    val dayExpense = rows.filter { it.kind == TxKind.EXPENSE }.sumOf { it.amountCents }
    val dayIncome = rows.filter { it.kind == TxKind.INCOME }.sumOf { it.amountCents }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${Dates.labelDay(dayStart)}  ${Dates.labelWeekday(dayStart)}",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Row {
            if (dayExpense > 0) {
                Text(
                    "支出 " + Money.format(dayExpense),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            if (dayExpense > 0 && dayIncome > 0) Spacer(Modifier.width(10.dp))
            if (dayIncome > 0) {
                Text(
                    "收入 " + Money.format(dayIncome),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
