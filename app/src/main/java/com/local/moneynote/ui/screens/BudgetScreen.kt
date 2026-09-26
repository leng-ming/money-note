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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.BudgetEntity
import com.local.moneynote.data.BudgetPeriod
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.components.MonthSwitcher
import com.local.moneynote.ui.components.SectionCard
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val WarnOrange = Color(0xFFF57C00)

/** 按使用比例给进度条上色：正常绿 / 接近超支橙 / 超支红 */
private fun budgetColor(ratio: Float): Color = when {
    ratio >= 1f -> ExpenseRed
    ratio >= 0.8f -> WarnOrange
    else -> BrandGreen
}

@Composable
fun BudgetScreen(
    vm: AppViewModel,
    onOpenRecurring: () -> Unit
) {
    val year by vm.year.collectAsState()
    val month by vm.month.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val expenseCats by vm.expenseByCategory.collectAsState()
    val expense by vm.monthExpense.collectAsState()
    val categories by vm.allCategories.collectAsState()
    val scope = rememberCoroutineScope()

    val totalBudget = budgets.firstOrNull { it.categoryId == null }
    val catBudgets = budgets.filter { it.categoryId != null }
    val spentMap = remember(expenseCats) { expenseCats.associate { it.categoryId to it.totalCents } }

    var editingCategoryId by remember { mutableStateOf<Long?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            MonthSwitcher(
                label = Dates.labelMonth(year, month),
                isCurrent = vm.isCurrentMonth(),
                onPrev = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) },
                onTitleClick = { vm.goToToday() },
                trailing = {
                    IconButton(onClick = onOpenRecurring) {
                        Icon(Icons.Filled.Repeat, contentDescription = "周期账单")
                    }
                }
            )
        }

        item {
            TotalBudgetCard(
                budget = totalBudget,
                spent = expense,
                onEdit = {
                    editingCategoryId = null
                    editingExisting = totalBudget != null
                    showEditor = true
                }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "分类预算",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    editingCategoryId = null
                    editingExisting = false
                    showEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加")
                }
            }
        }

        if (catBudgets.isEmpty()) {
            item {
                SectionCard(title = null) {
                    Text(
                        "还没有分类预算。给「餐饮」「购物」这类容易超支的分类单独设个上限，超了这里会标红。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(catBudgets, key = { it.id }) { b ->
                val cat = categories.firstOrNull { it.id == b.categoryId }
                CategoryBudgetRow(
                    category = cat,
                    budget = b,
                    spent = spentMap[b.categoryId] ?: 0L,
                    onEdit = {
                        editingCategoryId = b.categoryId
                        editingExisting = true
                        showEditor = true
                    }
                )
            }
        }
    }

    if (showEditor) {
        BudgetEditorDialog(
            categories = categories.filter { it.kind == TxKind.EXPENSE },
            initialCategoryId = editingCategoryId,
            existing = budgets.firstOrNull { it.categoryId == editingCategoryId },
            canDelete = editingExisting,
            onDismiss = { showEditor = false },
            onDelete = {
                budgets.firstOrNull { it.categoryId == editingCategoryId }?.let { b ->
                    scope.launch { vm.repo.deleteBudget(b) }
                }
                showEditor = false
            },
            onSave = { categoryId, amountCents ->
                scope.launch {
                    vm.repo.upsertBudget(categoryId, amountCents, BudgetPeriod.MONTHLY)
                }
                showEditor = false
            }
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun TotalBudgetCard(budget: BudgetEntity?, spent: Long, onEdit: () -> Unit) {
    SectionCard(title = "月度总预算") {
        if (budget == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "还没设总预算，点这里设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text("设置", color = BrandGreen, fontWeight = FontWeight.SemiBold)
            }
        } else {
            val ratio = if (budget.amountCents > 0) spent.toFloat() / budget.amountCents else 0f
            val remaining = budget.amountCents - spent
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥ " + Money.format(budget.amountCents),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("修改") }
            }
            Spacer(Modifier.height(10.dp))
            BudgetBar(ratio = ratio)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "已用 ¥" + Money.format(spent),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (remaining >= 0) "剩余 ¥" + Money.format(remaining)
                    else "超支 ¥" + Money.format(-remaining),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining >= 0) TextSecondary else ExpenseRed
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    category: CategoryEntity?,
    budget: BudgetEntity,
    spent: Long,
    onEdit: () -> Unit
) {
    val color = parseHexColor(category?.colorHex ?: "#FF757575")
    val ratio = if (budget.amountCents > 0) spent.toFloat() / budget.amountCents else 0f
    val remaining = budget.amountCents - spent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        CatIcons.of(category?.iconKey ?: "more"),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    category?.name ?: "已删除的分类",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "¥" + Money.format(spent) + " / " + Money.format(budget.amountCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remaining >= 0) TextPrimary else ExpenseRed,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            BudgetBar(ratio = ratio)
            if (remaining < 0) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "已超支 ¥" + Money.format(-remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseRed
                )
            }
        }
    }
}

@Composable
private fun BudgetBar(ratio: Float) {
    val color = budgetColor(ratio)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
        )
    }
}

/* ------------------------------------------------------------------ */
/*  预算编辑弹层                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun BudgetEditorDialog(
    categories: List<CategoryEntity>,
    initialCategoryId: Long?,
    existing: BudgetEntity?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (categoryId: Long?, amountCents: Long) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    var amountText by remember {
        mutableStateOf(existing?.let { Money.formatPlain(it.amountCents) } ?: "")
    }
    val isTotal = selectedCategoryId == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isTotal) "月度总预算" else "分类预算") },
        text = {
            Column {
                Text(
                    "总额 = 本月全部支出；分类预算只统计该分类。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        // 只允许数字和一个小数点，最多两位小数
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1 &&
                            (filtered.substringAfter('.', "").length <= 2) &&
                            filtered.length <= 12
                        ) {
                            amountText = filtered
                        }
                    },
                    label = { Text("预算金额") },
                    prefix = { Text("¥ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("适用分类", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                // 总预算 + 各支出分类
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        BudgetCategoryChip(
                            label = "总预算",
                            color = BrandGreen,
                            selected = isTotal,
                            onClick = { selectedCategoryId = null }
                        )
                    }
                    items(categories, key = { it.id }) { c ->
                        BudgetCategoryChip(
                            label = c.name,
                            color = parseHexColor(c.colorHex),
                            selected = selectedCategoryId == c.id,
                            onClick = { selectedCategoryId = c.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseToCents(amountText)
                if (cents != null && cents > 0L) {
                    onSave(selectedCategoryId, cents)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun BudgetCategoryChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
