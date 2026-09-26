package com.local.moneynote.core

import com.local.moneynote.data.RecurFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 金额与日期是记账软件最容易出错、且出错代价最大的两处逻辑，所以单独锁死。
 * 这些测试跑在纯 JVM 上，不依赖手机。
 */
class MoneyTest {

    @Test
    fun `解析整数金额自动补两位小数`() {
        assertEquals(1200L, Money.parseToCents("12") ?: -1L)
        assertEquals(100L, Money.parseToCents("1") ?: -1L)
    }

    @Test
    fun `解析一位小数的金额`() {
        assertEquals(1230L, Money.parseToCents("12.3") ?: -1L)
    }

    @Test
    fun `解析标准两位小数`() {
        assertEquals(1234L, Money.parseToCents("12.34") ?: -1L)
        assertEquals(1L, Money.parseToCents("0.01") ?: -1L)
        assertEquals(99L, Money.parseToCents("0.99") ?: -1L)
    }

    @Test
    fun `解析时忽略千分位逗号`() {
        assertEquals(123456L, Money.parseToCents("1,234.56") ?: -1L)
        assertEquals(123456789L, Money.parseToCents("1,234,567.89") ?: -1L)
    }

    @Test
    fun `超过两位小数按四舍五入处理`() {
        assertEquals(1235L, Money.parseToCents("12.345") ?: -1L)
        assertEquals(1234L, Money.parseToCents("12.344") ?: -1L)
    }

    @Test
    fun `非法输入返回 null 而不是崩溃`() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("   "))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("."))
        assertNull(Money.parseToCents("-"))
        assertNull(Money.parseToCents("1.2.3"))
    }

    @Test
    fun `格式化带千分位`() {
        assertEquals("12.34", Money.format(1234L))
        assertEquals("0.00", Money.format(0L))
        assertEquals("0.05", Money.format(5L))
        assertEquals("1,234,567.89", Money.format(123456789L))
        assertEquals("-12.34", Money.format(-1234L))
    }

    @Test
    fun `无千分位格式化用于输入框回填`() {
        assertEquals("1234.56", Money.formatPlain(123456L))
        assertEquals("-1234.56", Money.formatPlain(-123456L))
        assertEquals("0.00", Money.formatPlain(0L))
    }

    @Test
    fun `格式化与解析互为逆运算`() {
        val samples = listOf(1L, 99L, 100L, 1234L, 100000L, 123456789L, 999999999999L)
        samples.forEach { cents ->
            val text = Money.formatPlain(cents)
            assertEquals("往返失败: $cents -> $text", cents, Money.parseToCents(text) ?: -1L)
        }
    }

    @Test
    fun `大额金额不溢出`() {
        // 一亿万元 = 1e14 分，远小于 Long 上限
        val big = 100_000_000_000_000L
        assertEquals(big, Money.parseToCents(Money.formatPlain(big)) ?: -1L)
    }
}

class DatesTest {

    @Test
    fun `月份区间是左闭右开且覆盖整月`() {
        val range = Dates.monthRange(2026, 9)
        val startDate = Dates.toLocalDate(range.first)
        val endDate = Dates.toLocalDate(range.last + 1)
        assertEquals(1, startDate.dayOfMonth)
        assertEquals(9, startDate.monthValue)
        assertEquals(1, endDate.dayOfMonth)
        assertEquals(10, endDate.monthValue)
    }

    @Test
    fun `闰年二月长度正确`() {
        assertEquals(29, Dates.daysInMonth(2024, 2))
        assertEquals(28, Dates.daysInMonth(2026, 2))
        assertEquals(31, Dates.daysInMonth(2026, 1))
        assertEquals(30, Dates.daysInMonth(2026, 4))
    }

    @Test
    fun `跨年查询十二月不会漏掉最后一天`() {
        val dec = Dates.monthRange(2026, 12)
        val lastDayStart = Dates.toLocalDate(dec.last)
        assertEquals(31, lastDayStart.dayOfMonth)
        assertEquals(12, lastDayStart.monthValue)
        // 右边界应落在 2027-01-01 00:00
        val nextYearStart = Dates.monthRange(2027, 1).first
        assertEquals(nextYearStart, dec.last + 1)
    }

    @Test
    fun `月份偏移跨年正确`() {
        assertEquals(2025 to 12, Dates.shiftMonth(2026, 1, -1))
        assertEquals(2027 to 1, Dates.shiftMonth(2026, 12, 1))
        assertEquals(2026 to 9, Dates.shiftMonth(2026, 9, 0))
        assertEquals(2025 to 9, Dates.shiftMonth(2026, 9, -12))
    }

    @Test
    fun `每日周期推进一天`() {
        val start = Dates.toMillis(LocalDate.of(2026, 9, 26))
        val next = Dates.nextOccurrence(start, RecurFreq.DAILY, 1)
        assertEquals(LocalDate.of(2026, 9, 27), Dates.toLocalDate(next))
    }

    @Test
    fun `每月周期在 1 月 31 日会安全收敛到 2 月末`() {
        val start = Dates.toMillis(LocalDate.of(2026, 1, 31))
        val next = Dates.nextOccurrence(start, RecurFreq.MONTHLY, 1)
        // java.time 的 plusMonths 会把不存在的 2 月 31 日收敛为 2 月 28 日，不会抛异常
        assertEquals(LocalDate.of(2026, 2, 28), Dates.toLocalDate(next))
    }

    @Test
    fun `闰日按年推进会收敛到 2 月 28 日`() {
        val start = Dates.toMillis(LocalDate.of(2024, 2, 29))
        val next = Dates.nextOccurrence(start, RecurFreq.YEARLY, 1)
        assertEquals(LocalDate.of(2025, 2, 28), Dates.toLocalDate(next))
    }

    @Test
    fun `每周周期推进七天`() {
        val start = Dates.toMillis(LocalDate.of(2026, 9, 26))
        val next = Dates.nextOccurrence(start, RecurFreq.WEEKLY, 1)
        assertEquals(LocalDate.of(2026, 10, 3), Dates.toLocalDate(next))
    }

    @Test
    fun `间隔为零或负数时至少推进一个周期避免死循环`() {
        val start = Dates.toMillis(LocalDate.of(2026, 9, 26))
        val nextZero = Dates.nextOccurrence(start, RecurFreq.DAILY, 0)
        assertEquals(LocalDate.of(2026, 9, 27), Dates.toLocalDate(nextZero))
        val nextNegative = Dates.nextOccurrence(start, RecurFreq.DAILY, -5)
        assertEquals(LocalDate.of(2026, 9, 27), Dates.toLocalDate(nextNegative))
    }

    @Test
    fun `区间起点始终落在当天零点`() {
        val someMoment = Dates.toMillis(
            LocalDate.of(2026, 9, 26),
            java.time.LocalTime.of(23, 59, 59)
        )
        val dayStart = Dates.startOfDay(someMoment)
        assertEquals(0, Dates.toLocalDateTime(dayStart).hour)
        assertEquals(0, Dates.toLocalDateTime(dayStart).minute)
        assertEquals(26, Dates.toLocalDate(dayStart).dayOfMonth)
    }
}
