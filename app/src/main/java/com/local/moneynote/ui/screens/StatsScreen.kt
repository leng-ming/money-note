package com.local.moneynote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.Granularity
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.CategorySum
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.components.ChartSlice
import com.local.moneynote.ui.components.DayBarChart
import com.local.moneynote.ui.components.DonutChart
import com.local.moneynote.ui.components.EmptyHint
import com.local.moneynote.ui.components.PeriodBar
import com.local.moneynote.ui.components.RatioBar
import com.local.moneynote.ui.components.SectionCard
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary

@Composable
fun StatsScreen(vm: AppViewModel) {
    val year by vm.year.collectAsState()
    val month by vm.month.collectAsState()
    val granularity by vm.granularity.collectAsState()
    val periodLabel by vm.periodLabel.collectAsState()
    val isCurrentPeriod by vm.isCurrentPeriod.collectAsState()
    val expense by vm.periodExpense.collectAsState()
    val income by vm.periodIncome.collectAsState()
    val expenseCats by vm.expenseByCategory.collectAsState()
    val incomeCats by vm.incomeByCategory.collectAsState()
    val byDay by vm.expenseByDay.collectAsState()
    val byMonth by vm.expenseByMonth.collectAsState()

    var showIncome by remember { mutableStateOf(false) }

    val cats: List<CategorySum> = if (showIncome) incomeCats else expenseCats
    val total: Long = if (showIncome) income else expense
    val daysInMonth = Dates.daysInMonth(year, month)

    val slices = remember(cats) {
        cats.map { ChartSlice(it.name, it.totalCents, parseHexColor(it.colorHex)) }
    }

    // 月视图：每天一根柱子
    val dayValues = remember(byDay, daysInMonth) {
        val arr = MutableList(daysInMonth) { 0L }
        byDay.forEach { ds ->
            val d = ds.day.substringAfterLast('-').toIntOrNull()
            if (d != null && d in 1..daysInMonth) arr[d - 1] = ds.totalCents
        }
        arr
    }

    // 年视图：每月一根柱子
    val monthValues = remember(byMonth) {
        val arr = MutableList(12) { 0L }
        byMonth.forEach { ms ->
            val m = ms.month.substringAfterLast('-').toIntOrNull()
            if (m != null && m in 1..12) arr[m - 1] = ms.totalCents
        }
        arr
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            PeriodBar(
                granularity = granularity,
                label = periodLabel,
                isCurrent = isCurrentPeriod,
                onGranularityChange = { vm.setGranularity(it) },
                onPrev = { vm.shiftPeriod(-1) },
                onNext = { vm.shiftPeriod(1) },
                onTitleClick = { vm.goToToday() }
            )
        }

        item { OverviewCard(expense = expense, income = income) }

        item {
            SectionCard(title = "构成分析") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(146.dp)) {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier.fillMaxSize(),
                            centerTitle = if (showIncome) "总收入" else "总支出",
                            centerValue = "¥" + Money.format(total)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (slices.isEmpty()) {
                            Text(
                                "暂无数据",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            slices.take(6).forEach { slice -> LegendRow(slice, total) }
                            if (slices.size > 6) {
                                Text(
                                    "还有 ${slices.size - 6} 项…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                KindToggle(
                    showIncome = showIncome,
                    onChange = { showIncome = it }
                )
            }
        }

        // 趋势图：年视图看每月、月视图看每日；日视图只有一天，没有趋势可言
        if (granularity != Granularity.DAY) {
            item {
                SectionCard(
                    title = if (granularity == Granularity.YEAR) "每月支出趋势" else "每日支出趋势"
                ) {
                    if (expense <= 0L) {
                        EmptyHint("这段时间还没有支出")
                    } else {
                        DayBarChart(
                            values = if (granularity == Granularity.YEAR) monthValues else dayValues,
                            barColor = BrandGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val labels = if (granularity == Granularity.YEAR) {
                                listOf("1月", "6月", "12月")
                            } else {
                                listOf("1日", "${daysInMonth / 2}日", "${daysInMonth}日")
                            }
                            labels.forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (cats.isEmpty()) {
            item { EmptyHint("这段时间还没有数据") }
        } else {
            item {
                Text(
                    "分类排行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                )
            }
            items(cats, key = { it.categoryId }) { c ->
                CategoryRankRow(item = c, total = total)
            }
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun OverviewCard(expense: Long, income: Long) {
    SectionCard(title = null) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OverviewCell("支出", expense, ExpenseRed, Modifier.weight(1f))
            OverviewCell("收入", income, IncomeGreen, Modifier.weight(1f))
            OverviewCell("结余", income - expense, TextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewCell(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            "¥" + Money.format(value),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun LegendRow(slice: ChartSlice, total: Long) {
    val pct = if (total > 0) slice.value.toFloat() / total * 100f else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(slice.color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            slice.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            "%.1f%%".format(pct),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun KindToggle(showIncome: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        ToggleCell("支出构成", !showIncome, ExpenseRed) { onChange(false) }
        ToggleCell("收入构成", showIncome, IncomeGreen) { onChange(true) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToggleCell(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CardBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CategoryRankRow(item: CategorySum, total: Long) {
    val color = parseHexColor(item.colorHex)
    val ratio = if (total > 0) item.totalCents.toFloat() / total else 0f
    val pct = ratio * 100f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    CatIcons.of(item.iconKey),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "¥" + Money.format(item.totalCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatioBar(
                        ratio = ratio,
                        color = color,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "%.1f%%".format(pct),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
