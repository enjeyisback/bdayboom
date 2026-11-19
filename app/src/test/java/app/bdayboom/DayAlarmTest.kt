package app.bdayboom

import app.bdayboom.data.EntryType
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import app.bdayboom.reminders.ReminderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class DayAlarmTest {
    private fun p(id: String, m: Int, d: Int, on: Boolean = true) = Person(id, EntryType.Birthday, id, m, d, null, null, "", on)
    private val nine = LocalTime.of(9, 0)
    private val people = listOf(p("asha", 11, 19), p("ravi", 11, 25), p("off", 11, 19, on = false))

    @Test fun ringsOnlyOnTheDayAfterAlarmTime() {
        assertTrue(ReminderEngine.alarmDueNow(people, nine, Feb29Rule.Feb28, LocalDateTime.of(2026, 11, 19, 8, 59)).isEmpty())
        assertEquals(listOf("asha"), ReminderEngine.alarmDueNow(people, nine, Feb29Rule.Feb28, LocalDateTime.of(2026, 11, 19, 9, 0)).map { it.person.id })
        // Too late (phone was off all morning): no ringing at night.
        assertTrue(ReminderEngine.alarmDueNow(people, nine, Feb29Rule.Feb28, LocalDateTime.of(2026, 11, 19, 21, 0)).isEmpty())
        // A day with nobody: nothing.
        assertTrue(ReminderEngine.alarmDueNow(people, nine, Feb29Rule.Feb28, LocalDateTime.of(2026, 11, 20, 9, 30)).isEmpty())
    }

    @Test fun nextAlarmSkipsDaysWithoutCelebrations() {
        val now = LocalDateTime.of(2026, 11, 19, 10, 0)
        assertEquals(LocalDateTime.of(2026, 11, 25, 9, 0), ReminderEngine.alarmNextAt(people, nine, Feb29Rule.Feb28, now, rangToday = true))
        // Alarm time moved later on the day: rings today unless it already rang.
        val later = LocalTime.of(11, 0)
        assertEquals(LocalDateTime.of(2026, 11, 19, 11, 0), ReminderEngine.alarmNextAt(people, later, Feb29Rule.Feb28, now, rangToday = false))
        assertEquals(LocalDateTime.of(2026, 11, 25, 11, 0), ReminderEngine.alarmNextAt(people, later, Feb29Rule.Feb28, now, rangToday = true))
    }
}
