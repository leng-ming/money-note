package com.local.moneynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.local.moneynote.Granularity
import com.local.moneynote.ui.theme.BrandGreen
import com.local.moneynote.ui.theme.CardBg
import com.local.moneynote.ui.theme.TextSecondary

/** 月份切换条：< 2026年9月 >  （非当月时标题带 ⟲ 提示可点回本月） */
@Composable
fun MonthSwitcher(
    label: String,
    isCurrent: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        Text(
            text = label + if (isCurrent) "" else "  ⟲",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTitleClick)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
        }
        if (trailing != null) trailing()
    }
}

/**
 * 明细 / 图表页顶部：区间翻页 + 年·月·日 粒度切换。
 * 预算页仍用 [MonthSwitcher]（预算固定按自然月算，不受粒度影响）。
 */
@Composable
fun PeriodBar(
    granularity: Granularity,
    label: String,
    isCurrent: Boolean,
    onGranularityChange: (Granularity) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一页")
            }
            Text(
                text = label + if (isCurrent) "" else "  ⟲",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTitleClick)
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一页")
            }
            if (trailing != null) trailing()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GranularityChip("年", granularity == Granularity.YEAR) { onGranularityChange(Granularity.YEAR) }
            Spacer(Modifier.width(8.dp))
            GranularityChip("月", granularity == Granularity.MONTH) { onGranularityChange(Granularity.MONTH) }
            Spacer(Modifier.width(8.dp))
            GranularityChip("日", granularity == Granularity.DAY) { onGranularityChange(Granularity.DAY) }
        }
    }
}

@Composable
private fun GranularityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) BrandGreen.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) BrandGreen else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 统一的白色圆角分区卡片 */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

/** 空数据提示 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/** 通用筛选胶囊按钮 */
@Composable
fun SelectChipBtn(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** 小间距 */
@Composable
fun HGap(width: Int) {
    Spacer(Modifier.width(width.dp))
}
