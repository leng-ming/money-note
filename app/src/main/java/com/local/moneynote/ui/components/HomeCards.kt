package com.local.moneynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.moneynote.core.Money
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.BrandGreenLight
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.TextSecondary

/**
 * 主页顶部三张可左右滑的卡片。
 *
 * 顺序固定为：[资产] [收支] [预算]，默认停在中间的「收支」，
 * 于是「往左滑」看到预算、「往右滑」看到总资产 —— 正好对上直觉。
 */
const val HOME_PAGE_ASSETS = 0
const val HOME_PAGE_SUMMARY = 1
const val HOME_PAGE_BUDGET = 2
const val HOME_PAGE_COUNT = 3

/** 卡片统一高度。三张卡内容长短不一，固定高度才能让左右滑动时不跳来跳去 */
val HOME_CARD_HEIGHT = 172.dp

private val CardShape = RoundedCornerShape(16.dp)

@Composable
private fun CardShell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(BrandGreen, BrandGreenLight)))
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun CardAmount(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 卡片底部的一组「小标题 + 数值」 */
@Composable
private fun CardFootLine(vararg cells: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    value,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/* ------------------------------------------------------------------ */
/*  第 1 张：收支概览                                                   */
/* ------------------------------------------------------------------ */

@Composable
fun HomeSummaryCard(
    title: String,
    expense: Long,
    income: Long,
    dailyBudget: Long?,
    modifier: Modifier = Modifier
) {
    CardShell(modifier) {
        CardLabel(title)
        Spacer(Modifier.height(2.dp))
        CardAmount("¥ " + Money.format(expense))
        Spacer(Modifier.height(12.dp))
        CardFootLine(
            "收入" to "¥ " + Money.format(income),
            "结余" to "¥ " + Money.format(income - expense)
        )
        Spacer(Modifier.height(10.dp))
        if (dailyBudget != null) {
            Text(
                "剩余日均可用 ¥" + Money.format(dailyBudget),
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                "还没设总预算",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  第 2 张：预算                                                       */
/* ------------------------------------------------------------------ */

@Composable
fun HomeBudgetCard(
    budgetCents: Long,
    spentCents: Long,
    carryoverIn: Long,
    remainingDays: Int,
    modifier: Modifier = Modifier
) {
    if (budgetCents <= 0L) {
        CardShell(modifier) {
            CardLabel("本月预算")
            Spacer(Modifier.height(2.dp))
            CardAmount("未设置")
            Spacer(Modifier.height(10.dp))
            Text(
                "去「预算」页设一个额度，这里就会显示剩余预算和每天还能花多少",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        return
    }

    val available = budgetCents + carryoverIn
    val remaining = available - spentCents
    // 剩余日均 = 剩余预算 ÷ 剩余天数；已经超支或本月已过完就不给数字，免得误导
    val daily = if (remainingDays > 0 && remaining > 0) remaining / remainingDays else null
    val ratio = if (available > 0) (spentCents.toFloat() / available).coerceIn(0f, 1f) else 0f
    val over = remaining < 0

    CardShell(modifier) {
        CardLabel(if (over) "本月已超支" else "本月预算剩余")
        Spacer(Modifier.height(2.dp))
        CardAmount("¥ " + Money.format(if (over) -remaining else remaining))
        Spacer(Modifier.height(11.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (over) Color(0xFFFFCDD2) else Color.White)
            )
        }

        Spacer(Modifier.height(10.dp))
        CardFootLine(
            "剩余天数" to "$remainingDays 天",
            "日均可用" to (daily?.let { "¥" + Money.format(it) } ?: "—"),
            "已用" to "¥" + Money.format(spentCents)
        )
    }
}

/* ------------------------------------------------------------------ */
/*  第 3 张：总资产                                                     */
/* ------------------------------------------------------------------ */

@Composable
fun HomeAssetsCard(
    totalCents: Long,
    accountCount: Int,
    modifier: Modifier = Modifier
) {
    CardShell(modifier) {
        CardLabel("全部账户合计")
        Spacer(Modifier.height(2.dp))
        CardAmount("¥ " + Money.format(totalCents))
        Spacer(Modifier.height(10.dp))
        Text(
            if (accountCount > 0) "共 $accountCount 个账户 · 明细在下方"
            else "还没有账户",
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/* ------------------------------------------------------------------ */
/*  页码指示器                                                          */
/* ------------------------------------------------------------------ */

@Composable
fun HomeCardIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(HOME_PAGE_COUNT) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) BrandGreen else BrandGreen.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  资产页下方：账户余额列表                                            */
/* ------------------------------------------------------------------ */

/**
 * 账户列表里的一行。
 *
 * 单拎一个类型出来，是因为要带上账户 id —— 点进去看详情时得知道点的是谁。
 * 用 Triple 也能塞下，但 Long/String/Long 排在一起太容易传错顺序。
 */
data class AccountLine(
    val id: Long,
    val name: String,
    val balanceCents: Long
)

/**
 * 滑到「全部账户合计」那一页时，下方列表改显示这个。
 *
 * 为什么不塞进绿卡里：账户数量不可控，挤在小卡片里字又小又难看；
 * 余额本身信息量也不小，摊开来一行一个更好读。
 */
@Composable
fun AccountBalanceList(
    accounts: List<AccountLine>,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (accounts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有账户\n去「我的 → 账户管理」添加一个",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "账户余额",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        items(accounts, key = { it.id }) { acc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .clickable { onAccountClick(acc.id) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    acc.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "¥ " + Money.format(acc.balanceCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (acc.balanceCents < 0) ExpenseRed else Color.Unspecified
                )
            }
        }
    }
}
