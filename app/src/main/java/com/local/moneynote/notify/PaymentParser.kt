package com.local.moneynote.notify

/**
 * 从微信 / 支付宝的支付通知里抠出金额。
 *
 * 刻意只提取「金额 + 来源 + 收支方向」，不做商户名：
 * 支付通知里基本不带商户名（微信尤其如此），硬猜只会给用户添乱，
 * 还不如把金额填好、让用户自己补一句备注。
 *
 * 这层纯函数、无 Android 依赖，所以可以直接跑单元测试。
 */
object PaymentParser {

    /** 只关心这两个 App 的通知，其他一律不看 */
    val WATCHED_PACKAGES = setOf(
        "com.tencent.mm",              // 微信
        "com.eg.android.AlipayGphone"  // 支付宝
    )

    /**
     * 金额可能写成 ¥12.00、￥12、12.00元、12元、¥1,234.50。
     *
     * 小数点只认英文句点：之前写成 `[.,]` 时，「¥1,234.50」里的千分位逗号
     * 会被当成小数点，结果解析出 1.23 元 —— 单元测试抓到的真实 bug。
     * 所以这里把「带千分位」和「不带千分位」分成两个分支显式写出来。
     */
    private val AMOUNT_PATTERNS = listOf(
        Regex("""[¥￥]\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""),
        Regex("""(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*元""")
    )

    /** 必须命中其中之一，否则不认 —— 免得把聊天消息、广告也当成账单 */
    private val PAY_HINTS = listOf(
        "支付", "付款", "已付", "收款", "到账", "消费", "扣款", "转账"
    )

    /** 命中这些词就直接放掉：这些不是一笔新支出 */
    private val SKIP_HINTS = listOf(
        "退款", "红包", "优惠", "立减", "积分", "账单提醒", "余额提醒"
    )

    private const val WECHAT = "com.tencent.mm"
    /** 单笔上限 10 万元：超过基本是把别的数字认成了金额 */
    private const val MAX_CENTS = 10_000_00L

    data class Parsed(
        val amountCents: Long,
        /** "微信" / "支付宝" */
        val source: String,
        val isIncome: Boolean
    )

    /**
     * @param title 通知标题
     * @param body  通知正文（调用方应把 text / bigText 拼好传进来）
     */
    fun parse(packageName: String, title: String, body: String): Parsed? {
        if (packageName !in WATCHED_PACKAGES) return null

        val text = "$title $body"
        if (PAY_HINTS.none { text.contains(it) }) return null
        if (SKIP_HINTS.any { text.contains(it) }) return null

        val amountText = AMOUNT_PATTERNS
            .firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            ?: return null

        val cents = toCents(amountText) ?: return null
        if (cents <= 0L || cents > MAX_CENTS) return null

        val source = if (packageName == WECHAT) "微信" else "支付宝"
        val isIncome = text.contains("收款") || text.contains("到账") ||
            text.contains("已收钱") || text.contains("收入")

        return Parsed(cents, source, isIncome)
    }

    /** 把 "12.00" / "12" / "1,234.5" 转成分。用 Long 算，不碰浮点 */
    internal fun toCents(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null

        val parts = cleaned.split('.')
        if (parts.size > 2) return null

        val yuan = parts[0].toLongOrNull() ?: return null
        val frac = when (parts.size) {
            1 -> 0L
            else -> {
                val f = parts[1]
                if (f.isEmpty() || f.length > 2) return null
                f.padEnd(2, '0').take(2).toLongOrNull() ?: return null
            }
        }
        return yuan * 100 + frac
    }
}
