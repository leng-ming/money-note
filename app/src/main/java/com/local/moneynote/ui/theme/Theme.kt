package com.local.moneynote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌绿 —— 大卡片背景、主按钮靠它，深浅色下都保持同一支品牌色 */
val BrandGreen = Color(0xFF2E7D32)
val BrandGreenDark = Color(0xFF1B5E20)
val BrandGreenLight = Color(0xFF60AD5E)

/** 支出用红、收入用绿 —— 全局统一，避免各处随手取色 */
val ExpenseRed = Color(0xFFE53935)
val IncomeGreen = Color(0xFF2E7D32)

/**
 * 下面这几个以前是写死的浅色常量，现在改成从 MaterialTheme 取。
 *
 * 这样一份代码自动适配深色 / 浅色，**所有使用处一行都不用改** ——
 * 如果把它们换成 CompositionLocal，就得去动 100 多处调用，得不偿失。
 *
 * 注意：因为是 @Composable get()，只能在 @Composable 上下文里读。
 */
val PageBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val TextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val DividerColor: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFF546E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3E7),
    onSecondaryContainer = Color(0xFF243038),
    tertiary = Color(0xFF00695C),
    onTertiary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF6B7178),
    outline = Color(0xFFD5D8DD),
    outlineVariant = Color(0xFFEDEFF2),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    // 深色下必须把品牌绿提亮，否则 #2E7D32 压在 #121212 上几乎看不见
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C262B),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFDCE3E7),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF00332E),
    // 不用纯黑 #000：OLED 上纯黑和内容对比过强，长时间看眼睛累
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2C2C2C),
    // 支出红也提亮一档，深底上的高饱和红会很刺眼
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MoneyNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
