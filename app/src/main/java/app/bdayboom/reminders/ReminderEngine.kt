package app.bdayboom.reminders

import app.bdayboom.data.Dates
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One reminder: [person]'s [occurrence], announced [daysBefore] days ahead, at [at] local time. */
data class Due(
    val person: Person,
    val occurrence: LocalDate,
    val daysBefore: Int,
    val at: LocalDateTime,
    val label: String? = null,
    val year: Int? = null,
)

/**
 * Pure scheduling maths. Reminders are rebuilt from local dates and the local reminder time every time,
 * never by adding 24h, so DST and time-zone changes can't shift them. Unit-tested in ReminderEngineTest.
 */
object ReminderEngine {

    fun candidates(people: List<Person>, offsets: List<Int>, time: LocalTime, rule: Feb29Rule, now: LocalDateTime): Sequence<Due> {
        val today = now.toLocalDate()
        return people.asSequence()
            .filter { it.remindersEnabled }
            .flatMap { p ->
                val first = Dates.nextOccurrence(p.month, p.day, today, rule)
                val second = Dates.nextOccurrence(p.month, p.day, first.plusDays(1), rule)
                val primary = offsets.asSequence().flatMap { off ->
                    sequenceOf(first, second).map { occ -> Due(p, occ, off, occ.minusDays(off.toLong()).atTime(time), label = null, year = p.year) }
                }
                val secondary = if (p.secondMonth != null && p.secondDay != null) {
                    val lbl = p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary"
                    val sFirst = Dates.nextOccurrence(p.secondMonth, p.secondDay, today, rule)
                    val sSecond = Dates.nextOccurrence(p.secondMonth, p.secondDay, sFirst.plusDays(1), rule)
                    offsets.asSequence().flatMap { off ->
                        sequenceOf(sFirst, sSecond).map { occ -> Due(p, occ, off, occ.minusDays(off.toLong()).atTime(time), label = lbl, year = p.secondYear) }
                    }
                } else emptySequence()
                primary + secondary
            }
    }

    /** Reminders whose time has come today and not yet passed into tomorrow: catch-up after a late install or boot. */
    fun dueNow(people: List<Person>, offsets: List<Int>, time: LocalTime, rule: Feb29Rule, now: LocalDateTime): List<Due> =
        candidates(people, offsets, time, rule, now)
            .filter { it.at.toLocalDate() == now.toLocalDate() && !it.at.isAfter(now) }
            .toList()

    /** The next moment anything needs announcing, or null when nothing is scheduled. */
    fun nextAt(people: List<Person>, offsets: List<Int>, time: LocalTime, rule: Feb29Rule, now: LocalDateTime): LocalDateTime? =
        candidates(people, offsets, time, rule, now)
            .map { it.at }
            .filter { it.isAfter(now) }
            .minOrNull()
}
