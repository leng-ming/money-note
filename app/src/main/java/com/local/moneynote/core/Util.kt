package com.local.moneynote.core

import com.local.moneynote.data.RecurFreq
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* ------------------------------------------------------------------ */
/*  金额：内部一律用「分」存 Long，杜绝浮点误差                          */
/* ------------------------------------------------------------------ */

object Money {

    /** 1234567 -> "12,345.67" */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        val grouped = yuan.toString().reversed().chunked(3).joinToString(",").reversed()
        return buildString {
            if (negative) append('-')
            append(grouped)
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }

    /** 不带千分位，用于输入框回填：1234567 -> "12345.67" */
    fun formatPlain(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        return buildString {
            if (negative) append('-')
            append(abs / 100)
            append('.')
            append((abs % 100).toString().padStart(2, '0'))
        }
    }

    /** "12.34" / "12" / "12.3" -> 1234 / 1200 / 1230；非法输入返回 null */
    fun parseToCents(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty() || t == "." || t == "-") return null
        val bd = t.toBigDecimalOrNull() ?: return null
        return try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: Exception) {
            null
        }
    }

    /** 供图表用的元值 */
    fun toYuan(cents: Long): Double = BigDecimal(cents).movePointLeft(2).toDouble()
}

/* ------------------------------------------------------------------ */
/*  时间：统一用 epoch millis，区间一律「左闭右开」                      */
/* ------------------------------------------------------------------ */

object Dates {

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayFmt = DateTimeFormatter.ofPattern("M月d日")
    private val dayFullFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy年M月")

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(date: LocalDate, time: java.time.LocalTime = java.time.LocalTime.MIDNIGHT): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate): Long = toMillis(date)

    fun startOfDay(millis: Long): Long = startOfDay(toLocalDate(millis))

    fun labelDay(millis: Long): String = toLocalDate(millis).format(dayFmt)

    fun labelDayFull(millis: Long): String = toLocalDate(millis).format(dayFullFmt)

    fun labelTime(millis: Long): String = toLocalDateTime(millis).format(timeFmt)

    fun labelMonth(year: Int, month: Int): String =
        LocalDate.of(year, month, 1).format(monthFmt)

    fun labelWeekday(millis: Long): String =
        when (toLocalDate(millis).dayOfWeek.value) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; else -> "周日"
        }

    /** 某年某月的 [起, 止) 毫秒区间 */
    fun monthRange(year: Int, month: Int): LongRange {
        val start = LocalDate.of(year, month, 1)
        val startMs = toMillis(start)
        val endMs = toMillis(start.plusMonths(1))
        return startMs until endMs
    }

    /** 某天的 [起, 止) */
    fun dayRange(date: LocalDate): LongRange {
        val startMs = toMillis(date)
        return startMs until toMillis(date.plusDays(1))
    }

    fun yearRange(year: Int): LongRange =
        toMillis(LocalDate.of(year, 1, 1)) until toMillis(LocalDate.of(year + 1, 1, 1))

    /** 相对今天偏移 n 个月的 (year, month) */
    fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val base = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
        return base.year to base.monthValue
    }

    fun daysInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).lengthOfMonth()

    /** 按周期频率推进下一次发生时间 */
    fun nextOccurrence(fromMillis: Long, freq: RecurFreq, interval: Int): Long {
        val step = interval.coerceAtLeast(1).toLong()
        val dt = toLocalDateTime(fromMillis)
        val next = when (freq) {
            RecurFreq.DAILY -> dt.plusDays(step)
            RecurFreq.WEEKLY -> dt.plusWeeks(step)
            RecurFreq.MONTHLY -> dt.plusMonths(step)
            RecurFreq.YEARLY -> dt.plusYears(step)
        }
        return toMillis(next)
    }
}
