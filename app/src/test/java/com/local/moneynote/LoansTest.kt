package com.local.moneynote

import com.local.moneynote.core.loanNote
import com.local.moneynote.core.loanSettleNote
import com.local.moneynote.core.loanTotals
import com.local.moneynote.core.settleAmount
import com.local.moneynote.data.LoanDirection
import com.local.moneynote.data.LoanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 借还的金额逻辑。
 *
 * 这些东西算错了不会崩，只会让「谁欠我多少钱」悄悄错掉 ——
 * 属于最难被发现的那类 bug，所以逐条钉住。
 */
class LoansTest {

    private fun loan(
        id: Long,
        direction: LoanDirection,
        amount: Long,
        settled: Long = 0L,
        person: String = "张三"
    ) = LoanEntity(
        id = id,
        direction = direction,
        person = person,
        amountCents = amount,
        settledCents = settled,
        accountId = 1L,
        occurredAt = 1_700_000_000_000L
    )

    /* ---------------- 汇总 ---------------- */

    @Test
    fun `借出算应收 借入算应付`() {
        val totals = loanTotals(
            listOf(
                loan(1, LoanDirection.LENT, 100_000L),
                loan(2, LoanDirection.LENT, 50_000L, settled = 20_000L),
                loan(3, LoanDirection.BORROWED, 30_000L)
            )
        )

        // 借出未收：1000 + (500 - 200)
        assertEquals(130_000L, totals.receivableCents)
        assertEquals(30_000L, totals.payableCents)
        assertEquals(3, totals.openCount)
        assertEquals(0, totals.settledCount)
    }

    @Test
    fun `已结清的不再占着欠款额度`() {
        val totals = loanTotals(
            listOf(
                loan(1, LoanDirection.LENT, 100_000L, settled = 100_000L),
                loan(2, LoanDirection.LENT, 10_000L)
            )
        )

        // 结清的那一千块已经收回来了，不该还算成「别人欠我」
        assertEquals(10_000L, totals.receivableCents)
        assertEquals(1, totals.openCount)
        assertEquals(1, totals.settledCount)
    }

    @Test
    fun `空列表得到全零`() {
        val totals = loanTotals(emptyList())
        assertEquals(0L, totals.receivableCents)
        assertEquals(0L, totals.payableCents)
        assertEquals(0, totals.openCount)
    }

    /* ---------------- 派生状态 ---------------- */

    @Test
    fun `部分还款后未还金额是剩余`() {
        val l = loan(1, LoanDirection.LENT, 100_000L, settled = 30_000L)

        assertEquals(70_000L, l.openCents)
        assertFalse(l.isSettled)
    }

    @Test
    fun `收满本金就算结清`() {
        val l = loan(1, LoanDirection.LENT, 100_000L, settled = 100_000L)

        assertEquals(0L, l.openCents)
        assertTrue(l.isSettled)
    }

    @Test
    fun `收多了也不会出现负数`() {
        // 理论上 settledCents 不该超过本金，但万一数据被弄脏，界面也不能显示 -500
        val l = loan(1, LoanDirection.LENT, 100_000L, settled = 100_500L)

        assertEquals(0L, l.openCents)
        assertTrue(l.isSettled)
    }

    /* ---------------- 结算金额 ---------------- */

    @Test
    fun `结算金额被夹到未还上限`() {
        val l = loan(1, LoanDirection.LENT, 100_000L, settled = 30_000L)

        // 用户手滑多打了，不能让他把 "已收回" 记成 5000
        assertEquals(70_000L, settleAmount(l, 500_000L))
        assertEquals(50_000L, settleAmount(l, 50_000L))
        assertEquals(0L, settleAmount(l, -1L))
    }

    @Test
    fun `已经结清的记录再结算得到零`() {
        val l = loan(1, LoanDirection.LENT, 10_000L, settled = 10_000L)

        assertEquals(0L, settleAmount(l, 10_000L))
    }

    /* ---------------- 备注 ---------------- */

    @Test
    fun `流水备注能说清楚是谁`() {
        assertEquals("借给 张三", loanNote(LoanDirection.LENT, "张三"))
        assertEquals("向 张三 借入", loanNote(LoanDirection.BORROWED, "张三"))

        assertEquals("张三 还钱", loanSettleNote(LoanDirection.LENT, "张三"))
        assertEquals("还给 张三", loanSettleNote(LoanDirection.BORROWED, "张三"))
    }
}
