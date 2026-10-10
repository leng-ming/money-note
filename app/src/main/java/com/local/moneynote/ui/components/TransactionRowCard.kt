package com.local.moneynote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.moneynote.core.Money
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import com.local.moneynote.ui.CatIcons
import com.local.moneynote.ui.parseHexColor
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.ExpenseRed
import com.local.moneynote.ui.theme.IncomeGreen
import com.local.moneynote.ui.theme.TextSecondary

/** 单条账单卡片，明细页与搜索结果共用 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRowCard(
    row: TransactionRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val catColor = parseHexColor(row.categoryColor)
    val isTransfer = row.kind == TxKind.TRANSFER
    val isExpense = row.kind == TxKind.EXPENSE

    // 转账没有分类，标题直接写成「从 → 到」，一眼就知道钱去哪了
    val title = if (isTransfer) {
        "${row.accountName} → ${row.toAccountName}"
    } else {
        row.categoryName
    }
    // 转账的副标题优先显示手续费 —— 那是转账唯一容易漏记的信息
    val subtitle = when {
        isTransfer && row.feeCents > 0L ->
            "手续费 ¥" + Money.format(row.feeCents) + "，实际到账 ¥" +
                Money.format(row.amountCents - row.feeCents)
        else -> row.note
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isTransfer) {
                        Icons.Filled.SwapHoriz
                    } else {
                        CatIcons.of(row.categoryIcon)
                    },
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        // 转账既不是花掉也不是赚到，所以不带正负号
                        isTransfer -> Money.format(row.amountCents)
                        isExpense -> "-" + Money.format(row.amountCents)
                        else -> "+" + Money.format(row.amountCents)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isTransfer -> BrandGreen
                        isExpense -> ExpenseRed
                        else -> IncomeGreen
                    }
                )
                // 转账的标题里已经有账户了，这里就不再重复
                if (!isTransfer) {
                    Text(
                        row.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
