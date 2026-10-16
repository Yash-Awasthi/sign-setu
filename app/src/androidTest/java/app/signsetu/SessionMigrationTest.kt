package app.signsetu

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionMigrationTest {

    @Test
    fun version1SessionBecomesAVictimSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // Tables as written by version 1 (app/schemas/.../1.json); Room then validates the migrated schema against version 2.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `session` (`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `profile` TEXT NOT NULL, `subjectLang` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `type` TEXT NOT NULL, `source` TEXT NOT NULL, `lang` TEXT, `text` TEXT, `cardId` TEXT, `lexiconId` TEXT, `drawingId` INTEGER, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_sessionId` ON `event` (`sessionId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `drawing` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `png` BLOB NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drawing_sessionId` ON `drawing` (`sessionId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2828971f9fd646e7445b12038249e087')")
            db.execSQL("INSERT INTO session VALUES ('s', 1, NULL, 'YOUNG_CHILD', 'hi')")
            db.version = 1
        }
        val room = Room.databaseBuilder(context, SessionDatabase::class.java, name).addMigrations(SessionDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(Role.VICTIM.name, room.dao().openSession()?.role)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
