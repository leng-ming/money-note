package com.local.moneynote.core

import com.local.moneynote.data.AccountEntity
import com.local.moneynote.data.AccountType
import com.local.moneynote.data.BackupPayload
import com.local.moneynote.data.BudgetEntity
import com.local.moneynote.data.BudgetPeriod
import com.local.moneynote.data.CategoryEntity
import com.local.moneynote.data.RecurFreq
import com.local.moneynote.data.RecurringEntity
import com.local.moneynote.data.TransactionEntity
import com.local.moneynote.data.TransactionRow
import com.local.moneynote.data.TxKind
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
        categoryName = "餐饮",
        categoryIcon = "food",
        categoryColor = "#FFEF6C00",
        accountName = "现金",
        toAccountName = ""
    )
}
