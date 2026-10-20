package com.androidharness.app.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuth {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String = "请验证指纹、面容或 PIN",
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null,
    ) {
        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(AUTHENTICATORS)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                Toast.makeText(
                    activity,
                    "请先在设备设置中设定屏幕锁定（PIN 或指纹）",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    "此设备不支持生物识别验证",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            onError?.invoke("生物识别不可用")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError?.invoke(errString.toString())
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "验证失败")
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
