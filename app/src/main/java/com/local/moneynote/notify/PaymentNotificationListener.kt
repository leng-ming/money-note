package com.local.moneynote.notify

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.local.moneynote.core.Money

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

        val parsed = PaymentParser.parse(pkg, title, body)

        // 先留档再判断 —— 「收到了但没认出来」同样要留下痕迹，
        // 否则排查时根本分不清是没收到通知、还是规则没匹配上
        Diagnostics.record(
            this,
            Diagnostics.Entry(
                at = System.currentTimeMillis(),
                pkg = pkg,
                title = title,
                body = body,
                result = if (parsed == null) {
                    "未匹配"
                } else {
                    val action = if (parsed.isIncome) "收入" else "支出"
                    "已识别：$action ¥" + Money.format(parsed.amountCents)
                }
            )
        )

        if (parsed == null) return
        if (isDuplicate(parsed)) return

        PendingPayment.save(this, parsed)
        PendingPayment.post(this, parsed)
    }

    /**
     * 服务真正被系统绑上时走这里。
     *
     * 记一笔很有价值：如果诊断里只有「已连接」、却从来没有支付通知，
     * 说明绑定是好的、问题在通知本身；如果连「已连接」都没有，
     * 那就是系统压根没绑上（Android 在 App 升级后经常这样）。
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Diagnostics.record(
            this,
            Diagnostics.Entry(
                at = System.currentTimeMillis(),
                pkg = "system",
                title = "监听服务已连接",
                body = "",
                result = "服务就绪"
            )
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Diagnostics.record(
            this,
            Diagnostics.Entry(
                at = System.currentTimeMillis(),
                pkg = "system",
                title = "监听服务被系统断开",
                body = "",
                result = "需要重新绑定"
            )
        )
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

        /**
         * 主动请求系统重新绑定监听服务。
         *
         * Android 会在 App 升级、系统省电等情况下把它解绑，解绑之后
         * onNotificationPosted 再也不会被调用 —— 但设置里的「通知使用权」
         * 依然显示已授权。从用户视角看就是「权限明明给了却毫无反应」。
         * requestRebind 能把绑定要回来，这是这个问题的正解。
         */
        fun requestRebind(context: Context) {
            if (!isGranted(context)) return
            val me = ComponentName(context, PaymentNotificationListener::class.java)
            runCatching { requestRebind(me) }
        }

        /**
         * App 有没有「发通知」的权限。
         *
         * Android 13 起这是运行时权限，必须在代码里主动申请 —— 少了它，
         * 监听服务照样能识别到支付，但**提醒根本发不出去**，
         * 用户看到的现象就是「自动记账一次都没触发」（真实踩过的坑）。
         */
        fun canPostNotifications(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
