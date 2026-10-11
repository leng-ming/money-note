package com.local.moneynote.notify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自动记账的「行车记录仪」。
 *
 * 这条链路横跨三个边界（微信/支付宝 → 系统通知 → 我们的服务），
 * 任何一环断掉，用户看到的都是同一句话：「什么都没发生」。
 * 光看结果是分不清「通知没收到」和「收到了但没认出来」的，
 * 所以这里把每条相关通知的原始文案和解析结果都留档，
 * 排查时能一眼看出卡在哪一步。
 */
object Diagnostics {

    private const val PREF = "moneynote_diag"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 30

    data class Entry(
        val at: Long,
        val pkg: String,
        val title: String,
        val body: String,
        /** 人类可读的结论，例如「已识别：支出 ¥12.00」或「未匹配」 */
        val result: String
    )

    fun record(context: Context, entry: Entry) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(sp.getString(KEY, "[]")) }
            .getOrElse { JSONArray() }

        arr.put(
            JSONObject().apply {
                put("at", entry.at)
                put("pkg", entry.pkg)
                put("title", entry.title)
                put("body", entry.body)
                put("result", entry.result)
            }
        )

        // 只保留最近 N 条，免得无限膨胀
        val trimmed = JSONArray()
        val start = (arr.length() - MAX_ENTRIES).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))

        sp.edit().putString(KEY, trimmed.toString()).apply()
    }

    /** 最新的在最前面 */
    fun read(context: Context): List<Entry> {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(sp.getString(KEY, "[]")) }
            .getOrElse { JSONArray() }

        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                at = o.optLong("at"),
                pkg = o.optString("pkg"),
                title = o.optString("title"),
                body = o.optString("body"),
                result = o.optString("result")
            )
        }.reversed()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 包名 → 好读的名字 */
    fun sourceName(pkg: String): String = when (pkg) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        "system" -> "系统"
        else -> pkg
    }
}
