package com.local.moneynote.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 应用锁。
 *
 * 账目全在本机，手机被人拿到就等于全看见了 —— 系统锁屏之外再给 App 加一道。
 *
 * 用 androidx.biometric：API 28+ 走 BiometricPrompt（指纹/人脸/图案都行），
 * 更老的系统自动回退到 FingerprintManager，所以 minSdk 26 也覆盖得住。
 */
object AppLock {

    private const val PREF = "moneynote_app_lock"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * 这台设备现在能不能做生物识别验证。
     *
     * 用 BIOMETRIC_WEAK 而不是 STRONG：不强制"必须是指纹级别的强生物识别"，
     * 只要能验明是本人就行。国产机上人脸往往是弱等级，卡 STRONG 等于开了也用不了。
     */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * 该不该在启动时拦一道。
     *
     * 设备后来变得不支持生物识别（比如指纹全删了）时返回 false ——
     * 否则用户会被自己的 App 锁在门外，这比不锁更糟。
     */
    fun shouldLock(context: Context): Boolean =
        isEnabled(context) && canAuthenticate(context)

    /**
     * 弹指纹/人脸验证。
     *
     * 失败和取消都走 [onFail]，不区分 —— 反正都没通过。
     */
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁小鲸鱼记账")
            .setSubtitle("账目只保存在这台手机上")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("取消")
            .build()

        runCatching { BiometricPrompt(activity, executor, callback).authenticate(info) }
            .onFailure { onFail() }
    }
}
