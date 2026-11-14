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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.LoanTotals
import com.local.moneynote.core.Money
import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.LoanDirection
import com.local.moneynote.data.LoanEntity
import com.local.moneynote.ui.components.EmptyHint
import com.local.moneynote.ui.components.RatioBar
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.PageBg
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * 借还记录页。
 *
 * 为什么单独一页、而不是塞进「记一笔」的四个 tab 里？
 * 因为借出去的钱**不是消费** —— 它是「我的钱暂时放在别人那儿」。
 * 如果记成支出，当月支出当场多一千、预算直接爆掉，可这笔钱还会回来。
 * 所以这里关心的是「谁欠我多少、我还欠谁多少」，跟收支报表是两件事。
 *
 * 实现上借还仍然会往账户记一笔真实流水（否则余额不对），
 * 但那笔流水带 excludeFromStats 标记，不进统计、不吃预算。
 */
@Composable
fun LoanScreen(vm: AppViewModel, onBack: () -> Unit) {
    val loans by vm.loans.collectAsState()
    val accounts by vm.allAccounts.collectAsState()
    val totals by vm.loanSummary.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }

    var showEditor by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<LoanEntity?>(null) }

    val open = loans.filter { !it.isSettled }
    val settled = loans.filter { it.isSettled }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Android 15 起强制 edge-to-edge，不加这个顶栏会钻到状态栏底下
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }
                Text(
                    "借还记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔借还") }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item { LoanSummaryCard(totals) }

            if (loans.isEmpty()) {
                item { EmptyHint("还没有借还记录。别人借你的钱、你借别人的钱，都可以记在这里") }
            }

            if (open.isNotEmpty()) {
                item { SectionLabel("未结清 ${open.size} 笔") }
                items(open, key = { it.id }) { loan ->
                    LoanRow(
                        loan = loan,
                        accountName = accountNames[loan.accountId] ?: "已删除账户",
                        onClick = { actionTarget = loan }
                    )
                }
            }

            if (settled.isNotEmpty()) {
                item { SectionLabel("已结清 ${settled.size} 笔") }
                items(settled, key = { it.id }) { loan ->
                    LoanRow(
                        loan = loan,
                        accountName = accountNames[loan.accountId] ?: "已删除账户",
                        onClick = { actionTarget = loan }
                    )
                }
            }
        }
    }

    if (showEditor) {
        LoanEditorDialog(
            accounts = accounts,
            onDismiss = { showEditor = false },
            onSave = { direction, person, cents, accountId, occurredAt, dueAt, note ->
                showEditor = false
                scope.launch {
                    if (cents <= 0L) return@launch
                    vm.repo.addLoan(
                        direction = direction,
                        person = person,
                        amountCents = cents,
                        accountId = accountId,
                        occurredAt = occurredAt,
                        dueAt = dueAt,
                        note = note
                    )
                }
            }
        )
    }

    actionTarget?.let { target ->
        LoanActionDialog(
            loan = target,
            accountName = accountNames[target.accountId] ?: "已删除账户",
            onDismiss = { actionTarget = null },
            onSettle = { cents ->
                actionTarget = null
                scope.launch {
                    val actual = vm.repo.settleLoan(target.id, cents, System.currentTimeMillis())
                    val msg = if (actual > 0L) {
                        "已记录 ${Money.format(actual)}"
                    } else {
                        "没有可结算的金额"
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onUndo = {
                actionTarget = null
                scope.launch {
                    val undone = vm.repo.undoLastSettle(target.id)
                    val msg = if (undone > 0L) {
                        "已撤销 ${Money.format(undone)}，可以重新记"
                    } else {
                        "没有可撤销的还款"
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                actionTarget = null
                scope.launch {
                    vm.repo.deleteLoan(target.id)
                    android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun LoanSummaryCard(totals: LoanTotals) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
        ) {
            SummaryCell(
                label = "应收",
                hint = "别人欠我",
                cents = totals.receivableCents,
                color = BrandGreen,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(TextSecondary.copy(alpha = 0.15f))
            )
            SummaryCell(
                label = "应付",
                hint = "我欠别人",
                cents = totals.payableCents,
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    hint: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(hint, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            "¥" + Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun LoanRow(loan: LoanEntity, accountName: String, onClick: () -> Unit) {
    val lent = loan.direction == LoanDirection.LENT
    val accent = if (lent) BrandGreen else ExpenseRed
    val ratio = if (loan.amountCents > 0) {
        loan.settledCents.toFloat() / loan.amountCents
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        loan.person.take(1).ifBlank { "?" },
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            loan.person,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (lent) "借出" else "借入",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Dates.labelDayFull(loan.occurredAt) + " · " + accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥" + Money.format(loan.amountCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (loan.isSettled) {
                        Text(
                            "已结清",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandGreen
                        )
                    } else if (loan.settledCents > 0L) {
                        Text(
                            "还欠 ¥" + Money.format(loan.openCents),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (!loan.isSettled) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatioBar(
                        ratio = ratio,
                        color = accent,
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "已" + (if (lent) "收回" else "还") + " " +
                            Money.format(loan.settledCents) + " / " + Money.format(loan.amountCents),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            if (loan.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    loan.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */

/**
 * 新增一条借还记录。
 *
 * 日期是可选的：这次用户就是「前段时间借出去的」，只能按今天记就废了 ——
 * 所以这里必须允许把时间往回拨，否则补记出来的账会落在错误的时间轴上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanEditorDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (LoanDirection, String, Long, Long, Long, Long?, String) -> Unit
) {
    var direction by remember { mutableStateOf(LoanDirection.LENT) }
    var person by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: -1L) }

    val cents = Money.parseToCents(amountText) ?: 0L
    val canSave = person.isNotBlank() && cents > 0L && accountId > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (direction == LoanDirection.LENT) "我借出去" else "我借进来") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp)
                ) {
                    DirectionCell("借出", direction == LoanDirection.LENT, BrandGreen) {
                        direction = LoanDirection.LENT
                    }
                    DirectionCell("借入", direction == LoanDirection.BORROWED, ExpenseRed) {
                        direction = LoanDirection.BORROWED
                    }
                }
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = person,
                    onValueChange = { if (it.length <= 20) person = it },
                    label = { Text("对方是谁") },
                    placeholder = { Text("例如：张三") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                HorizontalAccountChips(
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )
                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { showDatePicker = true }, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(Dates.labelDayFull(occurredAt) + "（点这里可以改成补记的日期）")
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 60) note = it },
                    label = { Text("备注（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(direction, person.trim(), cents, accountId, occurredAt, null, note.trim())
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val initialUtc = Dates.toLocalDate(occurredAt)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Dates.toLocalDateTime(occurredAt).toLocalTime()
                        occurredAt = Dates.toMillis(picked, time)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DirectionCell(
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HorizontalAccountChips(
    accounts: List<AccountEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        accounts.forEach { acc ->
            val selected = acc.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(acc.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    acc.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else TextSecondary
                )
            }
        }
    }
}

/**
 * 点某一条借还记录时弹出的操作面板。
 *
 * 这个对话框要回答两个问题：
 * 1. **这次到底记多少？** 支持只还一部分 —— 现实里大多也是先还一点。
 *    输入框默认填满剩余金额（一次结清最省事），但「一半 / 全部」快捷键
 *    和下面那行实时回显会明确告诉你：改成任意金额都行。
 * 2. **记错了怎么办？** 可以撤销最近一次结算，不用把整条记录删掉重来。
 */
@Composable
private fun LoanActionDialog(
    loan: LoanEntity,
    accountName: String,
    onDismiss: () -> Unit,
    onSettle: (Long) -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    val lent = loan.direction == LoanDirection.LENT
    val verb = if (lent) "收回" else "还款"
    var amountText by remember(loan.id) { mutableStateOf(Money.formatPlain(loan.openCents)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cents = Money.parseToCents(amountText) ?: 0L
    val remainAfter = (loan.openCents - cents).coerceAtLeast(0L)
    val willSettle = cents > 0L && cents >= loan.openCents

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "它产生的所有流水（借出/收回的那几笔）也会一起删掉，" +
                        "相当于这件事从没发生过。账户余额会跟着恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loan.person + (if (lent) " · 借出" else " · 借入")) },
        text = {
            Column {
                Text(
                    "本金 ¥" + Money.format(loan.amountCents) +
                        "　已" + verb + " ¥" + Money.format(loan.settledCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Dates.labelDayFull(loan.occurredAt) + " · " + accountName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                if (loan.isSettled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "这笔已经结清了",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandGreen
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "还欠 ¥" + Money.format(loan.openCents),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("这次" + verb + "多少（可以只还一部分）") },
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmount("一半") {
                            amountText = Money.formatPlain(loan.openCents / 2)
                        }
                        QuickAmount("全部（结清）") {
                            amountText = Money.formatPlain(loan.openCents)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            cents <= 0L -> "填多少就记多少，剩下的下次接着记"
                            willSettle -> "这次记 ¥" + Money.format(cents) + "，这笔就结清了"
                            else -> "这次记 ¥" + Money.format(cents) +
                                "，之后还剩 ¥" + Money.format(remainAfter)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (willSettle) BrandGreen else TextSecondary
                    )
                }
                // 记错了的补救：撤掉最近一笔，金额退回去，再按正确数字重记
                if (loan.settledCents > 0L) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUndo, contentPadding = PaddingValues(0.dp)) {
                        Text("撤销最近一次" + verb)
                    }
                }
                if (loan.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        loan.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (!loan.isSettled) {
                TextButton(enabled = cents > 0L, onClick = { onSettle(cents) }) {
                    Text(if (cents > 0L) "记一笔 ¥" + Money.format(cents) else "记一笔" + verb)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 结算金额的快捷填法：一半、全部 */
@Composable
private fun QuickAmount(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
