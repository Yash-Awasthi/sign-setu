package app.signsetu.data.session

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate

/** This install's signing key, made inside the Android Keystore (StrongBox where the phone has one) and never
 * exported: a session's root signed by it shows the record came from this device as it was. The attestation chain
 * lets a lab check the key is hardware-backed. */
object DeviceSeal {
    private const val ALIAS = "signsetu_seal_v1"
    private val CHALLENGE = "signsetu-seal-v1".toByteArray()

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(): PrivateKey {
        (store().getKey(ALIAS, null) as? PrivateKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(CHALLENGE)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        try {
            gen.initialize(spec(strongBox = true)); gen.generateKeyPair()
        } catch (_: StrongBoxUnavailableException) {
            gen.initialize(spec(strongBox = false)); gen.generateKeyPair()
        } catch (_: java.security.ProviderException) {
            gen.initialize(spec(strongBox = false)); gen.generateKeyPair()
        }
        return store().getKey(ALIAS, null) as PrivateKey
    }

    /** DER ECDSA over [line], base64 without padding or wraps; null when the Keystore cannot sign. */
    fun sign(line: String): String? = runCatching {
        Signature.getInstance("SHA256withECDSA").run { initSign(key()); update(line.toByteArray()); sign() }
            .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING) }
    }.getOrNull()

    fun verify(line: String, sig: String, publicKey: ByteArray? = null): Boolean = runCatching {
        val pub = publicKey?.let { java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(it)) }
            ?: chain().first().publicKey
        Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(line.toByteArray()); verify(Base64.decode(sig, Base64.NO_WRAP or Base64.NO_PADDING)) }
    }.getOrDefault(false)

    /** The key's attestation chain, device key first. */
    fun chain(): List<Certificate> = runCatching { key(); store().getCertificateChain(ALIAS).toList() }.getOrDefault(emptyList())

    /** The public key (X.509 SubjectPublicKeyInfo) and its SHA-256, for printing beside the signature. */
    fun publicKey(): ByteArray? = chain().firstOrNull()?.publicKey?.encoded
    fun fingerprint(): String? = publicKey()?.let { MessageDigest.getInstance("SHA-256").digest(it).toHexString() }
}
