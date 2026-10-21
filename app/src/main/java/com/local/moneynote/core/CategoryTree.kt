package com.local.moneynote.core

import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.CategorySum

/**
 * 把一个一级分类拆成「下面的细分」列表。
 *
 * 背景：分类排行原来用的是 [com.local.moneynote.data.TransactionDao.observeSumByCategory]，
 * 那句 SQL 是 `GROUP BY COALESCE(p.id, c.id)` —— 二级分类的账在**数据库层**就被并到一级头上，
 * 界面上自然只看得见大类，细分的金额和占比无从得知。
 *
 * 所以这里拿两份数据合起来算：
 *  - [top]      归并后的那一行（金额 = 自身 + 所有子分类，是权威合计）
 *  - [exact]    未归并的按分类汇总，能查到每个二级分类自己的金额
 *  - [categories] 用来判断「谁是谁的子分类」
 *
 * 规则：
 *  - 金额为 0 的二级分类不占位置；
 *  - 按金额从大到小排；
 *  - 若一级分类自己也直接挂过账（记账时没选二级），补一条「未细分」，
 *    保证展开后的各行加起来正好等于一级的金额，不会出现「加起来对不上」的困惑。
 *
 * 返回值恒为非空时才值得展开：调用方用 [hasSubSlices] 判断要不要显示箭头。
 */
fun subCategorySlices(
    top: CategorySum,
    categories: List<CategoryEntity>,
    exact: List<CategorySum>
): List<CategorySum> {
    val parentOf: Map<Long, Long?> = categories.associate { it.id to it.parentId }

    val rows = exact
        .filter { parentOf[it.categoryId] == top.categoryId && it.totalCents > 0 }
        .sortedByDescending { it.totalCents }

    if (rows.isEmpty()) return emptyList()

    val rest = top.totalCents - rows.sumOf { it.totalCents }
    if (rest <= 0) return rows

    // 一级分类直接挂的账凑不成某个细分，单独占一行，用一级的图标和颜色
    return rows + CategorySum(
        categoryId = top.categoryId,
        name = "未细分",
        iconKey = top.iconKey,
        colorHex = top.colorHex,
        totalCents = rest,
        cnt = 0
    )
}

/**
 * 这个一级分类有没有细分可展开。
 *
 * 只看 [categories] 里存不存在子分类就够了，不必碰金额 ——
 * 这样即使这个子分类本区间没花钱，也仍然显示箭头（点开是空的会被当成 bug）。
 * 真正决定展开后有几行的是 [subCategorySlices]，所以两处都有金额判断，
 * 这里额外要求子分类里至少有一个在本区间有金额。
 */
fun hasSubSlices(
    top: CategorySum,
    categories: List<CategoryEntity>,
    exact: List<CategorySum>
): Boolean {
    val childIds = categories.asSequence()
        .filter { it.parentId == top.categoryId }
        .map { it.id }
        .toSet()
    if (childIds.isEmpty()) return false
    return exact.any { it.categoryId in childIds && it.totalCents > 0 }
}
