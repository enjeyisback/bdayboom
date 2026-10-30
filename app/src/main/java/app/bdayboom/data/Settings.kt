package app.bdayboom.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val feb29: Feb29Rule = Feb29Rule.Feb28,
    val theme: ThemeMode = ThemeMode.System,
    /** On-the-day reminders stay in the shade until "Wished" is tapped (reviews: "sticky notifications"). */
    val keepUntilWished: Boolean = true,
    val checkupSeen: Boolean = false,
)

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val hour = intPreferencesKey("reminder_hour")
    private val minute = intPreferencesKey("reminder_minute")
    private val feb29 = stringPreferencesKey("feb29")
    private val theme = stringPreferencesKey("theme")
    private val keep = booleanPreferencesKey("keep_until_wished")
    private val checkup = booleanPreferencesKey("checkup_seen")

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            reminderHour = p[hour] ?: 9,
            reminderMinute = p[minute] ?: 0,
            feb29 = p[feb29]?.let { runCatching { Feb29Rule.valueOf(it) }.getOrNull() } ?: Feb29Rule.Feb28,
            theme = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            keepUntilWished = p[keep] ?: true,
            checkupSeen = p[checkup] ?: false,
        )
    }

    /** For receivers and workers, already off the main thread. */
    fun now(): AppSettings = runBlocking { flow.first() }

    suspend fun setReminderTime(h: Int, m: Int) = context.store.edit { it[hour] = h; it[minute] = m }
    suspend fun setFeb29(rule: Feb29Rule) = context.store.edit { it[feb29] = rule.name }
    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[theme] = mode.name }
    suspend fun setKeepUntilWished(on: Boolean) = context.store.edit { it[keep] = on }
    suspend fun setCheckupSeen() = context.store.edit { it[checkup] = true }
}
