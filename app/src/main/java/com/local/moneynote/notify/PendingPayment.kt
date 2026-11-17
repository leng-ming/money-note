package com.local.moneynote.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.moneynote.MainActivity
import com.local.moneynote.R
import com.local.moneynote.core.Money
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「刚识别到一笔支付」的暂存队列 + 提醒。
 *
 * 为什么不直接静默落库：通知里的信息太薄（往往只有金额，没有商户、没有分类、
 * 甚至分不清是付款还是收款），直接写进去只会造出一堆要事后返工的错账。
 * 所以这里只发一条提醒：点一下跳到记账页、金额已填好，
 * 剩下的分类和账户交给用户一秒确认。
 *
 * 暂存用 SharedPreferences 而不是内存变量：通知监听服务和 MainActivity
 * 不在同一个生命周期里，系统随时可能回收其中一个，内存变量会丢。
 *
 * **为什么是队列而不是单个字段**：以前用一组固定的 prefs key 存一笔，
 * 短时间连着收到两笔付款（比如超市分两次付、或者一边网购一边点外卖），
 * 后一笔会把前一笔直接覆盖掉 —— 第一笔就这样无声无息地没了。
 */
object PendingPayment {

    private const val PREF = "moneynote_pending"
    private const val KEY_QUEUE = "queue"

    /** 队列上限，防止一路堆下去 */
    private const val MAX_ITEMS = 10

    /** 超过这个时间的暂存就不理了（比如用户一小时后才点开通知） */
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    /** 从通知点进来时带的标记，MainActivity 靠它决定要不要跳记账页 */
    const val EXTRA_FROM_NOTIFICATION = "from_payment_notification"

    private const val CHANNEL_ID = "auto_bookkeeping"
    private const val CHANNEL_NAME = "自动记账提醒"
    private const val NOTIFICATION_ID = 9001

    private data class Item(
        val amountCents: Long,
        val source: String,
        val isIncome: Boolean,
        val at: Long
    )

    /* ---------------- 队列读写 ---------------- */

    /** 入队 */
    fun save(context: Context, parsed: PaymentParser.Parsed) {
        val now = System.currentTimeMillis()
        val list = items(context).toMutableList()
        list.add(Item(parsed.amountCents, parsed.source, parsed.isIncome, now))

        // 过期的扔掉，再只留最近 MAX_ITEMS 条
        val cutoff = now - MAX_AGE_MS
        write(context, list.filter { it.at >= cutoff }.takeLast(MAX_ITEMS))
    }

    /** 读最早的一条（**不出队**：用户中途退出去，这条还在） */
    fun read(context: Context): PaymentParser.Parsed? = items(context).firstOrNull()?.let {
        PaymentParser.Parsed(
            amountCents = it.amountCents,
            source = it.source,
            isIncome = it.isIncome
        )
    }

    /** 队列里还有几条待确认 */
    fun count(context: Context): Int = items(context).size

    /**
     * 用户真的把那一条记下来之后调用。
     *
     * 只出队一条 —— 剩下的继续留在队列里，接着点通知就能逐条处理。
     * 这跟「读完就清空」是两回事：中途放弃不该把后面的账也丢掉。
     */
    fun consume(context: Context) {
        val list = items(context)
        if (list.isEmpty()) return
        val rest = list.drop(1)
        write(context, rest)
        refreshNotification(context, rest)
    }

    fun clear(context: Context) {
        write(context, emptyList())
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    /* ---------------- 通知 ---------------- */

    /** 发一条「记一笔？」的提醒，点它跳记账页 */
    fun post(context: Context, parsed: PaymentParser.Parsed) {
        ensureChannel(context)

        val queued = count(context)
        val action = if (parsed.isIncome) "收款" else "支出"
        val title = buildString {
            append(parsed.source).append(action)
            append(" ¥").append(Money.format(parsed.amountCents))
            if (queued > 1) append(" 等 ").append(queued).append(" 笔")
        }
        val text = if (queued > 1) {
            "点一下从第一笔记起，记完会自动跳到下一笔"
        } else {
            "点一下记下来，分类和账户确认一下就好"
        }

        notify(context, title, text)
    }

    /** 消费掉一条之后刷新通知文案；队列空了就把它收掉 */
    private fun refreshNotification(context: Context, rest: List<Item>) {
        if (rest.isEmpty()) {
            runCatching {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
            return
        }
        ensureChannel(context)
        val head = rest.first()
        val action = if (head.isIncome) "收款" else "支出"
        val title = buildString {
            append(head.source).append(action)
            append(" ¥").append(Money.format(head.amountCents))
            if (rest.size > 1) append(" 等 ").append(rest.size).append(" 笔")
        }
        notify(context, title, "还剩 ${rest.size} 笔待确认")
    }

    private fun notify(context: Context, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /* ---------------- 存储 ---------------- */

    /** 读出未过期的条目；顺带把过期的清掉 */
    private fun items(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null) ?: return emptyList()
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS

        val parsed = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val amount = o.optLong("amount", 0L)
                if (amount <= 0L) return@mapNotNull null
                Item(
                    amountCents = amount,
                    source = o.optString("source", ""),
                    isIncome = o.optBoolean("income", false),
                    at = o.optLong("at", 0L)
                )
            }
        }.getOrDefault(emptyList())

        val fresh = parsed.filter { it.at >= cutoff }
        if (fresh.size != parsed.size) write(context, fresh)
        return fresh
    }

    private fun write(context: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("amount", item.amountCents)
                    put("source", item.source)
                    put("income", item.isIncome)
                    put("at", item.at)
                }
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUEUE, arr.toString())
            .apply()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "识别到微信 / 支付宝付款后提醒你补一笔"
            }
        )
    }

    /**
     * 发一条测试提醒，用来验证「通知链路」通不通。
     *
     * 自动记账涉及两个独立授权（通知使用权 + 发通知权限），
     * 任何一个没给都会表现成「什么都没发生」，用户很难自己判断卡在哪一步。
     * 有了这个按钮，点一下就知道链路到底通没通。
     */
    fun postTest(context: Context) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("通知链路正常 ✓")
            .setContentText("能看到这条，说明自动记账的提醒可以正常发出来")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
        }
    }
}
