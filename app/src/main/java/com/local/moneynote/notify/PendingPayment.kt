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

/**
 * 「刚识别到一笔支付」的暂存 + 提醒。
 *
 * 为什么不直接静默落库：通知里的信息太薄（往往只有金额，没有商户、没有分类、
 * 甚至分不清是付款还是收款），直接写进去只会造出一堆要事后返工的错账。
 * 所以这里只发一条提醒：点一下跳到记账页、金额已填好，
 * 剩下的分类和账户交给用户一秒确认。
 *
 * 暂存用 SharedPreferences 而不是内存变量：通知监听服务和 MainActivity
 * 不在同一个生命周期里，系统随时可能回收其中一个，内存变量会丢。
 */
object PendingPayment {

    private const val PREF = "moneynote_pending"
    private const val KEY_AMOUNT = "amount_cents"
    private const val KEY_SOURCE = "source"
    private const val KEY_INCOME = "is_income"
    private const val KEY_AT = "at"

    /** 从通知点进来时带的标记，MainActivity 靠它决定要不要跳记账页 */
    const val EXTRA_FROM_NOTIFICATION = "from_payment_notification"

    private const val CHANNEL_ID = "auto_bookkeeping"
    private const val CHANNEL_NAME = "自动记账提醒"
    private const val NOTIFICATION_ID = 9001

    /** 超过这个时间的暂存就不理了（比如用户一小时后才点开通知） */
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun save(context: Context, parsed: PaymentParser.Parsed) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AMOUNT, parsed.amountCents)
            .putString(KEY_SOURCE, parsed.source)
            .putBoolean(KEY_INCOME, parsed.isIncome)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    /** 读出暂存；太旧的当作过期，返回 null 并顺手清掉 */
    fun read(context: Context): PaymentParser.Parsed? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val amount = sp.getLong(KEY_AMOUNT, 0L)
        if (amount <= 0L) return null

        if (System.currentTimeMillis() - sp.getLong(KEY_AT, 0L) > MAX_AGE_MS) {
            clear(context)
            return null
        }
        return PaymentParser.Parsed(
            amountCents = amount,
            source = sp.getString(KEY_SOURCE, "") ?: "",
            isIncome = sp.getBoolean(KEY_INCOME, false)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    /** 发一条「记一笔？」的提醒，点它跳记账页 */
    fun post(context: Context, parsed: PaymentParser.Parsed) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, flags)

        val action = if (parsed.isIncome) "收款" else "支出"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${parsed.source}$action ¥${Money.format(parsed.amountCents)}")
            .setContentText("点一下记下来，分类和账户确认一下就好")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
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
