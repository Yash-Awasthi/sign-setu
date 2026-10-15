package app.signsetu

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.signsetu.ui.AppRoot

/** Authentication and screen pinning, kept together because on Android 10 they interfere with each other. */
interface DeviceGuard {
    /** Unlocks the app; the device credential must be allowed because it authorises the Keystore key. */
    fun unlock(title: String, subtitle: String, onSuccess: () -> Unit, onFailure: () -> Unit)

    /** Confirms the officer before leaving the subject screen, keeping the app pinned wherever the platform allows. */
    fun confirmOfficer(title: String, subtitle: String, cancel: String, onSuccess: () -> Unit)

    fun pin()
    fun unpin()
}

class MainActivity : FragmentActivity(), DeviceGuard {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the recents thumbnail (SEC-40).
        // Debug builds skip it so UI tests can capture screens; only release builds are ever distributed.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val container = (application as SignSetuApp).container
        setContent { AppRoot(container, this) }
    }

    override fun unlock(title: String, subtitle: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        // BIOMETRIC_STRONG with DEVICE_CREDENTIAL is unsupported on API 29 (SEC-21).
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
        prompt(title, subtitle, authenticators, negative = null, onSuccess, onFailure)
    }

    override fun confirmOfficer(title: String, subtitle: String, cancel: String, onSuccess: () -> Unit) {
        when {
            // From Android 11 the credential screen is a system dialog that works while pinned.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                prompt(title, subtitle, BIOMETRIC_STRONG or DEVICE_CREDENTIAL, negative = null, onSuccess) {}
            // On Android 10 the credential screen belongs to Settings, which pinning blocks; a fingerprint does not need it.
            BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ->
                prompt(title, subtitle, BIOMETRIC_WEAK, negative = cancel, onSuccess) {}
            else -> {
                unpin()
                prompt(title, subtitle, BIOMETRIC_WEAK or DEVICE_CREDENTIAL, negative = null, onSuccess) { pin() }
            }
        }
    }

    override fun pin() {
        val am = getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) runCatching { startLockTask() }
    }

    override fun unpin() {
        runCatching { stopLockTask() }
    }

    private fun prompt(
        title: String,
        subtitle: String,
        authenticators: Int,
        negative: String?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .apply { negative?.let { setNegativeButtonText(it) } }
            .build()
        prompt.authenticate(info)
    }
}
