package app.signsetu

import android.app.KeyguardManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.DatabaseKey
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/** The Keystore behaviour the records depend on: a key the phone can only use while it is unlocked, a database that
 * no other key opens, and a phone with no screen lock told so rather than crashed on. */
@RunWith(AndroidJUnit4::class)
class LockedKeystoreDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private val alias = "signsetu.test.needs.auth"
    private val dbName = "locked-keystore-test.db"

    @After
    fun clean() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
        app.deleteDatabase(dbName)
    }

    @Test
    fun aKeystoreKeyThatNeedsAuthenticationRefusesToWorkWithoutIt() {
        Assume.assumeTrue(
            "this phone has no screen lock; the locked-device case needs a phone with one",
            app.getSystemService(KeyguardManager::class.java).isDeviceSecure,
        )
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        runCatching { keystore.deleteEntry(alias) }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .build(),
            )
        }.generateKey()
        val key = (keystore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        // No fingerprint or screen lock was shown, so the phone refuses the key exactly as it would while locked.
        assertThrows(UserNotAuthenticatedException::class.java) {
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key)
        }
    }

    @Test
    fun withoutAScreenLockAndroidRefusesToMakeAKeyThatNeedsAuthentication() {
        Assume.assumeFalse(
            "this phone has a screen lock, so such a key is made and refused instead",
            app.getSystemService(KeyguardManager::class.java).isDeviceSecure,
        )
        assertThrows(InvalidAlgorithmParameterException::class.java) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setUserAuthenticationRequired(true)
                        .build(),
                )
            }.generateKey()
        }
    }

    @Test
    fun aDatabaseWrittenUnderOneKeyIsRefusedByAnother() = runBlocking<Unit> {
        val first = ByteArray(32) { (it + 1).toByte() }
        val other = ByteArray(32) { (it + 100).toByte() }
        val db = SessionDatabase.open(app, first, fileName = dbName)
        val repo = SessionRepository(db.dao())
        val id = repo.start(Profile.NOT_SURE, "en")
        repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = "yes")
        db.close()

        // The key the phone cannot produce while locked is, to the file, simply the wrong key: no plain read, no guess.
        assertThrows(SQLiteNotADatabaseException::class.java) {
            SessionDatabase.open(app, other, fileName = dbName).openHelper.writableDatabase
        }
    }

    @Test
    fun aPhoneWithoutAScreenLockIsToldItsRecordsAreNotTiedToUnlocking() {
        val locked = app.getSystemService(KeyguardManager::class.java).isDeviceSecure
        val key = DatabaseKey(app)
        val unwrapped = key.unwrap()
        assertEquals("the data key is 32 bytes", 32, unwrapped.key.size)
        unwrapped.close()
        assertTrue("32 zero bytes after close", unwrapped.key.all { it == 0.toByte() })
        // This emulator has no screen lock, so the records are held under the key that does not need one, and the
        // officer is told (key_unbound_title / key_unbound_body) rather than stopped by a failure.
        assertEquals("unbound follows the phone's screen lock", !locked, key.unbound)
        assertTrue("the message exists", app.getString(R.string.key_unbound_title).isNotBlank())
    }

    @Test
    fun theAppUnlocksAConvertedPhoneWithoutCrashing() = runBlocking<Unit> {
        app.container.unlock()
        // On a phone with no lock screen the officer gets the documented notice; the records themselves still open.
        if (!app.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            assertTrue("the officer is told the records are not tied to unlocking", app.container.keyUnbound.value)
            app.container.acknowledgeKeyUnbound()
            assertFalse(app.container.keyUnbound.value)
        }
        assertTrue("sessions opened", app.container.sessions.value != null)
        app.container.lock()
    }
}
