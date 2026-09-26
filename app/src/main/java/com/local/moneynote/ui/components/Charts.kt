package com.local.moneynote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.moneynote.ui.theme.TextSecondary

data class ChartSlice(val label: String, val value: Long, val color: Color)

/**
 * 环形占比图。用 Canvas 直接画弧，不依赖任何图表库。
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 26.dp,
    centerTitle: String? = null,
    centerValue: String? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) return@Canvas
            val sw = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - sw
            if (diameter <= 0f) return@Canvas
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.value / total * 360f
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    // 留 1.2° 缝隙，让相邻色块有分隔感；但过小的扇区保底 0.6°，避免看不见
                    sweepAngle = (sweep - 1.2f).coerceAtLeast(0.6f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        if (centerValue != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerTitle != null) {
                    Text(
                        centerTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 每日柱状图。values[i] 表示第 i+1 天的金额。
 */
@Composable
fun DayBarChart(
    values: List<Long>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val maxV = (values.maxOrNull() ?: 0L).toFloat()
        if (maxV <= 0f) return@Canvas
        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtLeast(1.5f)
        values.forEachIndexed { index, value ->
            if (value <= 0L) return@forEachIndexed
            val h = (value / maxV) * (size.height - 2f)
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * 横向占比条，用于列表行内展示
 */
@Composable
fun RatioBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val r = 6.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.14f),
            cornerRadius = CornerRadius(r, r)
        )
        val w = (size.width * ratio.coerceIn(0f, 1f)).coerceAtLeast(2f)
        drawRoundRect(
            color = color,
            size = Size(w, size.height),
            cornerRadius = CornerRadius(r, r)
        )
    }
}
