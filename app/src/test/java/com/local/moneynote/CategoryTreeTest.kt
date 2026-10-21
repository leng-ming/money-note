package com.local.moneynote

import com.local.moneynote.core.hasSubSlices
import com.local.moneynote.core.subCategorySlices
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.CategorySum
import com.local.moneynote.data.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类排行展开二级细分的拆分逻辑。
 *
 * 背景：`observeSumByCategory` 在数据库层就把二级分类并进了一级，
 * 界面上只看得见大类。要展开就必须另取一份不归并的汇总再合起来，
 * 这里测的就是「合起来之后对不对」。
 */
class CategoryTreeTest {

    private fun cat(id: Long, name: String, parent: Long? = null) = CategoryEntity(
        id = id,
        name = name,
        kind = TxKind.EXPENSE,
        parentId = parent,
        iconKey = "more",
        colorHex = "#FF607D8B"
    )

    private fun sum(id: Long, name: String, cents: Long) =
        CategorySum(id, name, "more", "#FF607D8B", cents, 1)

    /* ---------------- subCategorySlices ---------------- */

    @Test
    fun `没有子分类时返回空`() {
        val categories = listOf(cat(1, "餐饮"))
        val top = sum(1, "餐饮", 5000)
        val exact = listOf(sum(1, "餐饮", 5000))

        assertTrue(subCategorySlices(top, categories, exact).isEmpty())
    }

    @Test
    fun `只返回挂在这个一级下的二级`() {
        val categories = listOf(
            cat(1, "餐饮"),
            cat(2, "早餐", 1),
            cat(3, "购物"),
            cat(4, "数码", 3)
        )
        val top = sum(1, "餐饮", 3000)
        val exact = listOf(
            sum(2, "早餐", 3000),
            sum(4, "数码", 9999)
        )

        val rows = subCategorySlices(top, categories, exact)

        assertEquals(listOf("早餐"), rows.map { it.name })
        assertEquals(3000L, rows.single().totalCents)
    }

    @Test
    fun `按金额从大到小排`() {
        val categories = listOf(
            cat(1, "餐饮"),
            cat(2, "早餐", 1),
            cat(3, "午餐", 1),
            cat(4, "晚餐", 1)
        )
        val exact = listOf(
            sum(2, "早餐", 1000),
            sum(3, "午餐", 5000),
            sum(4, "晚餐", 3000)
        )
        val top = sum(1, "餐饮", 9000)

        val rows = subCategorySlices(top, categories, exact)

        assertEquals(listOf("午餐", "晚餐", "早餐"), rows.map { it.name })
    }

    @Test
    fun `金额为零的二级不占位置`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1), cat(3, "午餐", 1))
        val exact = listOf(sum(2, "早餐", 2000), sum(3, "午餐", 0))
        val top = sum(1, "餐饮", 2000)

        val rows = subCategorySlices(top, categories, exact)

        assertEquals(listOf("早餐"), rows.map { it.name })
    }

    @Test
    fun `一级自己也直接挂过账时补一条「未细分」`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1))
        // 一级合计 5000 = 早餐 3000 + 一级自己直接记的 2000
        val exact = listOf(sum(1, "餐饮", 2000), sum(2, "早餐", 3000))
        val top = sum(1, "餐饮", 5000)

        val rows = subCategorySlices(top, categories, exact)

        assertEquals(listOf("早餐", "未细分"), rows.map { it.name })
        assertEquals(2000L, rows.last().totalCents)
        // 关键：展开后各行加起来必须正好等于一级，否则用户会以为漏了钱
        assertEquals(top.totalCents, rows.sumOf { it.totalCents })
    }

    @Test
    fun `全都记在二级上时不会凭空多出「未细分」`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1), cat(3, "午餐", 1))
        val exact = listOf(sum(2, "早餐", 1200), sum(3, "午餐", 1800))
        val top = sum(1, "餐饮", 3000)

        val rows = subCategorySlices(top, categories, exact)

        assertEquals(listOf("午餐", "早餐"), rows.map { it.name })
        assertEquals(top.totalCents, rows.sumOf { it.totalCents })
    }

    @Test
    fun `子分类没出现在汇总里也不会崩`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1))
        val top = sum(1, "餐饮", 0)

        assertTrue(subCategorySlices(top, categories, emptyList()).isEmpty())
    }

    /* ---------------- hasSubSlices ---------------- */

    @Test
    fun `有子分类且本区间真的花了钱才显示箭头`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1))
        val top = sum(1, "餐饮", 2000)

        assertTrue(hasSubSlices(top, categories, listOf(sum(2, "早餐", 2000))))
    }

    @Test
    fun `子分类本区间没花钱就不显示箭头`() {
        val categories = listOf(cat(1, "餐饮"), cat(2, "早餐", 1))
        val top = sum(1, "餐饮", 2000)

        // 只有一级自己直接记的账，点开是空的 —— 不给箭头
        assertFalse(hasSubSlices(top, categories, listOf(sum(1, "餐饮", 2000))))
    }

    @Test
    fun `压根没有子分类时不给箭头`() {
        val categories = listOf(cat(1, "餐饮"), cat(3, "购物"))
        val top = sum(1, "餐饮", 2000)

        assertFalse(hasSubSlices(top, categories, listOf(sum(1, "餐饮", 2000))))
    }
}
