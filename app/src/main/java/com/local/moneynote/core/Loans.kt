package com.local.moneynote.core

import com.local.moneynote.data.LoanDirection
import com.local.moneynote.data.LoanEntity

/**
 * 借还的整体面貌。
 *
 * 用「应收 / 应付」的说法而不是「借出 / 借入」，是因为用户真正关心的是
 * 「还有多少钱能要回来」和「我还欠着多少」，而不是发生过几笔。
 */
data class LoanTotals(
    /** 应收：别人欠我的（我借出去的、还没收回的部分） */
    val receivableCents: Long = 0L,
    /** 应付：我欠别人的（我借进来的、还没还掉的部分） */
    val payableCents: Long = 0L,
    /** 还没结清的笔数 */
    val openCount: Int = 0,
    /** 已经结清、只留档的笔数 */
    val settledCount: Int = 0
)

/** 把一堆借还记录压成两个数字。只算未结清的部分，结清的不该再占着"欠款"的额度 */
fun loanTotals(loans: List<LoanEntity>): LoanTotals {
    var receivable = 0L
    var payable = 0L
    var open = 0
    var settled = 0
    for (l in loans) {
        if (l.isSettled) {
            settled++
            continue
        }
        open++
        when (l.direction) {
            LoanDirection.LENT -> receivable += l.openCents
            LoanDirection.BORROWED -> payable += l.openCents
        }
    }
    return LoanTotals(receivable, payable, open, settled)
}

/**
 * 借出/借入时生成的那笔流水该写什么备注。
 *
 * 流水本身不带分类（categoryId = null），备注是账单列表里唯一的说明，
 * 所以它得自己把话说清楚 —— 半年后翻账单时才知道这 1000 块是怎么回事。
 */
fun loanNote(direction: LoanDirection, person: String): String = when (direction) {
    LoanDirection.LENT -> "借给 $person"
    LoanDirection.BORROWED -> "向 $person 借入"
}

/** 收回/还掉时的流水备注 */
fun loanSettleNote(direction: LoanDirection, person: String): String = when (direction) {
    LoanDirection.LENT -> "$person 还钱"
    LoanDirection.BORROWED -> "还给 $person"
}

/**
 * 这次到底能结算多少。
 *
 * 用户可能手滑输入超过未还金额的数（或者想"多收当利息"），
 * 一律夹到未还上限，免得 settledCents 超过本金让进度条变成负数。
 */
fun settleAmount(loan: LoanEntity, requested: Long): Long =
    requested.coerceIn(0L, loan.openCents)
