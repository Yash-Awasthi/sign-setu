package app.signsetu

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.DatabaseKey
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignSetuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container)
    }
}

/** Manual wiring; the app is small enough not to need a DI framework (02-architecture §3). */
class AppContainer(private val app: Application) : DefaultLifecycleObserver {
    val content = ContentRepository(app.assets)
    private val databaseKey = DatabaseKey(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var database: SessionDatabase? = null
    private var backgroundedAt = 0L

    private val _sessions = MutableStateFlow<SessionRepository?>(null)
    /** Null while the app is locked: no session data can be read without the officer's credential. */
    val sessions: StateFlow<SessionRepository?> = _sessions.asStateFlow()

    /** Call right after a successful authentication prompt, while the Keystore key is usable. */
    suspend fun unlock() = withContext(Dispatchers.IO) {
        if (_sessions.value != null) return@withContext
        // SQLCipher clears the key bytes itself once the database is open.
        val db = SessionDatabase.open(app, databaseKey.unwrap())
        db.openHelper.writableDatabase
        database = db
        val repo = SessionRepository(db.dao())
        repo.purgeExpired(RETENTION_HOURS)
        _sessions.value = repo
    }

    fun unlockAsync(onFailed: () -> Unit) {
        scope.launch { runCatching { unlock() }.onFailure { onFailed() } }
    }

    fun lock() {
        _sessions.value = null
        database?.close()
        database = null
    }

    fun clearAllData() {
        lock()
        databaseKey.destroy()
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) lock()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 5 * 60 * 1000L
        const val RETENTION_HOURS = 24
    }
}
