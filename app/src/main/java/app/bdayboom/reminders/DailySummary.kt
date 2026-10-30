package app.bdayboom.reminders

import app.bdayboom.data.EntryType
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import app.bdayboom.data.Upcoming
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class Summary(val title: String, val body: String)

/**
 * The 9:00 morning summary. Fixed time on purpose (phone's local time); the user can only turn it off.
 * Says something when there's a celebration today or in the next 7 days, or on the 1st of the month.
 * A quiet week gets one "nothing this week" on Monday, never a daily empty message.
 */
object DailySummary {
    val AT: LocalTime = LocalTime.of(9, 0)
    /** A late alarm (phone off, no exact-alarm permission) still sends until noon; after that it's not a morning summary. */
    private val UNTIL: LocalTime = LocalTime.NOON

    fun shouldSendNow(now: LocalDateTime, lastSent: String?): Boolean =
        lastSent != now.toLocalDate().toString() && !now.toLocalTime().isBefore(AT) && now.toLocalTime().isBefore(UNTIL)

    /** Next 9:00 still to come (today's if it hasn't been sent yet). */
    fun nextAt(now: LocalDateTime, lastSent: String?): LocalDateTime {
        val today = now.toLocalDate()
        return if (now.toLocalTime().isBefore(AT) && lastSent != today.toString()) today.atTime(AT) else today.plusDays(1).atTime(AT)
    }

    fun build(people: List<Person>, today: LocalDate, rule: Feb29Rule): Summary? {
        val events = people.filter { it.remindersEnabled }.flatMap { p ->
            listOfNotNull(
                Upcoming.of(p, today, rule),
                if (p.secondMonth != null && p.secondDay != null) {
                    Upcoming.of(p, today, rule, p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary", p.secondMonth, p.secondDay, p.secondYear)
                } else null,
            )
        }.sortedWith(compareBy({ it.daysLeft }, { it.person.name.lowercase() }))
        if (events.isEmpty()) return null

        val todays = events.filter { it.daysLeft == 0 }
        val week = events.filter { it.daysLeft in 1..6 }
        val month = if (today.dayOfMonth == 1) monthLine(events, today) else null
        return when {
            todays.isNotEmpty() -> Summary(
                "🎂 Today: " + names(todays),
                listOfNotNull(if (week.isNotEmpty()) "Also this week: " + dayList(week) else "Open BdayBoom to send a wish.", month).joinToString("\n"),
            )
            week.isNotEmpty() -> Summary("This week: " + count(week), listOfNotNull(dayList(week), month).joinToString("\n"))
            month != null -> Summary(month, nextUp(events))
            today.dayOfWeek == DayOfWeek.MONDAY -> Summary("No birthdays this week", nextUp(events))
            else -> null
        }
    }

    private fun isBirthday(u: Upcoming) = u.eventLabel == null && u.person.type == EntryType.Birthday

    /** "5 birthdays", "1 birthday", or "celebrations" once anniversaries or events are in the mix. */
    private fun count(list: List<Upcoming>): String {
        val word = if (list.all(::isBirthday)) "birthday" else "celebration"
        return "${list.size} $word" + if (list.size == 1) "" else "s"
    }

    /** "Asha and Rohan", "Asha, Rohan, Meera and 2 more". */
    private fun names(list: List<Upcoming>): String {
        val n = list.map { it.person.name.trim() }.distinct()
        return when {
            n.size == 1 -> n[0]
            n.size <= 3 -> n.dropLast(1).joinToString(", ") + " and " + n.last()
            else -> n.take(3).joinToString(", ") + " and ${n.size - 3} more"
        }
    }

    /** "Meera Thu · Kabir Sat · +3 more". */
    private fun dayList(list: List<Upcoming>): String {
        val shown = list.take(3).joinToString(" · ") { "${it.person.name.trim()} ${it.next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }
        return shown + if (list.size > 3) " · +${list.size - 3} more" else ""
    }

    private fun nextUp(events: List<Upcoming>): String {
        val u = events.first { it.daysLeft > 0 }
        return "Next up: ${u.person.name.trim()} on ${u.next.dayOfMonth} ${u.next.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    }

    /** "November: 8 birthdays", or null if the month is empty. */
    private fun monthLine(events: List<Upcoming>, today: LocalDate): String? {
        val inMonth = events.filter { it.next.month == today.month && it.next.year == today.year }
        if (inMonth.isEmpty()) return null
        return today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ": " + count(inMonth)
    }
}
