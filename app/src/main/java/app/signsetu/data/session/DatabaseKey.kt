package app.signsetu.data.session

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database key (DEK) is random, and stored only wrapped by a non-exportable Keystore key (KEK).
 * The KEK is usable for a short window after the officer authenticates (SEC-01, SEC-22).
 */
class DatabaseKey(private val context: Context) {
    private val wrapped = File(context.noBackupFilesDir, "dek.bin")

    /** Call only after a successful device-credential or biometric prompt. */
    fun unwrap(): ByteArray {
        val kek = kek()
        if (!wrapped.exists()) {
            val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, kek) }
            wrapped.writeBytes(cipher.iv + cipher.doFinal(dek))
            return dek
        }
        val bytes = wrapped.readBytes()
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, bytes, 0, IV_LEN))
        }
        return cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN)
    }

    /** Destroys both keys: every stored session becomes unreadable (Clear all data). */
    fun destroy() {
        wrapped.delete()
        keystore().deleteEntry(ALIAS)
        context.deleteDatabase("session.db")
    }

    private fun kek(): SecretKey {
        (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_WINDOW_SECONDS,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        const val ALIAS = "signsetu.kek"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val AUTH_WINDOW_SECONDS = 30
    }
}
