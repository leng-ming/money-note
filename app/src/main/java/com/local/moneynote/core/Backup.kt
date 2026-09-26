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
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份/恢复引擎。
 *
 * 备份文件是纯文本 JSON，落在你自己选的位置（U 盘、网盘、微信传给自己都行）——
 * 数据主权完全在你手里，任何服务器都拿不到。
 */
object Backup {

    private const val VERSION = 1

    /* ---------------- 导出 ---------------- */

    fun toJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("accounts", JSONArray().also { arr -> payload.accounts.forEach { arr.put(accountToJson(it)) } })
        root.put("categories", JSONArray().also { arr -> payload.categories.forEach { arr.put(categoryToJson(it)) } })
        root.put("transactions", JSONArray().also { arr -> payload.transactions.forEach { arr.put(txToJson(it)) } })
        root.put("budgets", JSONArray().also { arr -> payload.budgets.forEach { arr.put(budgetToJson(it)) } })
        root.put("recurring", JSONArray().also { arr -> payload.recurring.forEach { arr.put(recurringToJson(it)) } })
        return root.toString(2)
    }

    private fun accountToJson(a: AccountEntity) = JSONObject().apply {
        put("id", a.id)
        put("name", a.name)
        put("type", a.type.name)
        put("initialBalanceCents", a.initialBalanceCents)
        put("colorHex", a.colorHex)
        put("sortOrder", a.sortOrder)
        put("archived", a.archived)
        put("createdAt", a.createdAt)
    }

    private fun categoryToJson(c: CategoryEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("kind", c.kind.name)
        put("iconKey", c.iconKey)
        put("colorHex", c.colorHex)
        put("sortOrder", c.sortOrder)
        put("archived", c.archived)
        put("isSystem", c.isSystem)
    }

    private fun txToJson(t: TransactionEntity) = JSONObject().apply {
        put("id", t.id)
        put("amountCents", t.amountCents)
        put("kind", t.kind.name)
        put("accountId", t.accountId)
        put("categoryId", t.categoryId)
        put("note", t.note)
        put("occurredAt", t.occurredAt)
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
        put("excludeFromStats", t.excludeFromStats)
    }

    private fun budgetToJson(b: BudgetEntity) = JSONObject().apply {
        put("id", b.id)
        put("categoryId", b.categoryId ?: JSONObject.NULL)
        put("amountCents", b.amountCents)
        put("period", b.period.name)
        put("enabled", b.enabled)
        put("createdAt", b.createdAt)
    }

    private fun recurringToJson(r: RecurringEntity) = JSONObject().apply {
        put("id", r.id)
        put("name", r.name)
        put("amountCents", r.amountCents)
        put("kind", r.kind.name)
        put("accountId", r.accountId)
        put("categoryId", r.categoryId)
        put("note", r.note)
        put("freq", r.freq.name)
        put("intervalCount", r.intervalCount)
        put("nextOccurAt", r.nextOccurAt)
        put("lastGeneratedAt", r.lastGeneratedAt ?: JSONObject.NULL)
        put("enabled", r.enabled)
    }

    /* ---------------- 导入 ---------------- */

    /** 解析失败会抛异常，调用方负责捕获并提示用户 */
    fun fromJson(text: String): BackupPayload {
        val root = JSONObject(text)
        return BackupPayload(
            accounts = root.optJSONArray("accounts").mapObjects { o ->
                AccountEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = runCatching { AccountType.valueOf(o.getString("type")) }.getOrDefault(AccountType.OTHER),
                    initialBalanceCents = o.optLong("initialBalanceCents", 0L),
                    colorHex = o.optString("colorHex", "#FF607D8B"),
                    sortOrder = o.optInt("sortOrder", 0),
                    archived = o.optBoolean("archived", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            },
            categories = root.optJSONArray("categories").mapObjects { o ->
                CategoryEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    kind = runCatching { TxKind.valueOf(o.getString("kind")) }.getOrDefault(TxKind.EXPENSE),
                    iconKey = o.optString("iconKey", "more"),
                    colorHex = o.optString("colorHex", "#FF607D8B"),
                    sortOrder = o.optInt("sortOrder", 0),
                    archived = o.optBoolean("archived", false),
                    isSystem = o.optBoolean("isSystem", false)
                )
            },
            transactions = root.optJSONArray("transactions").mapObjects { o ->
                TransactionEntity(
                    id = o.getLong("id"),
                    amountCents = o.getLong("amountCents"),
                    kind = runCatching { TxKind.valueOf(o.getString("kind")) }.getOrDefault(TxKind.EXPENSE),
                    accountId = o.getLong("accountId"),
                    categoryId = o.getLong("categoryId"),
                    note = o.optString("note", ""),
                    occurredAt = o.optLong("occurredAt", System.currentTimeMillis()),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    excludeFromStats = o.optBoolean("excludeFromStats", false)
                )
            },
            budgets = root.optJSONArray("budgets").mapObjects { o ->
                BudgetEntity(
                    id = o.getLong("id"),
                    categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId"),
                    amountCents = o.getLong("amountCents"),
                    period = runCatching { BudgetPeriod.valueOf(o.getString("period")) }.getOrDefault(BudgetPeriod.MONTHLY),
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            },
            recurring = root.optJSONArray("recurring").mapObjects { o ->
                RecurringEntity(
                    id = o.getLong("id"),
                    name = o.optString("name", "周期账单"),
                    amountCents = o.getLong("amountCents"),
                    kind = runCatching { TxKind.valueOf(o.getString("kind")) }.getOrDefault(TxKind.EXPENSE),
                    accountId = o.getLong("accountId"),
                    categoryId = o.getLong("categoryId"),
                    note = o.optString("note", ""),
                    freq = runCatching { RecurFreq.valueOf(o.getString("freq")) }.getOrDefault(RecurFreq.MONTHLY),
                    intervalCount = o.optInt("intervalCount", 1),
                    nextOccurAt = o.optLong("nextOccurAt", System.currentTimeMillis()),
                    lastGeneratedAt = if (o.isNull("lastGeneratedAt")) null else o.getLong("lastGeneratedAt"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        )
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            runCatching { transform(o) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    /* ---------------- CSV ---------------- */

    /** 导出成 Excel 能直接打开的 CSV（带 UTF-8 BOM，避免中文乱码） */
    fun toCsv(rows: List<TransactionRow>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("日期,时间,类型,分类,账户,金额,备注\r\n")
        rows.forEach { r ->
            val date = Dates.toLocalDate(r.occurredAt).toString()
            val time = Dates.labelTime(r.occurredAt)
            val type = if (r.kind == TxKind.EXPENSE) "支出" else "收入"
            val amount = Money.formatPlain(r.amountCents)
            sb.append(csvEscape(date)).append(',')
                .append(csvEscape(time)).append(',')
                .append(csvEscape(type)).append(',')
                .append(csvEscape(r.categoryName)).append(',')
                .append(csvEscape(r.accountName)).append(',')
                .append(csvEscape(amount)).append(',')
                .append(csvEscape(r.note))
                .append("\r\n")
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
