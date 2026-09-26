package com.local.moneynote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌绿 */
val BrandGreen = Color(0xFF2E7D32)
val BrandGreenDark = Color(0xFF1B5E20)
val BrandGreenLight = Color(0xFF60AD5E)

/** 支出用红、收入用绿 —— 全局统一，避免各处随手取色 */
val ExpenseRed = Color(0xFFE53935)
val IncomeGreen = Color(0xFF2E7D32)

val PageBg = Color(0xFFF5F6F8)
val CardBg = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1B1B1B)
val TextSecondary = Color(0xFF6B7178)
val DividerColor = Color(0xFFEDEFF2)

private val AppColorScheme = lightColorScheme(
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
    background = PageBg,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD5D8DD),
    outlineVariant = Color(0xFFE6E9ED),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun MoneyNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
