package app.signsetu.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenVault {
    fun get(): String?
    /** False when the token could not be written; the caller must not claim enrolment succeeded. */
    fun put(token: String): Boolean
    fun clear()
    /** True when something is stored, readable or not; tells "never written" from "not readable yet". */
    fun stored(): Boolean = get() != null
}

/** The token is stored only wrapped by a non-exportable Keystore key, in a file excluded from backup. */
class KeystoreVault(context: Context, name: String = "gw.bin", private val alias: String = ALIAS) : TokenVault {
    private val file = File(context.noBackupFilesDir, name)

    override fun get(): String? = synchronized(LOCK) {
        runCatching {
            val b = file.takeIf { it.exists() }?.readBytes() ?: return null
            if (b.size <= IV_LEN) throw javax.crypto.AEADBadTagException("truncated")
            Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV_LEN)) }
                .doFinal(b, IV_LEN, b.size - IV_LEN).decodeToString()
        }.getOrElse { e ->
            // A rotated or invalidated Keystore key, or a truncated file, can never be read again: dropped so the next
            // enrolment starts clean. A Keystore that is only busy (just after boot) keeps the file for the next try.
            if (!transient(e)) runCatching { file.delete() }
            null
        }
    }

    override fun put(token: String): Boolean = synchronized(LOCK) { runCatching {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        // Written beside and renamed over, so a crash mid-write cannot leave a half token that never decrypts.
        val tmp = File(file.path + ".new").apply { writeBytes(cipher.iv + cipher.doFinal(token.toByteArray())) }
        if (!tmp.renameTo(file)) throw java.io.IOException("could not store the gateway token")
        true
    }.getOrElse { false } }

    override fun stored(): Boolean = file.exists()

    override fun clear() = synchronized(LOCK) {
        file.delete()
        runCatching { keystore().deleteEntry(alias) }
        Unit
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keystore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    private fun transient(e: Throwable): Boolean =
        e is java.io.IOException || e is java.security.ProviderException || e is java.security.KeyStoreException

    private companion object {
        /** One lock for every vault: two vaults share a file and alias (the gateway token), and a key made twice at
         * once would leave one write sealed with a key that no longer exists. */
        val LOCK = Any()
        const val ALIAS = "signsetu.gateway.token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
