package app.bdayboom.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.bdayboom.data.db.AppDatabase
import app.bdayboom.data.db.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * All reads are flows from SQLite; every write calls [onChange] so reminders get rescheduled.
 * Blocking variants (suffix Now) are for receivers and workers that are already off the main thread.
 */
class Repository(private val db: AppDatabase, private val onChange: () -> Unit) {

    private val io = Dispatchers.IO

    val people: Flow<List<Person>> =
        db.entryQueries.selectActive().asFlow().mapToList(io).map { list -> list.map { it.toPerson() } }

    val groups: Flow<List<Group>> =
        db.personGroupQueries.selectAll().asFlow().mapToList(io).map { list -> list.map { it.toGroup() } }

    val offsets: Flow<List<Int>> =
        db.reminderQueries.offsets().asFlow().mapToList(io).map { list -> list.map { it.toInt() } }

    fun person(id: String): Flow<Person?> =
        db.entryQueries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.takeIf { e -> e.deleted_at == null }?.toPerson() }

    fun peopleNow(): List<Person> = db.entryQueries.selectActive().executeAsList().map { it.toPerson() }

    fun offsetsNow(): List<Int> = db.reminderQueries.offsets().executeAsList().map { it.toInt() }

    /** Insert or update. Returns the id. */
    suspend fun save(p: Person): String = withContext(io) {
        val now = System.currentTimeMillis()
        val id = p.id.ifBlank { UUID.randomUUID().toString() }
        db.transaction {
            val existing = db.entryQueries.selectById(id).executeAsOneOrNull()
            if (existing == null) {
                db.entryQueries.insert(p.copy(id = id).toEntry(createdAt = now, updatedAt = now))
            } else {
                db.entryQueries.update(
                    type = p.type.db,
                    name = p.name.trim(),
                    month = p.month.toLong(),
                    day = p.day.toLong(),
                    year = p.year?.toLong(),
                    second_label = p.secondLabel,
                    second_month = p.secondMonth?.toLong(),
                    second_day = p.secondDay?.toLong(),
                    second_year = p.secondYear?.toLong(),
                    photo_path = p.photoPath,
                    group_id = p.groupId,
                    notes = p.notes,
                    reminders_enabled = if (p.remindersEnabled) 1 else 0,
                    contact_lookup_key = p.contactKey,
                    updated_at = now,
                    id = id,
                )
            }
        }
        onChange()
        id
    }

    suspend fun softDelete(id: String) = withContext(io) {
        db.entryQueries.softDelete(System.currentTimeMillis(), id)
        onChange()
    }

    suspend fun undoDelete(id: String) = withContext(io) {
        db.entryQueries.undoDelete(id)
        onChange()
    }

    suspend fun addGroup(name: String): Group = withContext(io) {
        val clean = name.trim()
        db.personGroupQueries.selectByName(clean).executeAsOneOrNull()?.let { return@withContext it.toGroup() }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sort = db.personGroupQueries.maxSort().executeAsOne() + 1
        db.personGroupQueries.insert(id, clean, sort, now, now)
        Group(id, clean)
    }

    suspend fun setOffset(daysBefore: Int, on: Boolean) = withContext(io) {
        if (on) {
            val now = System.currentTimeMillis()
            db.reminderQueries.addOffset("off-$daysBefore", daysBefore.toLong(), now, now)
        } else {
            db.reminderQueries.removeOffset(daysBefore.toLong())
        }
        onChange()
    }

    /** Keys of people already imported from contacts, mapped to their stored (month, day, year). */
    suspend fun importedContacts(): Map<String, Person> = withContext(io) {
        peopleNow().filter { it.contactKey != null }.associateBy { it.contactKey!! }
    }

    /** Upserts by contact key inside one transaction; user-made notes, group and photo are kept. */
    suspend fun importContacts(found: List<ContactBirthday>): Int = withContext(io) {
        val now = System.currentTimeMillis()
        var count = 0
        db.transaction {
            for (c in found) {
                val existing = db.entryQueries.selectByContactKey(c.key).executeAsOneOrNull()?.toPerson()
                if (existing == null) {
                    db.entryQueries.insert(
                        Person(
                            id = UUID.randomUUID().toString(), type = c.type, name = c.name,
                            month = c.month, day = c.day, year = c.year, groupId = null, notes = "",
                            remindersEnabled = true, source = Source.Contacts.db, contactKey = c.key,
                        ).toEntry(now, now),
                    )
                } else {
                    db.entryQueries.update(
                        type = existing.type.db, name = existing.name, month = c.month.toLong(), day = c.day.toLong(),
                        year = c.year?.toLong(), second_label = existing.secondLabel, second_month = existing.secondMonth?.toLong(),
                        second_day = existing.secondDay?.toLong(), second_year = existing.secondYear?.toLong(),
                        photo_path = existing.photoPath, group_id = existing.groupId, notes = existing.notes,
                        reminders_enabled = if (existing.remindersEnabled) 1 else 0, contact_lookup_key = c.key,
                        updated_at = now, id = existing.id,
                    )
                }
                count++
            }
        }
        onChange()
        count
    }

    /** Returns true the first time; false when this exact reminder was already shown. */
    fun markFiredNow(entryId: String, occurrence: String, daysBefore: Int): Boolean =
        db.transactionWithResult {
            val seen = db.reminderQueries.wasFired(entryId, occurrence, daysBefore.toLong()).executeAsOne() > 0
            if (!seen) {
                val now = System.currentTimeMillis()
                db.reminderQueries.markFired(UUID.randomUUID().toString(), entryId, occurrence, daysBefore.toLong(), now, now, now)
            }
            !seen
        }

    fun purgeNow() {
        val now = System.currentTimeMillis()
        db.entryQueries.purgeDeleted(now - 7L * 24 * 3600 * 1000)
        db.reminderQueries.purgeFired(now - 400L * 24 * 3600 * 1000)
    }

    fun deleteAllNow() = db.entryQueries.deleteAll()
}

data class ContactBirthday(val key: String, val name: String, val type: EntryType, val month: Int, val day: Int, val year: Int?)

private fun Person.toEntry(createdAt: Long, updatedAt: Long) = Entry(
    id = id,
    type = type.db,
    name = name.trim(),
    month = month.toLong(),
    day = day.toLong(),
    year = year?.toLong(),
    second_label = secondLabel,
    second_month = secondMonth?.toLong(),
    second_day = secondDay?.toLong(),
    second_year = secondYear?.toLong(),
    photo_path = photoPath,
    group_id = groupId,
    notes = notes,
    reminders_enabled = if (remindersEnabled) 1 else 0,
    source = source,
    contact_lookup_key = contactKey,
    remote_submission_id = null,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)
