package app.signsetu

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/** A session's database file, lifted out of the app, is ciphertext: no plain SQLite header, none of the written
 * text, and no way in without the Keystore-held key. The same file still opens with the real key, so the refusals
 * are the cipher and not a damaged file. */
@RunWith(AndroidJUnit4::class)
class DatabaseAtRestDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private val testName = "at-rest-test.db"
    private val copyName = "at-rest-copy.db"
    private val extractedName = "at-rest-extracted.db"

    @After
    fun clean() {
        app.deleteDatabase(testName)
        app.deleteDatabase(copyName)
        File(app.cacheDir, extractedName).delete()
    }

    @Test
    fun aSessionFileIsUnreadableWithoutTheKeystoreKey() = runBlocking<Unit> {
        val dek = DatabaseKey(app).unwrap()
        try {
            app.deleteDatabase(testName)
            app.deleteDatabase(copyName)
            val extracted = File(app.cacheDir, extractedName)
            extracted.delete()

            // A real session, with words an officer would recognise, written under the app's own key.
            val db = SessionDatabase.open(app, dek.key, fileName = testName)
            val repo = SessionRepository(db.dao())
            val id = repo.start(Profile.NOT_SURE, "en")
            repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", cardId = CANARY)
            db.close()

            // Pull the file out twice: one copy for the byte checks, one where the keyless opens are tried.
            val source = app.getDatabasePath(testName)
            val copy = app.getDatabasePath(copyName)
            copy.parentFile?.mkdirs()
            source.inputStream().use { input -> extracted.outputStream().use { input.copyTo(it) } }
            source.inputStream().use { input -> copy.outputStream().use { input.copyTo(it) } }

            val bytes = extracted.readBytes()
            assertFalse(
                "the file starts as ciphertext, not an SQLite database",
                containsWithin(bytes, "SQLite format 3".toByteArray(Charsets.US_ASCII), 128),
            )
            assertFalse("UTF-8 bytes hold none of the session text", String(bytes, Charsets.UTF_8).contains(CANARY))
            assertFalse("ISO-8859-1 bytes hold none of the session text", String(bytes, Charsets.ISO_8859_1).contains(CANARY))

            // No key at all: Android's own SQLite opens the path lazily, so the first read is what refuses it.
            assertThrows(SQLiteException::class.java) {
                SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READONLY).use { plain ->
                    plain.rawQuery("select count(*) from session", null).use { it.count }
                }
            }
            // A key that is not the app's: SQLCipher refuses it, whether zeroes or a fresh random key.
            assertThrows(SQLiteNotADatabaseException::class.java) {
                SessionDatabase.open(app, ByteArray(32), fileName = copyName).openHelper.writableDatabase
            }
            val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
            assertThrows(SQLiteNotADatabaseException::class.java) {
                SessionDatabase.open(app, random, fileName = copyName).openHelper.writableDatabase
            }

            // The copy was never damaged: the real key still opens it and sees the one session.
            val reopened = SessionDatabase.open(app, dek.key, fileName = copyName)
            try {
                reopened.openHelper.readableDatabase.query("select count(*) from session").use { cursor ->
                    assertTrue("the count row is returned", cursor.moveToFirst())
                    assertEquals("the session is still there", 1, cursor.getInt(0))
                }
            } finally {
                reopened.close()
            }
        } finally {
            dek.close()
        }
    }

    private fun containsWithin(bytes: ByteArray, needle: ByteArray, limit: Int): Boolean =
        (0..(minOf(limit, bytes.size) - needle.size)).any { start ->
            needle.indices.all { bytes[start + it] == needle[it] }
        }

    private companion object {
        const val CANARY = "canary-plaintext"
    }
}
