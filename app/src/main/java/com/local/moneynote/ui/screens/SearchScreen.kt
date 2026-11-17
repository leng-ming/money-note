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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.components.EmptyHint
import com.local.moneynote.ui.components.SelectChipBtn
import com.local.moneynote.ui.components.TransactionRowCard
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate

private val RANGE_LABELS = listOf("本月", "近三月", "今年", "全部")

@Composable
fun SearchScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    var rangeKey by remember { mutableIntStateOf(0) }
    var kindFilter by remember { mutableStateOf<TxKind?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<TransactionRow?>(null) }

    // 金额范围：留空 = 不限。用文本做状态而不是直接存 Int，
    // 否则输入「12.」这种中间态会被强行改写成别的数字，光标乱跳
    var minAmountText by remember { mutableStateOf("") }
    var maxAmountText by remember { mutableStateOf("") }
    val minCents = Money.parseToCents(minAmountText) ?: 0L
    val maxCents = Money.parseToCents(maxAmountText)

    val startEnd: Pair<Long, Long> = remember(rangeKey) {
        val today = LocalDate.now()
        when (rangeKey) {
            0 -> {
                val r = Dates.monthRange(today.year, today.monthValue)
                r.first to (r.last + 1)
            }
            1 -> {
                val from = today.minusMonths(2).withDayOfMonth(1)
                Dates.toMillis(from) to Dates.toMillis(today.plusDays(1))
            }
            2 -> {
                val r = Dates.yearRange(today.year)
                r.first to (r.last + 1)
            }
            else -> 0L to Long.MAX_VALUE
        }
    }

    val results by remember(keyword, startEnd, categoryId, accountId, kindFilter, minCents, maxCents) {
        vm.repo.search(
            start = startEnd.first,
            end = startEnd.second,
            keyword = keyword.trim(),
            categoryId = categoryId,
            accountId = accountId,
            kind = kindFilter,
            minCents = minCents,
            maxCents = maxCents
        )
    }.collectAsState(initial = emptyList())

    // 和数据库的统计口径保持一致：借还、押金这类「不计入统计」的流水不进合计。
    // 否则搜出来的「支出合计」会把借出去的 2000 也算进去，跟顶部月度支出对不上。
    val totalExpense = results
        .filter { it.kind == TxKind.EXPENSE && !it.excludeFromStats }
        .sumOf { it.amountCents }
    val totalIncome = results
        .filter { it.kind == TxKind.INCOME && !it.excludeFromStats }
        .sumOf { it.amountCents }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Android 15+ 强制 edge-to-edge，自己避开状态栏
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "搜索筛选",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = { Text("搜索备注或分类名") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { keyword = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(RANGE_LABELS.size) { index ->
                SelectChipBtn(
                    label = RANGE_LABELS[index],
                    selected = rangeKey == index,
                    accent = BrandGreen,
                    onClick = { rangeKey = index }
                )
            }
            item {
                SelectChipBtn(
                    label = "仅支出",
                    selected = kindFilter == TxKind.EXPENSE,
                    accent = ExpenseRed,
                    onClick = {
                        kindFilter = if (kindFilter == TxKind.EXPENSE) null else TxKind.EXPENSE
                    }
                )
            }
            item {
                SelectChipBtn(
                    label = "仅收入",
                    selected = kindFilter == TxKind.INCOME,
                    accent = IncomeGreen,
                    onClick = {
                        kindFilter = if (kindFilter == TxKind.INCOME) null else TxKind.INCOME
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SelectChipBtn(
                    label = "全部分类",
                    selected = categoryId == null,
                    accent = BrandGreen,
                    onClick = { categoryId = null }
                )
            }
            items(categories, key = { it.id }) { c ->
                SelectChipBtn(
                    label = c.name,
                    selected = categoryId == c.id,
                    accent = parseHexColor(c.colorHex),
                    onClick = {
                        categoryId = if (categoryId == c.id) null else c.id
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SelectChipBtn(
                    label = "全部账户",
                    selected = accountId == null,
                    accent = BrandGreen,
                    onClick = { accountId = null }
                )
            }
            items(accounts, key = { it.id }) { a ->
                SelectChipBtn(
                    label = a.name,
                    selected = accountId == a.id,
                    accent = parseHexColor(a.colorHex),
                    onClick = {
                        accountId = if (accountId == a.id) null else a.id
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 金额范围 —— 用来回答「这几个月超过 100 块的大额支出都有哪些」这类问题，
        // 留空即不限
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = minAmountText,
                onValueChange = { minAmountText = it },
                label = { Text("最低金额") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Text(
                "—",
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            OutlinedTextField(
                value = maxAmountText,
                onValueChange = { maxAmountText = it },
                label = { Text("最高金额") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${results.size} 笔",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f))
            if (totalExpense > 0) {
                Text(
                    "支出 ¥" + Money.format(totalExpense),
                    style = MaterialTheme.typography.labelMedium,
                    color = ExpenseRed
                )
                Spacer(Modifier.width(10.dp))
            }
            if (totalIncome > 0) {
                Text(
                    "收入 ¥" + Money.format(totalIncome),
                    style = MaterialTheme.typography.labelMedium,
                    color = IncomeGreen
                )
            }
        }

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyHint("没有符合条件的账单")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp)
            ) {
                items(results, key = { it.id }) { row ->
                    Column {
                        Text(
                            "${Dates.labelDayFull(row.occurredAt)} ${Dates.labelTime(row.occurredAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                        )
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
                    (if (target.loanId != null) "这笔是借还流水，删除会连带删除对应的借还记录。\n" else "") +
                        "${target.categoryName.ifBlank { target.note.ifBlank { "借还流水" } }}  ¥${Money.format(target.amountCents)}"
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
