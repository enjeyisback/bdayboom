package app.bdayboom.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val feb29: Feb29Rule = Feb29Rule.Feb28,
    val theme: ThemeMode = ThemeMode.System,
    /** On-the-day reminders stay in the shade until "Wished" is tapped (reviews: "sticky notifications"). */
    val keepUntilWished: Boolean = true,
    val checkupSeen: Boolean = false,
    /** Folder (a document-tree URI) that gets a backup every week; null = automatic backup off. */
    val autoBackupFolder: String? = null,
    val autoBackupLast: Long = 0,
    val autoBackupFailed: Boolean = false,
    /** The user's own wishes; "{name}" becomes the person's first name. */
    val myWishes: List<String> = emptyList(),
)

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) : BackupSettings {
    private val hour = intPreferencesKey("reminder_hour")
    private val minute = intPreferencesKey("reminder_minute")
    private val feb29 = stringPreferencesKey("feb29")
    private val theme = stringPreferencesKey("theme")
    private val keep = booleanPreferencesKey("keep_until_wished")
    private val checkup = booleanPreferencesKey("checkup_seen")
    private val autoFolder = stringPreferencesKey("auto_backup_folder")
    private val autoLast = longPreferencesKey("auto_backup_last")
    private val autoFailed = booleanPreferencesKey("auto_backup_failed")
    private val wishes = stringPreferencesKey("my_wishes")

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            reminderHour = p[hour] ?: 9,
            reminderMinute = p[minute] ?: 0,
            feb29 = p[feb29]?.let { runCatching { Feb29Rule.valueOf(it) }.getOrNull() } ?: Feb29Rule.Feb28,
            theme = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            keepUntilWished = p[keep] ?: true,
            checkupSeen = p[checkup] ?: false,
            autoBackupFolder = p[autoFolder],
            autoBackupLast = p[autoLast] ?: 0,
            autoBackupFailed = p[autoFailed] ?: false,
            myWishes = p[wishes]?.let { j -> runCatching { JSONArray(j).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull() } ?: emptyList(),
        )
    }

    /** For receivers and workers, already off the main thread. */
    override fun now(): AppSettings = runBlocking { flow.first() }

    override fun restore(hour: Int?, minute: Int, feb29: Feb29Rule?, wishes: List<String>) {
        runBlocking {
            if (hour != null) setReminderTime(hour, minute)
            if (feb29 != null) setFeb29(feb29)
            wishes.forEach { addWish(it) }
        }
    }

    suspend fun setReminderTime(h: Int, m: Int) = context.store.edit { it[hour] = h; it[minute] = m }
    suspend fun setFeb29(rule: Feb29Rule) = context.store.edit { it[feb29] = rule.name }
    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[theme] = mode.name }
    suspend fun setKeepUntilWished(on: Boolean) = context.store.edit { it[keep] = on }
    suspend fun setCheckupSeen() = context.store.edit { it[checkup] = true }

    suspend fun setAutoBackupFolder(uri: String?) = context.store.edit {
        if (uri == null) it.remove(autoFolder) else it[autoFolder] = uri
        it.remove(autoLast); it.remove(autoFailed)
    }
    suspend fun setAutoBackupResult(ok: Boolean, at: Long) = context.store.edit {
        it[autoFailed] = !ok
        if (ok) it[autoLast] = at
    }

    /** Adds to the end; blank or already-saved wishes are ignored. */
    suspend fun addWish(text: String) = editWishes { list -> text.trim().let { if (it.isEmpty() || it in list) list else list + it } }
    suspend fun removeWish(text: String) = editWishes { it - text }
    private suspend fun editWishes(change: (List<String>) -> List<String>) = context.store.edit { p ->
        val now = p[wishes]?.let { j -> runCatching { JSONArray(j).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull() } ?: emptyList()
        p[wishes] = JSONArray(change(now)).toString()
    }
}
