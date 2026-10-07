package com.local.moneynote.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.moneynote.AppViewModel
import com.local.moneynote.core.Dates
import com.local.moneynote.core.Money
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.BrandGreenLight
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 单个账户的年度详情。
 *
 * 从「明细 → 总资产」那一页点某个账户进来，
 * 看这个账户今年每个月花掉多少，右上角可以直接改余额。
 */
@Composable
fun AccountDetailScreen(
    vm: AppViewModel,
    accountId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts by vm.allAccounts.collectAsState()
    val netByAccount by vm.netByAccount.collectAsState()
    val account = accounts.firstOrNull { it.id == accountId }

    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var showEditor by remember { mutableStateOf(false) }

    val range = remember(year) { Dates.yearRange(year) }
    val monthSums by remember(accountId, year) {
        vm.repo.observeSumByMonthForAccount(
            accountId,
            TxKind.EXPENSE,
            range.first,
            range.last + 1
        )
    }.collectAsState(emptyList())

    val yearTotal = remember(monthSums) { monthSums.sumOf { it.totalCents } }
    val peak = remember(monthSums) { monthSums.maxOfOrNull { it.totalCents } ?: 0L }
    val balance = account?.let { it.initialBalanceCents + (netByAccount[it.id] ?: 0L) } ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Android 15 起强制 edge-to-edge，不处理的话顶栏会钻到状态栏底下
            .safeDrawingPadding()
    ) {

        // ---------- 顶栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                account?.name ?: "账户",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showEditor = true }) { Text("编辑") }
        }

        // ---------- 余额卡 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(BrandGreen, BrandGreenLight)))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    "当前余额",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "¥ " + Money.format(balance),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    Column {
                        Text(
                            "$year 年支出",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "¥ " + Money.format(yearTotal),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        // ---------- 年份切换 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { year-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年")
            }
            Text(
                "${year}年",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IconButton(
                onClick = { year++ },
                // 没必要看未来的年份
                enabled = year < LocalDate.now().year
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一年"
                )
            }
        }

        // ---------- 12 个月 ----------
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    "每月支出",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            items(12) { index ->
                val month = index + 1
                val amount = monthSums
                    .firstOrNull { it.month == "%04d-%02d".format(year, month) }
                    ?.totalCents ?: 0L
                MonthSpendRow(
                    month = month,
                    amountCents = amount,
                    peakCents = peak,
                    isCurrentMonth = year == LocalDate.now().year &&
                        month == LocalDate.now().monthValue
                )
            }
        }
    }

    if (showEditor && account != null) {
        AccountEditorDialog(
            initial = account,
            onDismiss = { showEditor = false },
            onSave = { name, type, initialCents ->
                scope.launch {
                    vm.repo.updateAccount(
                        account.copy(
                            name = name,
                            type = type,
                            initialBalanceCents = initialCents
                        )
                    )
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
                showEditor = false
            },
            onDelete = {
                showEditor = false
                scope.launch {
                    val count = vm.repo.accountTxCount(account.id)
                    if (count > 0) {
                        Toast.makeText(context, "该账户下还有 $count 笔账，不能删除", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.repo.deleteAccount(account)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            }
        )
    }
}

/** 一行：月份 + 横向柱状 + 金额 */
@Composable
private fun MonthSpendRow(
    month: Int,
    amountCents: Long,
    peakCents: Long,
    isCurrentMonth: Boolean
) {
    // 柱长按「今年最高的那个月」归一化，这样月份之间能横向比
    val ratio = if (peakCents > 0) (amountCents.toFloat() / peakCents).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${month}月",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrentMonth) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrentMonth) BrandGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(42.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isCurrentMonth) BrandGreen else BrandGreen.copy(alpha = 0.55f))
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        Text(
            if (amountCents > 0) "¥" + Money.format(amountCents) else "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (amountCents > 0) MaterialTheme.colorScheme.onSurface else TextSecondary,
            modifier = Modifier.width(84.dp)
        )
    }
}
