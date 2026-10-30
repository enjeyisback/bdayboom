package app.bdayboom.data

import app.bdayboom.data.db.Entry
import app.bdayboom.data.db.Person_group
import java.time.LocalDate

enum class EntryType(val db: String) {
    Birthday("birthday"), Anniversary("anniversary"), Event("event");

    companion object {
        fun of(db: String) = entries.firstOrNull { it.db == db } ?: Birthday
    }
}

enum class Source(val db: String) { Manual("manual"), Contacts("contacts"), Link("link"), Restore("restore") }

data class Person(
    val id: String,
    val type: EntryType,
    val name: String,
    val month: Int,
    val day: Int,
    val year: Int?,
    val groupId: String?,
    val notes: String,
    val remindersEnabled: Boolean,
    val source: String = Source.Manual.db,
    val contactKey: String? = null,
    val photoPath: String? = null,
    val secondLabel: String? = null,
    val secondMonth: Int? = null,
    val secondDay: Int? = null,
    val secondYear: Int? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class Group(val id: String, val name: String)

/** A person plus everything derived from today's date. */
data class Upcoming(
    val person: Person,
    val next: LocalDate,
    val daysLeft: Int,
    val turning: Int?,
    val eventLabel: String? = null,
) {
    val isToday get() = daysLeft == 0

    companion object {
        fun of(
            p: Person,
            today: LocalDate,
            rule: Feb29Rule,
            eventLabel: String? = null,
            customMonth: Int? = null,
            customDay: Int? = null,
            customYear: Int? = null,
        ): Upcoming {
            val m = customMonth ?: p.month
            val d = customDay ?: p.day
            val y = customYear ?: p.year
            val next = Dates.nextOccurrence(m, d, today, rule)
            return Upcoming(p, next, Dates.daysBetween(today, next), Dates.turning(y, next), eventLabel)
        }
    }
}

internal fun Entry.toPerson() = Person(
    id = id,
    type = EntryType.of(type),
    name = name,
    month = month.toInt(),
    day = day.toInt(),
    year = year?.toInt(),
    groupId = group_id,
    notes = notes,
    remindersEnabled = reminders_enabled == 1L,
    source = source,
    contactKey = contact_lookup_key,
    photoPath = photo_path,
    secondLabel = second_label,
    secondMonth = second_month?.toInt(),
    secondDay = second_day?.toInt(),
    secondYear = second_year?.toInt(),
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun Person_group.toGroup() = Group(id, name)
