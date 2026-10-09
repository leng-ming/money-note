package com.local.moneynote.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 支付通知解析的测试。
 *
 * 这些用例都是照着微信 / 支付宝真实的通知文案写的 ——
 * 解析规则一旦改坏，用户看到的就不是「金额填错」而是「凭空多出一笔账」，
 * 所以这里覆盖得细一点。
 */
class PaymentParserTest {

    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"

    /* ---------------- 金额识别 ---------------- */

    @Test
    fun `微信 带人民币符号`() {
        val r = PaymentParser.parse(wechat, "微信支付", "微信支付凭证 ¥12.00")
        assertEquals(1200L, r?.amountCents)
        assertEquals("微信", r?.source)
        assertFalse(r!!.isIncome)
    }

    @Test
    fun `微信 用元结尾`() {
        val r = PaymentParser.parse(wechat, "微信支付", "已支付 8.5元")
        assertEquals(850L, r?.amountCents)
    }

    @Test
    fun `支付宝 付款成功`() {
        val r = PaymentParser.parse(alipay, "支付宝", "成功付款 25.00元")
        assertEquals(2500L, r?.amountCents)
        assertEquals("支付宝", r?.source)
    }

    @Test
    fun `整数金额没有小数点`() {
        val r = PaymentParser.parse(wechat, "微信支付", "已支付 100元")
        assertEquals(10000L, r?.amountCents)
    }

    @Test
    fun `带千分位逗号`() {
        val r = PaymentParser.parse(wechat, "微信支付", "付款 ¥1,234.50")
        assertEquals(123450L, r?.amountCents)
    }

    @Test
    fun `金额出现在正文 bigText 里`() {
        val r = PaymentParser.parse(
            alipay,
            "支付宝",
            "你已成功付款，金额 ¥66.60，点击查看详情"
        )
        assertEquals(6660L, r?.amountCents)
    }

    /* ---------------- 收支方向 ---------------- */

    @Test
    fun `收款算收入`() {
        val r = PaymentParser.parse(wechat, "微信收款", "微信支付收款到账 ¥20.00")
        assertTrue(r!!.isIncome)
    }

    @Test
    fun `已收钱算收入`() {
        val r = PaymentParser.parse(alipay, "支付宝", "已收钱 30.00元")
        assertTrue(r!!.isIncome)
    }

    /* ---------------- 该放过的 ---------------- */

    @Test
    fun `聊天消息不算账`() {
        assertNull(PaymentParser.parse(wechat, "张三", "在吗？晚上吃饭"))
    }

    @Test
    fun `退款不记成支出`() {
        assertNull(PaymentParser.parse(alipay, "支付宝", "退款成功 ¥12.00"))
    }

    @Test
    fun `优惠券提醒不记`() {
        assertNull(PaymentParser.parse(alipay, "支付宝", "你有 3 元优惠券即将过期"))
    }

    @Test
    fun `其他 App 的通知一律不看`() {
        assertNull(PaymentParser.parse("com.other.app", "支付", "已支付 ¥10.00"))
    }

    @Test
    fun `没有金额就不记`() {
        assertNull(PaymentParser.parse(wechat, "微信支付", "支付成功"))
    }

    @Test
    fun `金额为 0 不记`() {
        assertNull(PaymentParser.parse(wechat, "微信支付", "已支付 ¥0.00"))
    }

    @Test
    fun `金额大得离谱不记`() {
        // 20 万：多半是把订单号之类的数字认成了金额
        assertNull(PaymentParser.parse(wechat, "微信支付", "已支付 ¥200000.00"))
    }

    /* ---------------- 金额换算 ---------------- */

    @Test
    fun `分转元 各种写法`() {
        assertEquals(1200L, PaymentParser.toCents("12.00"))
        assertEquals(1200L, PaymentParser.toCents("12"))
        assertEquals(1250L, PaymentParser.toCents("12.5"))
        assertEquals(100000L, PaymentParser.toCents("1000"))
        assertEquals(1L, PaymentParser.toCents("0.01"))
    }

    @Test
    fun `非法金额返回 null`() {
        assertNull(PaymentParser.toCents(""))
        assertNull(PaymentParser.toCents("abc"))
        assertNull(PaymentParser.toCents("1.234"))
        assertNull(PaymentParser.toCents("1.2.3"))
    }
}
