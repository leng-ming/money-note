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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.AccountType
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.RecurFreq
import com.local.moneynote.data.RecurringEntity
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.AccountStyles
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.components.EmptyHint
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

private fun freqLabel(freq: RecurFreq): String = when (freq) {
    RecurFreq.DAILY -> "每天"
    RecurFreq.WEEKLY -> "每周"
    RecurFreq.MONTHLY -> "每月"
    RecurFreq.YEARLY -> "每年"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rules by vm.recurringRules.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categories by vm.allCategories.collectAsState()
    val scope = rememberCoroutineScope()

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }

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
                "周期账单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                scope.launch { vm.repo.materializeDueRecurring() }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "立即检查到期")
            }
            IconButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "新增规则")
            }
        }

        if (rules.isEmpty()) {
            EmptyHint("还没有周期账单。房租、话费、订阅这类固定支出，设一次就自动帮你记账。")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp)
            ) {
                item {
                    Text(
                        "每次打开 App 时，到期的规则会自动补记成真实账单。",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
                items(rules, key = { it.id }) { rule ->
                    val cat = categories.firstOrNull { it.id == rule.categoryId }
                    val acc = accounts.firstOrNull { it.id == rule.accountId }
                    RuleCard(
                        rule = rule,
                        category = cat,
                        accountName = acc?.name ?: "已删除账户",
                        onToggle = { enabled ->
                            scope.launch { vm.repo.updateRecurring(rule.copy(enabled = enabled)) }
                        },
                        onClick = {
                            editing = rule
                            showEditor = true
                        }
                    )
                }
            }
        }
    }

    if (showEditor) {
        RecurringEditorDialog(
            initial = editing,
            accounts = accounts,
            categories = categories,
            onDismiss = { showEditor = false },
            onDelete = { rule ->
                scope.launch { vm.repo.deleteRecurring(rule) }
                showEditor = false
            },
            onSave = { entity ->
                scope.launch {
                    if (entity.id == 0L) vm.repo.addRecurring(entity)
                    else vm.repo.updateRecurring(entity)
                }
                showEditor = false
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RecurringEntity,
    category: CategoryEntity?,
    accountName: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val color = parseHexColor(category?.colorHex ?: "#FF757575")
    val isExpense = rule.kind == TxKind.EXPENSE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    CatIcons.of(category?.iconKey ?: "more"),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${freqLabel(rule.freq)} · ${accountName} · 下次 ${Dates.labelDayFull(rule.nextOccurAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isExpense) "-" else "+") + Money.format(rule.amountCents),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpense) ExpenseRed else IncomeGreen
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    initial: RecurringEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onDelete: (RecurringEntity) -> Unit,
    onSave: (RecurringEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.let { Money.formatPlain(it.amountCents) } ?: "")
    }
    var kind by remember { mutableStateOf(initial?.kind ?: TxKind.EXPENSE) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var accountId by remember { mutableStateOf(initial?.accountId) }
    var freq by remember { mutableStateOf(initial?.freq ?: RecurFreq.MONTHLY) }
    var nextAt by remember { mutableStateOf(initial?.nextOccurAt ?: System.currentTimeMillis()) }
    var dateExpanded by remember { mutableStateOf(false) }

    val cats = categories.filter { it.kind == kind }

    // 默认值兜底
    LaunchedEffect(accounts, cats) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
        if (categoryId == null || cats.none { it.id == categoryId }) {
            categoryId = cats.firstOrNull()?.id
        }
    }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = Dates.toLocalDate(nextAt)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial == null) "新增周期账单" else "编辑周期账单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：房租") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1 &&
                            filtered.substringAfter('.', "").length <= 2 &&
                            filtered.length <= 12
                        ) {
                            amountText = filtered
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                LabelText("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("支出", kind == TxKind.EXPENSE, ExpenseRed) { kind = TxKind.EXPENSE }
                    SelectChip("收入", kind == TxKind.INCOME, IncomeGreen) { kind = TxKind.INCOME }
                }
                Spacer(Modifier.height(14.dp))

                LabelText("频率")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurFreq.entries.forEach { f ->
                        SelectChip(freqLabel(f), freq == f, BrandGreen) { freq = f }
                    }
                }
                Spacer(Modifier.height(14.dp))

                LabelText("分类")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats, key = { it.id }) { c ->
                        SelectChip(
                            label = c.name,
                            selected = categoryId == c.id,
                            accent = parseHexColor(c.colorHex)
                        ) { categoryId = c.id }
                    }
                }
                Spacer(Modifier.height(14.dp))

                LabelText("账户")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { a ->
                        SelectChip(
                            label = a.name,
                            selected = accountId == a.id,
                            accent = parseHexColor(a.colorHex)
                        ) { accountId = a.id }
                    }
                }
                Spacer(Modifier.height(14.dp))

                LabelText("下次记账日期")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { dateExpanded = !dateExpanded }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Dates.labelDayFull(nextAt), modifier = Modifier.weight(1f))
                    Text(
                        if (dateExpanded) "收起" else "修改",
                        color = BrandGreen,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (dateExpanded) {
                    DatePicker(state = dateState)
                    LaunchedEffect(dateState.selectedDateMillis) {
                        dateState.selectedDateMillis?.let { ms ->
                            val picked = Instant.ofEpochMilli(ms)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            nextAt = Dates.toMillis(picked, LocalTime.of(9, 0))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initial != null) {
                        TextButton(onClick = { onDelete(initial) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        val cents = Money.parseToCents(amountText)
                        val cid = categoryId
                        val aid = accountId
                        if (name.isNotBlank() && cents != null && cents > 0L && cid != null && aid != null) {
                            onSave(
                                RecurringEntity(
                                    id = initial?.id ?: 0L,
                                    name = name.trim(),
                                    amountCents = cents,
                                    kind = kind,
                                    accountId = aid,
                                    categoryId = cid,
                                    note = initial?.note ?: "",
                                    freq = freq,
                                    intervalCount = initial?.intervalCount ?: 1,
                                    nextOccurAt = nextAt,
                                    lastGeneratedAt = initial?.lastGeneratedAt,
                                    enabled = initial?.enabled ?: true
                                )
                            )
                        }
                    }) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
