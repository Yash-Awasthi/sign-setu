package app.signsetu.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class InterpreterApp { NONE, WHATSAPP, SANDES }

/** Settings hold no personal data about any subject (SEC-07). */
@Immutable
data class AppSettings(
    val firstRunDone: Boolean = false,
    val retentionHours: Int = DEFAULT_RETENTION_HOURS,
    val interpreterApp: InterpreterApp = InterpreterApp.NONE,
    /** A phone number or contact name given by the department; never a subject's details. */
    val interpreterContact: String = "",
    /** Start time of the newest session; lets the purge worker shred everything without the officer's key. */
    val newestSessionAt: Long = 0L,
) {
    val interpreterConfigured get() = interpreterApp != InterpreterApp.NONE && interpreterContact.isNotBlank()

    companion object {
        const val DEFAULT_RETENTION_HOURS = 24
        val RETENTION_CHOICES = listOf(4, 8, 12, 24, 48, 72)
    }
}

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val firstRun = booleanPreferencesKey("first_run_done")
        val retention = intPreferencesKey("retention_hours")
        val interpreterApp = stringPreferencesKey("interpreter_app")
        val interpreterContact = stringPreferencesKey("interpreter_contact")
        val newestSession = longPreferencesKey("newest_session_at")
    }

    val settings: Flow<AppSettings> = context.store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setFirstRunDone() = context.store.edit { it[Keys.firstRun] = true }
    suspend fun setRetentionHours(hours: Int) = context.store.edit { it[Keys.retention] = hours.coerceIn(4, 72) }
    suspend fun setInterpreter(app: InterpreterApp, contact: String) = context.store.edit {
        it[Keys.interpreterApp] = app.name
        it[Keys.interpreterContact] = contact.trim()
    }
    suspend fun setNewestSessionAt(time: Long) = context.store.edit { it[Keys.newestSession] = time }

    private fun Preferences.toSettings() = AppSettings(
        firstRunDone = this[Keys.firstRun] ?: false,
        retentionHours = this[Keys.retention] ?: AppSettings.DEFAULT_RETENTION_HOURS,
        interpreterApp = this[Keys.interpreterApp]?.let { runCatching { InterpreterApp.valueOf(it) }.getOrNull() } ?: InterpreterApp.NONE,
        interpreterContact = this[Keys.interpreterContact] ?: "",
        newestSessionAt = this[Keys.newestSession] ?: 0L,
    )
}

/**
 * The international number wa.me expects, without "+". A 10-digit number, or 11 with a leading trunk 0, is Indian.
 * Returns null when the contact cannot be a phone number.
 */
fun whatsappNumber(contact: String): String? {
    val digits = contact.filter(Char::isDigit).let { if (it.length == 11 && it.startsWith("0")) it.drop(1) else it }
    return when (digits.length) {
        10 -> "91$digits"
        in 11..15 -> digits
        else -> null
    }
}
