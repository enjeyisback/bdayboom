package app.bdayboom.data

import app.bdayboom.data.db.AppDatabase
import app.bdayboom.data.db.Entry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)

/** What a backup file holds, shown before restoring so the wrong file isn't picked by accident. */
data class BackupPreview(val exportedAt: String, val people: Int, val photos: Int)

data class RestoreResult(val added: Int, val skipped: Int)

/**
 * Backup file = a zip holding backup.json (and, once photos exist, photos/).
 * schema_version lets older backups restore into newer apps; newer ones are refused with a clear message.
 */
/** The settings a backup carries. SettingsStore implements it; tests use a plain fake. */
interface BackupSettings {
    fun now(): AppSettings
    fun restore(hour: Int?, minute: Int, feb29: Feb29Rule?, wishes: List<String>)
}

class Backup(
    private val db: AppDatabase,
    private val settings: BackupSettings,
    private val photoDir: File,
    private val onChange: () -> Unit,
) {

    fun write(out: OutputStream, appVersion: String) {
        val json = snapshot(appVersion)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            db.entryQueries.selectActive().executeAsList().forEach { e ->
                val f = e.photo_path?.let(::File)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry(PHOTO_DIR + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Reads the file and says what's in it. Changes nothing. */
    fun preview(input: InputStream): BackupPreview {
        val (json, photos) = readZip(input)
        validate(json)
        return BackupPreview(
            exportedAt = json.optString("exported_at", ""),
            people = json.optJSONArray("entries")?.length() ?: 0,
            photos = photos.size,
        )
    }

    private fun validate(json: JSONObject) {
        if (json.optString("format") != FORMAT) throw BackupException("This file isn't a BdayBoom backup.")
        if (json.optInt("schema_version", 0) > SCHEMA_VERSION) {
            throw BackupException("This backup was made by a newer version of BdayBoom. Update the app, then try again.")
        }
    }

    /** replace = wipe the list first; otherwise only people and groups not already here are added. */
    fun restore(input: InputStream, replace: Boolean): RestoreResult {
        val (json, photos) = readZip(input)
        validate(json)

        val now = System.currentTimeMillis()
        var added = 0
        var skipped = 0
        db.transaction {
            // Deleting people cascades to their "already notified" rows; keep them so a replace-restore on the
            // same phone doesn't repeat today's reminders. (A new phone has none, so it still notifies once.)
            val keptFired = if (replace) db.reminderQueries.selectAllFired().executeAsList() else emptyList()
            if (replace) {
                db.entryQueries.deleteAll()
                db.personGroupQueries.deleteAll()
                db.reminderQueries.deleteAllOffsets()
            }

            // Groups: same name already here (e.g. the default "Family") -> reuse that id.
            val groupIds = mutableMapOf<String, String>()
            json.optJSONArray("groups")?.forEachObject { g ->
                val id = g.getString("id")
                val name = g.getString("name").trim()
                val existing = db.personGroupQueries.selectByName(name).executeAsOneOrNull()
                if (existing != null) {
                    groupIds[id] = existing.id
                } else {
                    db.personGroupQueries.insertOrIgnore(id, name, g.optLong("sort_order", 0), now, now)
                    groupIds[id] = id
                }
            }

            json.optJSONArray("entries")?.forEachObject { e ->
                val id = e.getString("id")
                val month = e.getInt("month")
                val day = e.getInt("day")
                val name = e.optString("name").trim()
                val alreadyHere = db.entryQueries.selectByIdAny(id).executeAsOneOrNull() != null
                val contactKey = e.optStringOrNull("contact_lookup_key")
                val keyTaken = contactKey != null && db.entryQueries.selectByContactKey(contactKey).executeAsOneOrNull() != null
                if (alreadyHere || keyTaken || name.isEmpty() || !Dates.isValid(month, day)) {
                    skipped++
                    return@forEachObject
                }
                db.entryQueries.insert(
                    Entry(
                        id = id,
                        type = EntryType.of(e.optString("type", "birthday")).db,
                        name = name,
                        month = month.toLong(),
                        day = day.toLong(),
                        year = e.optLongOrNull("year"),
                        second_label = e.optStringOrNull("second_label"),
                        second_month = e.optLongOrNull("second_month"),
                        second_day = e.optLongOrNull("second_day"),
                        second_year = e.optLongOrNull("second_year"),
                        photo_path = e.optStringOrNull("photo")?.let { photos[it] }?.let { bytes ->
                            File(photoDir.apply { mkdirs() }, "${java.util.UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }.absolutePath
                        },
                        group_id = e.optStringOrNull("group_id")?.let { groupIds[it] },
                        notes = e.optString("notes", ""),
                        reminders_enabled = if (e.optBoolean("reminders_enabled", true)) 1 else 0,
                        source = Source.Restore.db,
                        contact_lookup_key = contactKey,
                        remote_submission_id = null,
                        created_at = e.optLong("created_at", now),
                        updated_at = now,
                        deleted_at = null,
                    ),
                )
                added++
            }

            for (f in keptFired) {
                if (db.entryQueries.selectByIdAny(f.entry_id).executeAsOneOrNull() != null) {
                    db.reminderQueries.markFired(f.id, f.entry_id, f.occurrence_date, f.days_before, f.fired_at, f.created_at, f.updated_at)
                }
            }

            val offsets = json.optJSONArray("reminder_offsets")
            if (offsets != null) {
                for (i in 0 until offsets.length()) {
                    val d = offsets.getLong(i)
                    if (d in 0..60) db.reminderQueries.addOffset("off-$d", d, now, now)
                }
            }
            if (replace && db.reminderQueries.offsets().executeAsList().isEmpty()) {
                db.reminderQueries.addOffset("off-0", 0, now, now) // never leave someone with no reminders by accident
            }
        }

        json.optJSONObject("settings")?.let { s ->
            settings.restore(
                hour = if (s.has("reminder_hour")) s.getInt("reminder_hour") else null,
                minute = s.optInt("reminder_minute", 0),
                feb29 = s.optStringOrNull("feb29")?.let { runCatching { Feb29Rule.valueOf(it) }.getOrNull() },
                wishes = s.optJSONArray("my_wishes")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            )
        }
        onChange()
        return RestoreResult(added, skipped)
    }

    private fun snapshot(appVersion: String): JSONObject {
        val s = settings.now()
        return JSONObject()
            .put("format", FORMAT)
            .put("schema_version", SCHEMA_VERSION)
            .put("app_version", appVersion)
            .put("exported_at", LocalDateTime.now().toString())
            .put(
                "settings",
                JSONObject().put("reminder_hour", s.reminderHour).put("reminder_minute", s.reminderMinute).put("feb29", s.feb29.name)
                    .put("my_wishes", JSONArray(s.myWishes)),
            )
            .put("reminder_offsets", JSONArray(db.reminderQueries.offsets().executeAsList()))
            .put(
                "groups",
                JSONArray(
                    db.personGroupQueries.selectAll().executeAsList().map {
                        JSONObject().put("id", it.id).put("name", it.name).put("sort_order", it.sort_order)
                    },
                ),
            )
            .put(
                "entries",
                JSONArray(
                    db.entryQueries.selectActive().executeAsList().map { e ->
                        JSONObject()
                            .put("id", e.id).put("type", e.type).put("name", e.name)
                            .put("month", e.month).put("day", e.day).putOpt("year", e.year)
                            .putOpt("second_label", e.second_label).putOpt("second_month", e.second_month)
                            .putOpt("second_day", e.second_day).putOpt("second_year", e.second_year)
                            .putOpt("group_id", e.group_id).put("notes", e.notes)
                            .put("reminders_enabled", e.reminders_enabled == 1L)
                            .put("source", e.source).putOpt("contact_lookup_key", e.contact_lookup_key)
                            .putOpt("photo", e.photo_path?.let(::File)?.takeIf { it.isFile }?.name)
                            .put("created_at", e.created_at)
                    },
                ),
            )
    }

    /** backup.json plus photos by file name. Photo names are reduced to a bare name, so a crafted zip can't write elsewhere. */
    private fun readZip(input: InputStream): Pair<JSONObject, Map<String, ByteArray>> {
        var json: JSONObject? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == JSON_NAME -> json = runCatching { JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }
                        .getOrElse { throw BackupException("The backup file is damaged.") }
                    entry.name.startsWith(PHOTO_DIR) && !entry.isDirectory -> {
                        val bytes = zip.readBytes()
                        if (bytes.size <= MAX_PHOTO_BYTES) photos[File(entry.name).name] = bytes
                    }
                }
            }
        }
        return (json ?: throw BackupException("This file isn't a BdayBoom backup.")) to photos
    }

    companion object {
        const val FORMAT = "birthdays-backup"
        const val SCHEMA_VERSION = 1
        private const val JSON_NAME = "backup.json"
        private const val PHOTO_DIR = "photos/"
        private const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
    }
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
