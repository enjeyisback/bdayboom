package app.bdayboom

import app.bdayboom.data.ContactsReader
import app.bdayboom.data.Dates
import app.bdayboom.data.EntryType
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import app.bdayboom.reminders.ReminderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DatesTest {
    private val nine = LocalTime.of(9, 0)

    private fun person(m: Int, d: Int, y: Int? = null, on: Boolean = true) =
        Person("p", EntryType.Birthday, "P", m, d, y, null, "", on)

    @Test fun nextOccurrenceIsTodayWhenTodayIsTheDay() {
        val today = LocalDate.of(2026, 10, 30)
        assertEquals(today, Dates.nextOccurrence(10, 30, today, Feb29Rule.Feb28))
    }

    @Test fun nextOccurrenceRollsToNextYear() {
        assertEquals(LocalDate.of(2027, 1, 5), Dates.nextOccurrence(1, 5, LocalDate.of(2026, 10, 30), Feb29Rule.Feb28))
    }

    @Test fun leapDayInNonLeapYearFollowsTheRule() {
        assertEquals(LocalDate.of(2027, 2, 28), Dates.occurrenceIn(2027, 2, 29, Feb29Rule.Feb28))
        assertEquals(LocalDate.of(2027, 3, 1), Dates.occurrenceIn(2027, 2, 29, Feb29Rule.Mar1))
        assertEquals(LocalDate.of(2028, 2, 29), Dates.occurrenceIn(2028, 2, 29, Feb29Rule.Feb28))
    }

    @Test fun turningAndUnknownYear() {
        assertEquals(31, Dates.turning(1995, LocalDate.of(2026, 3, 11)))
        assertNull(Dates.turning(null, LocalDate.of(2026, 3, 11)))
        assertNull(Dates.turning(2030, LocalDate.of(2026, 3, 11)))
    }

    @Test fun validity() {
        assertTrue(Dates.isValid(2, 29))
        assertFalse(Dates.isValid(4, 31))
        assertFalse(Dates.isValid(13, 1))
    }

    @Test fun zodiacEdges() {
        assertEquals("Capricorn", Dates.zodiac(1, 19))
        assertEquals("Aquarius", Dates.zodiac(1, 20))
        assertEquals("Sagittarius", Dates.zodiac(12, 21))
        assertEquals("Capricorn", Dates.zodiac(12, 22))
        assertEquals("Libra", Dates.zodiac(10, 16))
        assertEquals("Ox · Fire", Dates.chineseZodiac(1997))
    }

    @Test fun contactDateFormats() {
        assertEquals(Triple(1990, 5, 12), ContactsReader.parse("1990-05-12"))
        assertEquals(Triple<Int?, Int, Int>(null, 5, 12), ContactsReader.parse("--05-12"))
        assertEquals(Triple(1990, 5, 12), ContactsReader.parse("19900512"))
        assertEquals(Triple(1990, 5, 12), ContactsReader.parse("1990-05-12T00:00:00.000Z"))
        assertNull(ContactsReader.parse("04/31/1990"))
    }

    @Test fun dueNowWhenTimeHasPassedToday() {
        val now = LocalDateTime.of(2026, 10, 30, 10, 0)
        val due = ReminderEngine.dueNow(listOf(person(10, 30)), listOf(0), nine, Feb29Rule.Feb28, now)
        assertEquals(1, due.size)
        assertEquals(LocalDate.of(2026, 10, 30), due[0].occurrence)
    }

    @Test fun notDueBeforeTheTime() {
        val now = LocalDateTime.of(2026, 10, 30, 8, 59)
        assertTrue(ReminderEngine.dueNow(listOf(person(10, 30)), listOf(0), nine, Feb29Rule.Feb28, now).isEmpty())
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 0), ReminderEngine.nextAt(listOf(person(10, 30)), listOf(0), nine, Feb29Rule.Feb28, now))
    }

    @Test fun daysBeforeOffsetPicksTheEarlierDate() {
        val now = LocalDateTime.of(2026, 10, 30, 12, 0)
        // birthday on 5 Nov, remind 7 days before -> 29 Oct already passed -> next year; 1 day before -> 4 Nov
        val next = ReminderEngine.nextAt(listOf(person(11, 5)), listOf(7, 1), nine, Feb29Rule.Feb28, now)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), next)
    }

    @Test fun disabledPeopleAreSkipped() {
        val now = LocalDateTime.of(2026, 10, 30, 10, 0)
        assertTrue(ReminderEngine.dueNow(listOf(person(10, 30, on = false)), listOf(0), nine, Feb29Rule.Feb28, now).isEmpty())
        assertNull(ReminderEngine.nextAt(listOf(person(10, 30, on = false)), listOf(0), nine, Feb29Rule.Feb28, now))
    }

    @Test fun monthBeforeOffsetFiresThirtyDaysAhead() {
        val now = LocalDateTime.of(2026, 10, 30, 12, 0)
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), ReminderEngine.nextAt(listOf(person(12, 5)), listOf(30), nine, Feb29Rule.Feb28, now))
    }

    @Test fun secondDateGetsItsOwnLabelledReminder() {
        val p = person(3, 1).copy(secondLabel = "Wedding", secondMonth = 10, secondDay = 30, secondYear = 2016)
        val due = ReminderEngine.dueNow(listOf(p), listOf(0), nine, Feb29Rule.Feb28, LocalDateTime.of(2026, 10, 30, 9, 0))
        assertEquals(1, due.size)
        assertEquals("Wedding", due[0].label)
        assertEquals(10, Dates.turning(due[0].year, due[0].occurrence))
    }

    @Test fun leapDayReminderLandsOnFeb28() {
        val now = LocalDateTime.of(2027, 2, 1, 12, 0)
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), ReminderEngine.nextAt(listOf(person(2, 29, 2000)), listOf(0), nine, Feb29Rule.Feb28, now))
    }
}
