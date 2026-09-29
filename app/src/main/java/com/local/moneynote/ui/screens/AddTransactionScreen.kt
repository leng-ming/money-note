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
import com.local.moneynote.data.CategoryEntity
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
    // 用户是否手动选过账户。选过之后就不再自动预选，免得"抢"用户的选择
    var accountTouched by remember { mutableStateOf(false) }

    var showAccountPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 非 null 时展示该一级分类的二级细分面板
    var showSubCategoryFor by remember { mutableStateOf<CategoryEntity?>(null) }

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
                // 编辑时沿用原账单的账户，不要被"账户记忆"覆盖
                accountTouched = true
            }
        }
        loaded = true
    }

    // 记账页只展示一级分类；二级细分通过点一级分类弹出的面板选
    val topCats = remember(allCategories, kind) {
        allCategories.filter { it.kind == kind && it.parentId == null }
    }
    val subCatsOf: (Long) -> List<CategoryEntity> = { parentId ->
        allCategories.filter { it.parentId == parentId }
    }
    // 当前选中的一级分类（选了二级时回溯到它的父级）
    val selectedCategory = allCategories.firstOrNull { it.id == categoryId }
    val selectedTopId: Long? = selectedCategory?.let { it.parentId ?: it.id }

    // 分类兜底：首次进入、或切换收/支后原分类不属于当前类型时，换一个
    LaunchedEffect(loaded, allCategories, kind) {
        if (!loaded) return@LaunchedEffect
        val cur = allCategories.firstOrNull { it.id == categoryId }
        if (cur == null || cur.kind != kind) {
            categoryId = topCats.firstOrNull()?.id
        }
    }

    // 账户记忆：优先「该分类上次用过的账户」，其次「全局上次用过的账户」，
    // 都没有历史才退回第一个账户。用户手动选过之后不再干预。
    LaunchedEffect(loaded, accounts, categoryId, accountTouched) {
        if (!loaded || accountTouched || accounts.isEmpty()) return@LaunchedEffect
        if (isEditing) return@LaunchedEffect
        val remembered = categoryId?.let { vm.repo.lastAccountIdForCategory(it) }
            ?: vm.repo.lastAccountId()
        accountId = if (remembered != null && accounts.any { it.id == remembered }) {
            remembered
        } else {
            accounts.firstOrNull()?.id
        }
    }

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
            items(topCats, key = { it.id }) { cat ->
                val c = parseHexColor(cat.colorHex)
                val selected = cat.id == selectedTopId
                val hasChildren = allCategories.any { it.parentId == cat.id }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(vertical = 7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            // 有细分的弹面板让用户挑；没有细分的直接选中
                            if (hasChildren) showSubCategoryFor = cat else categoryId = cat.id
                        }
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

        // 分类网格里只高亮到一级，所以这里明确写出当前到底选的是哪个细分
        val selectedPath = selectedCategory?.let { c ->
            val parent = c.parentId?.let { pid -> allCategories.firstOrNull { it.id == pid } }
            if (parent != null) "${parent.name} · ${c.name}" else c.name
        }
        if (selectedPath != null) {
            Text(
                "当前分类：$selectedPath",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
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
            onClearAll = { amountText = "" },
            onDone = { save() }
        )
        Spacer(Modifier.height(4.dp))
    }

    /* ---------------- 弹层 ---------------- */

    // ---------- 二级分类选择面板 ----------
    showSubCategoryFor?.let { parent ->
        val children = subCatsOf(parent.id)
        val parentColor = parseHexColor(parent.colorHex)
        AlertDialog(
            onDismissRequest = { showSubCategoryFor = null },
            title = { Text("${parent.name} · 选细分") },
            text = {
                Column {
                    Text(
                        "选一个更具体的，以后统计更清楚",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    children.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { child ->
                                val isSel = categoryId == child.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) parentColor.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            categoryId = child.id
                                            showSubCategoryFor = null
                                        }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        child.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSel) parentColor else TextPrimary,
                                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubCategoryFor = null }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 不细分，直接记在一级分类上
                    categoryId = parent.id
                    showSubCategoryFor = null
                }) { Text("不细分") }
            }
        )
    }

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
                                    accountTouched = true
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

/**
 * 数字键盘。
 *
 * 布局：左侧 3 列数字键，右侧 1 列功能键；「完成」跨两行高。
 * 这样右下角只有一个提交按钮 —— 之前把同一个保存动作挂了两个按钮（保存/完成），
 * 用户完全分不清区别，是设计失误。
 */
@Composable
private fun Keypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    val numberRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "00")
    )
    val rowHeight = 48.dp
    // 每格实际占 键高 + 上下各 3dp 内边距
    val gridHeight = rowHeight * numberRows.size + 6.dp * numberRows.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // ---- 左侧：数字 ----
        Column(modifier = Modifier.weight(3f)) {
            numberRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        KeyButton(
                            label = key,
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight),
                            onClick = { onKey(key) }
                        )
                    }
                }
            }
        }
        // ---- 右侧：退格 / 清空 / 完成（占满剩余两行）----
        Column(
            modifier = Modifier
                .weight(1f)
                .height(gridHeight)
        ) {
            FuncButton(
                label = "退格",
                icon = Icons.Filled.Backspace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                onClick = onBackspace
            )
            FuncButton(
                label = "清空",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                onClick = onClearAll
            )
            FuncButton(
                label = "完成",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                highlight = true,
                onClick = onDone
            )
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
