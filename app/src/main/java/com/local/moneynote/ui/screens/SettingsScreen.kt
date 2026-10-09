package com.local.moneynote.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Backup
import com.local.moneynote.core.Money
import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.AccountType
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.TxKind
import com.local.moneynote.notify.PaymentNotificationListener
import com.local.moneynote.ui.AccountStyles
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.components.SectionCard
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextPrimary
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val PRESET_COLORS = listOf(
    "#FFEF6C00", "#FFD81B60", "#FF8E24AA", "#FF3949AB",
    "#FF1E88E5", "#FF00897B", "#FF43A047", "#FF7CB342",
    "#FFF4511E", "#FF6D4C41", "#FF546E7A", "#FF757575"
)

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenRecurring: () -> Unit
) {
    val allAccounts by vm.allAccounts.collectAsState()
    val netByAccount by vm.netByAccount.collectAsState()
    val categories by vm.allCategories.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAccountEditor by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    // 自动记账是否已授权。授权是在系统设置里做的，用户点完返回时状态会变，
    // 所以监听 ON_RESUME 重新查一次，否则界面会一直显示「未开启」。
    var notifyGranted by remember {
        mutableStateOf(PaymentNotificationListener.isGranted(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifyGranted = PaymentNotificationListener.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // 写文件属于阻塞 IO，绝不能放在主线程跑（数据量大时会 ANR）
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = Backup.toJson(vm.repo.snapshotForBackup())
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        } ?: error("无法写入所选位置")
                    }
                }
                result.onSuccess { toast("备份已保存") }
                    .onFailure { toast("导出失败：${it.message}") }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val rows = vm.repo
                            .search(0L, Long.MAX_VALUE, "", null, null, null, 0L, null)
                            .first()
                        val csv = Backup.toCsv(rows)
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("无法写入所选位置")
                    }
                }
                result.onSuccess { toast("表格已导出") }
                    .onFailure { toast("导出失败：${it.message}") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    val activeAccounts = allAccounts.filter { !it.archived }
    val totalAssets = activeAccounts.sumOf { it.initialBalanceCents + (netByAccount[it.id] ?: 0L) }
    val today = LocalDate.now().toString()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // ---------- 总资产 ----------
        item {
            SectionCard(title = "全部账户余额合计") {
                Text(
                    "¥ " + Money.format(totalAssets),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "数据全部保存在这台手机上，不联网、不上传",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // ---------- 账户 ----------
        item {
            SectionCard(title = "账户") {
                allAccounts.forEach { acc ->
                    val balance = acc.initialBalanceCents + (netByAccount[acc.id] ?: 0L)
                    ManageRow(
                        icon = AccountStyles.icon(acc.type),
                        iconColor = parseHexColor(acc.colorHex),
                        title = acc.name + if (acc.archived) "（已停用）" else "",
                        subtitle = AccountStyles.label(acc.type) + " · ¥" + Money.format(balance),
                        onClick = {
                            editAccount = acc
                            showAccountEditor = true
                        }
                    )
                }
                AddRow(text = "添加账户") {
                    editAccount = null
                    showAccountEditor = true
                }
            }
        }

        // ---------- 分类 ----------
        item {
            SectionCard(title = "支出分类") {
                CategoryGrid(
                    // 只列一级分类，二级细分在各自的编辑对话框里管理
                    list = categories.filter { it.kind == TxKind.EXPENSE && it.parentId == null },
                    onClick = {
                        editCategory = it
                        showCategoryEditor = true
                    }
                )
                AddRow(text = "添加支出分类") {
                    editCategory = null
                    showCategoryEditor = true
                }
            }
        }

        item {
            SectionCard(title = "收入分类") {
                CategoryGrid(
                    list = categories.filter { it.kind == TxKind.INCOME && it.parentId == null },
                    onClick = {
                        editCategory = it
                        showCategoryEditor = true
                    }
                )
                AddRow(text = "添加收入分类") {
                    editCategory = null
                    showCategoryEditor = true
                }
            }
        }

        // ---------- 数据 ----------
        // ---------- 自动记账 ----------
        item {
            SectionCard(title = "自动记账") {
                ManageRow(
                    icon = Icons.Filled.NotificationsActive,
                    iconColor = if (notifyGranted) BrandGreen else TextSecondary,
                    title = if (notifyGranted) "已开启" else "去开启自动记账",
                    subtitle = if (notifyGranted) {
                        "微信 / 支付宝付款后会提醒你，点一下就能记好"
                    } else {
                        "需要授权「通知使用权」，只读取微信和支付宝的支付通知"
                    },
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                )
                Text(
                    "只解析通知里的金额，不做任何上传。App 依旧没有网络权限，" +
                        "数据不会离开这台手机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }

        item {
            SectionCard(title = "数据与备份") {
                ManageRow(
                    icon = Icons.Filled.Repeat,
                    iconColor = BrandGreen,
                    title = "周期账单",
                    subtitle = "房租、话费、订阅自动记账",
                    onClick = onOpenRecurring
                )
                ManageRow(
                    icon = Icons.Filled.FileDownload,
                    iconColor = BrandGreen,
                    title = "导出完整备份",
                    subtitle = "含全部账单/账户/预算，.json 格式",
                    onClick = { exportJsonLauncher.launch("记账备份-$today.json") }
                )
                ManageRow(
                    icon = Icons.Filled.TableChart,
                    iconColor = BrandGreen,
                    title = "导出账单表格",
                    subtitle = "Excel 可直接打开的 .csv",
                    onClick = { exportCsvLauncher.launch("账单明细-$today.csv") }
                )
                ManageRow(
                    icon = Icons.Filled.FileUpload,
                    iconColor = ExpenseRed,
                    title = "从备份恢复",
                    subtitle = "会用备份内容覆盖当前数据",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
        }

        // ---------- 关于 ----------
        item {
            SectionCard(title = "关于") {
                Text(
                    "本地记账 · 纯离线版本",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "所有数据存放在手机内置 SQLite 数据库里，应用没有任何网络权限，"
                        + "所以在飞行模式、地铁隧道、欠费断网时都能正常记账和查账。"
                        + "建议定期用上面的「导出完整备份」把数据存一份到手机之外。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    /* ---------------- 弹层 ---------------- */

    if (showAccountEditor) {
        AccountEditorDialog(
            initial = editAccount,
            onDismiss = { showAccountEditor = false },
            onSave = { name, type, initialCents ->
                scope.launch {
                    val editing = editAccount
                    if (editing == null) {
                        vm.repo.addAccount(name, type, initialCents)
                    } else {
                        vm.repo.updateAccount(
                            editing.copy(
                                name = name,
                                type = type,
                                initialBalanceCents = initialCents
                            )
                        )
                    }
                }
                showAccountEditor = false
            },
            onDelete = {
                val editing = editAccount
                showAccountEditor = false
                if (editing != null) {
                    scope.launch {
                        val count = vm.repo.accountTxCount(editing.id)
                        if (count > 0) {
                            toast("该账户下还有 $count 笔账，不能删除")
                        } else {
                            vm.repo.deleteAccount(editing)
                            toast("已删除")
                        }
                    }
                }
            }
        )
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            initial = editCategory,
            children = editCategory?.let { parent -> categories.filter { it.parentId == parent.id } }
                ?: emptyList(),
            onDismiss = { showCategoryEditor = false },
            onSave = { name, iconKey, colorHex, kind ->
                scope.launch {
                    val editing = editCategory
                    if (editing == null) {
                        vm.repo.addCategory(name, kind, iconKey, colorHex)
                    } else {
                        vm.repo.updateCategory(
                            editing.copy(name = name, iconKey = iconKey, colorHex = colorHex)
                        )
                    }
                }
                showCategoryEditor = false
            },
            onDelete = {
                val editing = editCategory
                showCategoryEditor = false
                if (editing != null) {
                    scope.launch {
                        val count = vm.repo.categoryTxCount(editing.id)
                        if (count > 0) {
                            toast("该分类下还有 $count 笔账，不能删除")
                        } else {
                            // 连带清掉它下面的二级细分，免得留下孤儿行
                            categories.filter { it.parentId == editing.id }
                                .forEach { vm.repo.deleteCategory(it) }
                            vm.repo.deleteCategory(editing)
                            toast("已删除")
                        }
                    }
                }
            },
            onAddChild = { childName ->
                val parent = editCategory
                if (parent != null) {
                    scope.launch {
                        vm.repo.addSubCategory(parent.id, childName, parent.iconKey, parent.colorHex)
                    }
                }
            },
            onDeleteChild = { child ->
                scope.launch {
                    val count = vm.repo.categoryTxCount(child.id)
                    if (count > 0) {
                        toast("「${child.name}」下还有 $count 笔账，不能删除")
                    } else {
                        vm.repo.deleteCategory(child)
                    }
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text("确认恢复？") },
            text = {
                Text("当前的账单、账户、分类、预算会被备份文件里的内容完全覆盖，且无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    showImportConfirm = false
                    pendingImportUri = null
                    if (uri != null) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val text = context.contentResolver.openInputStream(uri)
                                        ?.bufferedReader()
                                        ?.use { it.readText() }
                                        ?: error("无法读取文件")
                                    val payload = Backup.fromJson(text)
                                    vm.repo.restoreFrom(payload)
                                }
                            }
                            result.onSuccess { toast("恢复完成") }
                                .onFailure { toast("恢复失败：${it.message}") }
                        }
                    }
                }) {
                    Text("覆盖恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) { Text("取消") }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  通用行                                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun ManageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(BrandGreen.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = BrandGreen, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryGrid(list: List<CategoryEntity>, onClick: (CategoryEntity) -> Unit) {
    if (list.isEmpty()) {
        Text(
            "暂无分类",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }
    Column {
        list.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                rowItems.forEach { c ->
                    val color = parseHexColor(c.colorHex)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick(c) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                CatIcons.of(c.iconKey),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            c.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  账户编辑                                                           */
/* ------------------------------------------------------------------ */

@Composable
fun AccountEditorDialog(
    initial: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, initialCents: Long) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.CASH) }
    var balanceText by remember {
        mutableStateOf(initial?.let { Money.formatPlain(it.initialBalanceCents) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加账户" else "编辑账户") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' || it == '-' }
                        if (filtered.count { it == '.' } <= 1 &&
                            filtered.substringAfter('.', "").length <= 2 &&
                            filtered.length <= 14
                        ) {
                            balanceText = filtered
                        }
                    },
                    label = { Text("初始余额") },
                    prefix = { Text("¥ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("账户类型", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(7.dp))
                AccountType.entries.chunked(3).forEach { rowTypes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTypes.forEach { t ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (type == t) BrandGreen.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { type = t }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    AccountStyles.label(t),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (type == t) BrandGreen else TextSecondary,
                                    fontWeight = if (type == t) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val cents = Money.parseToCents(balanceText) ?: 0L
                    onSave(name.trim(), type, cents)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/* ------------------------------------------------------------------ */
/*  分类编辑                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun CategoryEditorDialog(
    initial: CategoryEntity?,
    children: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, colorHex: String, kind: TxKind) -> Unit,
    onDelete: () -> Unit,
    onAddChild: (String) -> Unit,
    onDeleteChild: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "more") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: PRESET_COLORS.first()) }
    var kind by remember { mutableStateOf(initial?.kind ?: TxKind.EXPENSE) }
    var newChildName by remember { mutableStateOf("") }

    val accent = parseHexColor(colorHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加分类" else "编辑分类") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 8) name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (initial == null) {
                    Spacer(Modifier.height(12.dp))
                    Text("类型", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (kind == TxKind.EXPENSE) ExpenseRed.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { kind = TxKind.EXPENSE }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "支出",
                                color = if (kind == TxKind.EXPENSE) ExpenseRed else TextSecondary,
                                fontWeight = if (kind == TxKind.EXPENSE) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (kind == TxKind.INCOME) IncomeGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { kind = TxKind.INCOME }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "收入",
                                color = if (kind == TxKind.INCOME) IncomeGreen else TextSecondary,
                                fontWeight = if (kind == TxKind.INCOME) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(7.dp))
                CatIcons.all.chunked(5).forEach { rowIcons ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        rowIcons.forEach { key ->
                            val selected = iconKey == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 3.dp)
                                    .height(42.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) accent else accent.copy(alpha = 0.12f))
                                    .clickable { iconKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    CatIcons.of(key),
                                    contentDescription = null,
                                    tint = if (selected) Color.White else accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        repeat(5 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(7.dp))
                PRESET_COLORS.chunked(6).forEach { rowColors ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowColors.forEach { hex ->
                            val c = parseHexColor(hex)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (colorHex == hex) 3.dp else 0.dp,
                                        color = if (colorHex == hex) TextPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                }

                // ---- 二级细分管理（只有已保存的一级分类才能挂细分）----
                if (initial != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("细分分类", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "记账时点「${initial.name}」会弹出这些细分，方便把账记到奶茶、咖啡这一层",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    if (children.isEmpty()) {
                        Text(
                            "还没有细分",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    } else {
                        children.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { child ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            child.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "删除细分",
                                            tint = TextSecondary,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onDeleteChild(child) }
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newChildName,
                            onValueChange = { if (it.length <= 6) newChildName = it },
                            label = { Text("新增细分") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val n = newChildName.trim()
                            if (n.isNotEmpty()) {
                                onAddChild(n)
                                newChildName = ""
                            }
                        }) { Text("添加") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name.trim(), iconKey, colorHex, kind)
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
