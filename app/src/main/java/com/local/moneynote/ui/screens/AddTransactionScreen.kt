package com.local.moneynote.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.AccountStyles
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * 记一笔 / 编辑一笔。
 * 全程只跟本地数据库打交道：点「完成」立刻落库，不需要等任何网络。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    vm: AppViewModel,
    txId: Long,
    onDone: () -> Unit
) {
    val accounts by vm.accounts.collectAsState()
    val allCategories by vm.allCategories.collectAsState()
    val netByAccount by vm.netByAccount.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var kind by remember { mutableStateOf(TxKind.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var loaded by remember { mutableStateOf(false) }

    var showAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isEditing = txId > 0L

    // 编辑模式：回填原数据
    LaunchedEffect(txId) {
        if (isEditing) {
            vm.repo.transactionById(txId)?.let { t ->
                kind = t.kind
                amountText = Money.formatPlain(t.amountCents)
                categoryId = t.categoryId
                accountId = t.accountId
                note = t.note
                occurredAt = t.occurredAt
            }
        }
        loaded = true
    }

    // 兜底默认值
    LaunchedEffect(loaded, accounts, allCategories) {
        if (!loaded) return@LaunchedEffect
        if (accountId == null) accountId = accounts.firstOrNull()?.id
        if (categoryId == null) categoryId = allCategories.firstOrNull { it.kind == kind }?.id
    }

    // 切换收/支时，把不属于当前类型的分类换掉
    LaunchedEffect(kind) {
        val cur = allCategories.firstOrNull { it.id == categoryId }
        if (cur != null && cur.kind != kind) {
            categoryId = allCategories.firstOrNull { it.kind == kind }?.id
        }
    }

    val cats = remember(allCategories, kind) { allCategories.filter { it.kind == kind } }
    val currentAccount = accounts.firstOrNull { it.id == accountId }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun save() {
        val cents = Money.parseToCents(amountText)
        if (cents == null || cents <= 0L) {
            toast("请输入金额")
            return
        }
        val cid = categoryId
        if (cid == null) {
            toast("请选择分类")
            return
        }
        val aid = accountId
        if (aid == null) {
            toast("请选择账户")
            return
        }
        scope.launch {
            if (isEditing) {
                val old = vm.repo.transactionById(txId)
                if (old == null) {
                    toast("这笔账已不存在")
                } else {
                    vm.repo.updateTransaction(
                        old.copy(
                            amountCents = cents,
                            kind = kind,
                            accountId = aid,
                            categoryId = cid,
                            note = note,
                            occurredAt = occurredAt
                        )
                    )
                }
            } else {
                vm.repo.addTransaction(
                    amountCents = cents,
                    kind = kind,
                    accountId = aid,
                    categoryId = cid,
                    note = note,
                    occurredAt = occurredAt
                )
            }
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Android 15+ 强制 edge-to-edge，必须自己避开状态栏/手势条，否则顶栏会被状态栏压住
            .safeDrawingPadding()
    ) {
        // ---------- 顶栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            KindSwitch(
                kind = kind,
                onKindChange = { kind = it },
                modifier = Modifier.weight(1f)
            )
            if (isEditing) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // ---------- 金额 ----------
        AmountDisplay(amountText = amountText, kind = kind)

        // ---------- 分类 ----------
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp, vertical = 4.dp
            )
        ) {
            items(cats, key = { it.id }) { cat ->
                val c = parseHexColor(cat.colorHex)
                val selected = cat.id == categoryId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(vertical = 7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { categoryId = cat.id }
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) c else c.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CatIcons.of(cat.iconKey),
                            contentDescription = null,
                            tint = if (selected) Color.White else c,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        cat.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) TextPrimary else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ---------- 账户 / 日期 / 备注 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip(
                icon = { Icon(AccountStyles.icon(currentAccount?.type ?: com.local.moneynote.data.AccountType.CASH), null, Modifier.size(16.dp), tint = TextSecondary) },
                text = currentAccount?.name ?: "选择账户",
                modifier = Modifier.weight(1f),
                onClick = { showAccountPicker = true }
            )
            Spacer(Modifier.width(8.dp))
            InfoChip(
                icon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(16.dp), tint = TextSecondary) },
                text = Dates.labelDayFull(occurredAt),
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker = true }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip(
                icon = { Icon(Icons.Filled.EditNote, null, Modifier.size(16.dp), tint = TextSecondary) },
                text = note.ifBlank { "添加备注…" },
                modifier = Modifier.fillMaxWidth(),
                onClick = { showNoteDialog = true }
            )
        }

        // ---------- 数字键盘 ----------
        Keypad(
            onKey = { key -> amountText = applyKey(amountText, key) },
            onBackspace = { amountText = amountText.dropLast(1) },
            onDone = { save() }
        )
        Spacer(Modifier.height(4.dp))
    }

    /* ---------------- 弹层 ---------------- */

    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text("选择账户") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    accounts.forEach { acc ->
                        val balance = acc.initialBalanceCents + (netByAccount[acc.id] ?: 0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    accountId = acc.id
                                    showAccountPicker = false
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AccountStyles.icon(acc.type),
                                contentDescription = null,
                                tint = parseHexColor(acc.colorHex),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(acc.name, modifier = Modifier.weight(1f))
                            Text(
                                "¥ " + Money.format(balance),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountPicker = false }) { Text("关闭") }
            }
        )
    }

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

    if (showNoteDialog) {
        var draft by remember { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("备注") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 60) draft = it },
                    singleLine = false,
                    maxLines = 3,
                    placeholder = { Text("例如：和同事吃午饭") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    note = draft
                    showNoteDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔账？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        vm.deleteTransactionById(txId)
                        onDone()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  子组件                                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun KindSwitch(
    kind: TxKind,
    onKindChange: (TxKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KindTab(
            text = "支出",
            selected = kind == TxKind.EXPENSE,
            selectedColor = ExpenseRed,
            onClick = { onKindChange(TxKind.EXPENSE) }
        )
        Spacer(Modifier.width(10.dp))
        KindTab(
            text = "收入",
            selected = kind == TxKind.INCOME,
            selectedColor = IncomeGreen,
            onClick = { onKindChange(TxKind.INCOME) }
        )
    }
}

@Composable
private fun KindTab(
    text: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) selectedColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) selectedColor.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) selectedColor else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun AmountDisplay(amountText: String, kind: TxKind) {
    val color = if (kind == TxKind.EXPENSE) ExpenseRed else IncomeGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            "¥",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = amountText.ifBlank { "0" },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoChip(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 把一次按键作用到金额字符串上，规则与常见记账 App 一致 */
private fun applyKey(current: String, key: String): String {
    if (key == ".") {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    if (key == "00") {
        // 空、以及整数部分的 "0" 后面不允许直接补 00；小数部分也不用 00 键
        if (current.isEmpty() || current == "0") return current
        if (current.contains('.')) return current
        if (current.length + 2 > 9) return current
        return current + "00"
    }
    // 小数位最多两位
    val dot = current.indexOf('.')
    if (dot >= 0 && current.length - dot > 2) return current
    // 整数部分最长 9 位
    if (dot < 0 && current.length >= 9) return current
    val next = if (current == "0") key else current + key
    return if (next.length > 13) current else next
}

@Composable
private fun Keypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "00")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(key) }
                    )
                }
                if (row.first() == "1") {
                    FuncButton(
                        label = "退格",
                        icon = Icons.Filled.Backspace,
                        modifier = Modifier.weight(1f),
                        onClick = onBackspace
                    )
                } else if (row.first() == "4") {
                    FuncButton(
                        label = "清空",
                        modifier = Modifier.weight(1f),
                        onClick = { repeat(13) { onBackspace() } }
                    )
                } else if (row.first() == "7") {
                    FuncButton(
                        label = "保存",
                        modifier = Modifier.weight(1f),
                        highlight = true,
                        onClick = onDone
                    )
                } else {
                    FuncButton(
                        label = "完成",
                        modifier = Modifier.weight(1f),
                        highlight = true,
                        onClick = onDone
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun FuncButton(
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) BrandGreen else Color(0xFFE3E6EA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (highlight) Color.White else TextSecondary
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                textAlign = TextAlign.Center
            )
        }
    }
}
