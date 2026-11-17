package com.local.moneynote.core

import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.AccountType
import com.local.moneynote.data.BackupPayload
import com.local.moneynote.data.BudgetEntity
import com.local.moneynote.data.BudgetPeriod
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.LoanDirection
import com.local.moneynote.data.LoanEntity
import com.local.moneynote.data.RecurFreq
import com.local.moneynote.data.RecurringEntity
import com.local.moneynote.data.TransactionEntity
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 备份/恢复直接关系到用户的数据能不能保住，属于"错了就不可挽回"的功能，
 * 所以对序列化往返做完整校验。
 */
class BackupTest {

    private val samplePayload = BackupPayload(
        accounts = listOf(
            AccountEntity(
                id = 1L, name = "现金", type = AccountType.CASH,
                initialBalanceCents = 10000L, colorHex = "#FF43A047",
                sortOrder = 0, archived = false, createdAt = 1_700_000_000_000L
            ),
            AccountEntity(
                id = 2L, name = "招商银行卡", type = AccountType.BANK,
                initialBalanceCents = -12345L, colorHex = "#FF5C6BC0",
                sortOrder = 1, archived = true, createdAt = 1_700_000_000_001L
            )
        ),
        categories = listOf(
            CategoryEntity(
                id = 1L, name = "餐饮", kind = TxKind.EXPENSE, iconKey = "food",
                colorHex = "#FFEF6C00", sortOrder = 0, archived = false, isSystem = true
            ),
            CategoryEntity(
                id = 3L, name = "午餐", kind = TxKind.EXPENSE, parentId = 1L,
                iconKey = "food", colorHex = "#FFEF6C00", sortOrder = 0,
                archived = false, isSystem = true
            ),
            CategoryEntity(
                id = 2L, name = "工资", kind = TxKind.INCOME, iconKey = "salary",
                colorHex = "#FF2E7D32", sortOrder = 1, archived = false, isSystem = true
            )
        ),
        transactions = listOf(
            TransactionEntity(
                id = 1L, amountCents = 2350L, kind = TxKind.EXPENSE,
                accountId = 1L, categoryId = 1L,
                note = "午饭, 带\"引号\"和\n换行 🐟 以及中文",
                occurredAt = 1_700_000_000_000L,
                createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
                excludeFromStats = false
            ),
            TransactionEntity(
                id = 2L, amountCents = 999_999_99L, kind = TxKind.INCOME,
                accountId = 2L, categoryId = 2L, note = "",
                occurredAt = 1_700_000_000_001L,
                createdAt = 1_700_000_000_001L, updatedAt = 1_700_000_000_002L,
                excludeFromStats = true
            )
        ),
        budgets = listOf(
            // categoryId 为 null 表示总预算，必须能正确往返
            BudgetEntity(
                id = 1L, categoryId = null, amountCents = 500_000L,
                period = BudgetPeriod.MONTHLY, enabled = true, createdAt = 1_700_000_000_000L
            ),
            BudgetEntity(
                id = 2L, categoryId = 1L, amountCents = 120_000L,
                period = BudgetPeriod.YEARLY, enabled = false, createdAt = 1_700_000_000_001L
            )
        ),
        recurring = listOf(
            RecurringEntity(
                id = 1L, name = "房租", amountCents = 200_000L, kind = TxKind.EXPENSE,
                accountId = 1L, categoryId = 1L, note = "每月1号",
                freq = RecurFreq.MONTHLY, intervalCount = 1,
                nextOccurAt = 1_700_000_000_000L,
                lastGeneratedAt = null,      // 从未生成过，必须是 null 而不是 0
                enabled = true
            )
        )
    )

