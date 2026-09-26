package com.local.moneynote.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.local.moneynote.data.AccountType

/**
 * 分类图标：数据库里只存 key，UI 层映射成矢量图。
 * 这样加图标不用改数据库结构。
 */
object CatIcons {

    /** 可供用户挑选的图标 key（新建/编辑分类时展示） */
    val all: List<String> = listOf(
        "food", "transport", "shopping", "home", "phone", "game",
        "medical", "edu", "gift", "salary", "bonus", "parttime",
        "invest", "redpacket", "more"
    )

    fun of(key: String): ImageVector = when (key) {
        "food" -> Icons.Filled.Restaurant
        "transport" -> Icons.Filled.DirectionsBus
        "shopping" -> Icons.Filled.ShoppingBag
        "home" -> Icons.Filled.Home
        "phone" -> Icons.Filled.PhoneAndroid
        "game" -> Icons.Filled.SportsEsports
        "medical" -> Icons.Filled.LocalHospital
        "edu" -> Icons.Filled.School
        "gift" -> Icons.Filled.CardGiftcard
        "salary" -> Icons.Filled.Payments
        "bonus" -> Icons.Filled.EmojiEvents
        "parttime" -> Icons.Filled.Work
        "invest" -> Icons.Filled.TrendingUp
        "redpacket" -> Icons.Filled.Redeem
        "more" -> Icons.Filled.MoreHoriz
        else -> Icons.Filled.Category
    }
}

/** 账户类型图标与默认配色 */
object AccountStyles {
    fun icon(type: AccountType): ImageVector = when (type) {
        AccountType.CASH -> Icons.Filled.Wallet
        AccountType.WECHAT -> Icons.Filled.Payments
        AccountType.ALIPAY -> Icons.Filled.AccountBalance
        AccountType.BANK -> Icons.Filled.AccountBalance
        AccountType.CREDIT -> Icons.Filled.Savings
        AccountType.OTHER -> Icons.Filled.Wallet
    }

    fun label(type: AccountType): String = when (type) {
        AccountType.CASH -> "现金"
        AccountType.WECHAT -> "微信"
        AccountType.ALIPAY -> "支付宝"
        AccountType.BANK -> "银行卡"
        AccountType.CREDIT -> "信用卡"
        AccountType.OTHER -> "其他"
    }
}

/** 把 #AARRGGBB 或 #RRGGBB 解析成 Compose Color，解析失败给个兜底灰 */
fun parseHexColor(hex: String, fallback: Color = Color(0xFF757575)): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val value = when (cleaned.length) {
            6 -> 0xFF000000L or cleaned.toLong(16)
            8 -> cleaned.toLong(16)
            else -> return fallback
        }
        Color(value.toInt())
    } catch (e: Exception) {
        fallback
    }
}
