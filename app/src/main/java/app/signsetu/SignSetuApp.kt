package app.signsetu

import android.app.Application
import android.os.Build
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.DatabaseKey
import app.signsetu.data.session.RetentionWorker
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.data.settings.SettingsStore
import app.signsetu.export.SessionReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class SignSetuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container)
        RetentionWorker.schedule(this)
    }
}

/** Manual wiring; the app is small enough not to need a DI framework (02-architecture §3). */
class AppContainer(private val app: Application) : DefaultLifecycleObserver {
    val content = ContentRepository(app.assets)
    val settings = SettingsStore(app)
    val report = SessionReport(app)

    /** SEC-63: warn only; blocking would fail an officer in an emergency. */
    val deviceLooksRooted: Boolean by lazy {
        Build.TAGS?.contains("test-keys") == true ||
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").any { File(it).exists() }
    }

    /** FR-67: personal phones have no device management, so an old build stops starting sessions by itself. */
    val buildExpired: Boolean
        get() = LocalDate.now().toEpochDay() - BuildConfig.BUILD_EPOCH_DAY > BUILD_LIFETIME_DAYS
    private val databaseKey = DatabaseKey(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var database: SessionDatabase? = null
    private var backgroundedAt = 0L

    private val _sessions = MutableStateFlow<SessionRepository?>(null)
    private val _keyReset = MutableStateFlow(false)
    /** True after stored sessions were lost because the screen lock changed; the officer is told once. */
    val keyReset: StateFlow<Boolean> = _keyReset.asStateFlow()
    fun acknowledgeKeyReset() { _keyReset.value = false }

    /** Null while the app is locked: no session data can be read without the officer's credential. */
    val sessions: StateFlow<SessionRepository?> = _sessions.asStateFlow()

    /** Call right after a successful authentication prompt, while the Keystore key is usable. */
    suspend fun unlock() = withContext(Dispatchers.IO) {
        if (_sessions.value != null) return@withContext
        val db = try {
            openDatabase()
        } catch (e: SQLiteNotADatabaseException) {
            // The file does not match the key (corrupted, or left from an older key): unreadable for good.
            databaseKey.destroy()
            openDatabase().also { _keyReset.value = true }
        }
        database = db
        val repo = SessionRepository(db.dao(), onSessionStarted = { settings.setNewestSessionAt(it) })
        repo.purgeExpired(settings.current().retentionHours)
        // Exported reports are only needed while the share sheet hands them over.
        File(app.cacheDir, "exports").deleteRecursively()
        _sessions.value = repo
    }

    private fun openDatabase(): SessionDatabase {
        val unwrapped = databaseKey.unwrap()
        if (unwrapped.reset) _keyReset.value = true
        // SQLCipher clears the key bytes itself once the database is open.
        return SessionDatabase.open(app, unwrapped.key).also { db ->
            runCatching { db.openHelper.writableDatabase }.onFailure { db.close(); throw it }
        }
    }

    fun unlockAsync(onFailed: () -> Unit) {
        scope.launch { runCatching { unlock() }.onFailure { Log.w(TAG, "unlock failed", it); onFailed() } }
    }

    fun lock() {
        _sessions.value = null
        database?.close()
        database = null
    }

    fun clearAllData() {
        lock()
        databaseKey.destroy()
        File(app.cacheDir, "exports").deleteRecursively()
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) lock()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 5 * 60 * 1000L
        const val BUILD_LIFETIME_DAYS = 180
        const val TAG = "SignSetu"
    }
}