    @Test
    fun `备份文件是合法 JSON 且带版本号`() {
        val json = Backup.toJson(samplePayload)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"accounts\""))
        assertTrue(json.contains("\"transactions\""))
    }

    @Test
    fun `完整往返后五张表的所有字段都一致`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))

        assertEquals(samplePayload.accounts, restored.accounts)
        assertEquals(samplePayload.categories, restored.categories)
        assertEquals(samplePayload.transactions, restored.transactions)
        assertEquals(samplePayload.budgets, restored.budgets)
        assertEquals(samplePayload.recurring, restored.recurring)
    }

    @Test
    fun `备注里的引号换行和 emoji 不会破坏 JSON`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        val tx = restored.transactions.first { it.id == 1L }
        assertEquals("午饭, 带\"引号\"和\n换行 🐟 以及中文", tx.note)
    }

    @Test
    fun `总预算的 null 分类能正确往返`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        val total = restored.budgets.first { it.id == 1L }
        assertNull(total.categoryId)
        assertNotNull(restored.budgets.first { it.id == 2L }.categoryId)
    }

    @Test
    fun `周期账单的 null 上次生成时间能正确往返`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        assertNull(restored.recurring.first().lastGeneratedAt)
    }

    @Test
    fun `二级分类的父子关系能正确往返`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        assertEquals(1L, restored.categories.first { it.id == 3L }.parentId)
    }

    @Test
    fun `不支持的备份版本会明确失败`() {
        // 不写死 toJson 的缩进格式：直接改 JSON 对象，版本号变了也不会挂
        val json = JSONObject(Backup.toJson(samplePayload)).put("version", 999).toString(2)
        try {
            Backup.fromJson(json)
            throw AssertionError("应该拒绝未知备份版本")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("备份版本"))
        }
    }

    @Test
    fun `老备份没有版本号和 parentId 也能导入`() {
        val root = JSONObject(Backup.toJson(samplePayload))
        root.remove("version")
        // 模拟 v1 的导出文件：categories 里根本没有 parentId 这个字段
        val cats = root.getJSONArray("categories")
        for (i in 0 until cats.length()) cats.getJSONObject(i).remove("parentId")

        val restored = Backup.fromJson(root.toString())

        assertTrue(restored.categories.all { it.parentId == null })
        assertEquals(samplePayload.categories.size, restored.categories.size)
        assertEquals(samplePayload.transactions.size, restored.transactions.size)
    }

    @Test
    fun `损坏的条目会明确报错而不是静默丢掉`() {
        val root = JSONObject(Backup.toJson(samplePayload))
        // 把第一笔账单的金额删掉。以前这里被 runCatching 吞掉，结果是「恢复成功」
        // 但少了一笔账 —— 用户以为数据回来了，其实悄悄丢了一部分。
        root.getJSONArray("transactions").getJSONObject(0).remove("amountCents")

        try {
            Backup.fromJson(root.toString())
            throw AssertionError("损坏条目应该报错")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("数据无效"))
        }
    }

    @Test
    fun `负数余额与超大金额不丢失精度`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        assertEquals(-12345L, restored.accounts.first { it.id == 2L }.initialBalanceCents)
        assertEquals(999_999_99L, restored.transactions.first { it.id == 2L }.amountCents)
    }

    @Test
    fun `布尔标记保持原值`() {
        val restored = Backup.fromJson(Backup.toJson(samplePayload))
        assertTrue(restored.accounts.first { it.id == 2L }.archived)
        assertTrue(restored.budgets.first { it.id == 1L }.enabled)
        assertFalse(restored.budgets.first { it.id == 2L }.enabled)
        assertTrue(restored.transactions.first { it.id == 2L }.excludeFromStats)
        assertFalse(restored.transactions.first { it.id == 1L }.excludeFromStats)
    }

    @Test
    fun `空备份也能安全往返`() {
        val empty = BackupPayload(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val restored = Backup.fromJson(Backup.toJson(empty))
        assertTrue(restored.accounts.isEmpty())
        assertTrue(restored.categories.isEmpty())
        assertTrue(restored.transactions.isEmpty())
        assertTrue(restored.budgets.isEmpty())
        assertTrue(restored.recurring.isEmpty())
    }

    @Test
    fun `CSV 带 UTF-8 BOM 和表头`() {
        val csv = Backup.toCsv(listOf(sampleRow()))
        assertTrue("缺少 BOM，Excel 打开会中文乱码", csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("日期,时间,类型,分类,账户,金额,备注"))
    }

    @Test
    fun `CSV 金额按元输出而不是分`() {
        val csv = Backup.toCsv(listOf(sampleRow()))
        assertTrue("应该出现 23.50（元），而不是 2350（分）", csv.contains(",23.50,"))
        assertFalse(csv.contains(",2350,"))
    }

    @Test
    fun `CSV 会对含逗号引号的字段做转义`() {
        val csv = Backup.toCsv(listOf(sampleRow(note = "午饭,带\"引号\"")))
        assertTrue("含逗号的字段必须用双引号包裹", csv.contains("\"午饭,带\"\"引号\"\"\""))
    }

    @Test
    fun `CSV 的收支类型输出中文`() {
        val csv = Backup.toCsv(listOf(sampleRow()))
        assertTrue(csv.contains("支出"))
    }

    @Test
    fun `借还记录能正确往返`() {
        val payload = samplePayload.copy(
            loans = listOf(
                LoanEntity(
                    id = 1L,
                    direction = LoanDirection.LENT,
                    person = "张三",
                    amountCents = 100_000L,
                    settledCents = 30_000L,
                    accountId = 1L,
                    occurredAt = 1_700_000_000_000L,
                    dueAt = 1_700_000_500_000L,
                    note = "急用"
                )
            )
        )

        val restored = Backup.fromJson(Backup.toJson(payload)).loans.single()

        assertEquals("张三", restored.person)
        assertEquals(LoanDirection.LENT, restored.direction)
        assertEquals(100_000L, restored.amountCents)
        assertEquals(30_000L, restored.settledCents)
        assertEquals(1_700_000_500_000L, restored.dueAt ?: 0L)
        // 派生状态也要跟着对得上，才算真的复原了
        assertEquals(70_000L, restored.openCents)
        assertFalse(restored.isSettled)
    }

    @Test
    fun `老备份没有借还这一段也能导入`() {
        val root = JSONObject(Backup.toJson(samplePayload))
        root.remove("loans")

        assertTrue(Backup.fromJson(root.toString()).loans.isEmpty())
    }

    @Test
    fun `账单里指向借还记录的引用能往返`() {
        val payload = samplePayload.copy(
            transactions = samplePayload.transactions.map { it.copy(loanId = 7L) }
        )

        val restored = Backup.fromJson(Backup.toJson(payload))

        // 恢复后这些流水还得认得自己的借还记录，否则删记录时连带撤不掉
        assertTrue(restored.transactions.all { it.loanId == 7L })
    }

    private fun sampleRow(
        note: String = "普通备注"
    ) = TransactionRow(
        id = 1L,
        amountCents = 2350L,
        kind = TxKind.EXPENSE,
        accountId = 1L,
        // 转账专用字段，这里测的是普通支出所以都留空
        toAccountId = null,
        feeCents = 0L,
        categoryId = 1L,
        note = note,
        occurredAt = Dates.toMillis(LocalDate.of(2026, 9, 26), java.time.LocalTime.of(12, 30)),
        excludeFromStats = false,
        loanId = null,
        categoryName = "餐饮",
        categoryIcon = "food",
        categoryColor = "#FFEF6C00",
        accountName = "现金",
        toAccountName = ""
    )
}
