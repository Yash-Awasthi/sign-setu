package app.signsetu.data.session

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Schema history is in app/schemas; every version from 1 migrates forward without losing a row. */
@Database(entities = [SessionEntity::class, EventEntity::class, DrawingEntity::class, AudioEntity::class, LandmarksEntity::class], version = 9, exportSchema = true)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun dao(): SessionDao

    companion object {
        const val FILE_NAME = "session.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN role TEXT NOT NULL DEFAULT 'VICTIM'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN itemId TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN reason TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN shownId INTEGER")
                db.execSQL("ALTER TABLE event ADD COLUMN via TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN title TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN hash TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN rootHash TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN rootSig TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN audioId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `wav` BLOB NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_sessionId` ON `audio` (`sessionId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN landmarksId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `landmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `json` BLOB NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_landmarks_sessionId` ON `landmarks` (`sessionId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in listOf("savedThrough INTEGER", "stationFile TEXT", "stationHead TEXT", "phoneFolder TEXT", "phoneHead TEXT")) {
                    db.execSQL("ALTER TABLE session ADD COLUMN $column")
                }
            }
        }

        fun open(context: Context, key: ByteArray, onDestructiveMigration: () -> Unit = {}, fileName: String = FILE_NAME): SessionDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, SessionDatabase::class.java, fileName)
                .openHelperFactory(SupportOpenHelperFactory(key))
                // Purged rows are overwritten in place, and no write-ahead log keeps old pages around.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                // A downgrade drops sessions rather than failing every unlock; the officer is told (onDestructiveMigration).
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        onDestructiveMigration()
                    }
                })
                .build()
        }
    }
}
