package com.local.moneynote.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 盯着微信 / 支付宝的支付通知。
 *
 * 为什么选「监听通知」而不是「读屏幕」：
 * - 不需要截屏权限，也没有任何常驻的系统提示
 * - 不用用户每次按键，付完款自动就有了
 * - 微信 / 支付宝出于安全考虑会屏蔽无障碍读取，但**通知是公开的**
 *
 * 代价是通知里信息有限：通常只有金额，没有商户名。所以这里只做到
 * 「把金额填好、弹个提醒」，分类和账户留给用户确认。
 */
class PaymentNotificationListener : NotificationListenerService() {

    // 同一条通知系统可能回调多次，用「同金额 + 5 秒内」做一次粗去重
    private var lastAmountCents = 0L
    private var lastAt = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg !in PaymentParser.WATCHED_PACKAGES) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        // 长通知的正文常在 bigText 里，短通知在 text 里，标题补充在 subText
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val body = listOf(text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val parsed = PaymentParser.parse(pkg, title, body) ?: return
        if (isDuplicate(parsed)) return

        PendingPayment.save(this, parsed)
        PendingPayment.post(this, parsed)
    }

    private fun isDuplicate(parsed: PaymentParser.Parsed): Boolean {
        val now = System.currentTimeMillis()
        val duplicate = parsed.amountCents == lastAmountCents && now - lastAt < 5_000
        lastAmountCents = parsed.amountCents
        lastAt = now
        return duplicate
    }

    companion object {
        /** 用户是否已授予「通知使用权」。没授予时服务根本不会被启动 */
        fun isGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            val me = ComponentName(context, PaymentNotificationListener::class.java)
            // 这个字段是「包名/类名:包名/类名」的冒号分隔串
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
