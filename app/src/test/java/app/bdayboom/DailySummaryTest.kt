package app.bdayboom

import app.bdayboom.data.EntryType
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import app.bdayboom.reminders.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class DailySummaryTest {
    private val friday = LocalDate.of(2026, 10, 30)
    private val sunday = LocalDate.of(2026, 11, 8)

    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun p(name: String, m: Int, d: Int, type: EntryType = EntryType.Birthday, on: Boolean = true) =
        Person(name, type, name, m, d, null, null, "", on)

    @Test fun todayNamesThemAndListsTheRestOfTheWeek() {
        val s = DailySummary.build(listOf(p("Asha", 10, 30), p("Rohan", 10, 30), p("Meera", 10, 31), p("Kabir", 11, 1)), friday, Feb29Rule.Feb28)!!
        assertEquals("🎂 Today: Asha and Rohan", s.title)
        assertEquals("Also this week: Meera Sat", s.body) // Kabir's Sunday is next week
    }

    @Test fun weekCountsAndShowsTheFirstThree() {
        val people = listOf(p("A", 11, 9), p("B", 11, 10), p("C", 11, 11), p("D", 11, 12), p("E", 11, 14), p("F", 11, 15))
        val s = DailySummary.build(people, sunday, Feb29Rule.Feb28)!!
        assertEquals("This week: 5 birthdays", s.title) // F is on the next Sunday
        assertEquals("A Mon · B Tue · C Wed · +2 more", s.body)
    }

    @Test fun anniversariesMakeItCelebrations() {
        val s = DailySummary.build(listOf(p("A", 11, 9), p("B", 11, 10, EntryType.Anniversary)), sunday, Feb29Rule.Feb28)!!
        assertEquals("This week: 2 celebrations", s.title)
    }

    @Test fun quietWeekSaysSoOnSundayOnly() {
        val people = listOf(p("Riya", 11, 20))
        assertNull(DailySummary.build(people, friday, Feb29Rule.Feb28))
        val s = DailySummary.build(people, sunday, Feb29Rule.Feb28)!!
        assertEquals("No birthdays this week", s.title)
        assertEquals("Next up: Riya on 20 Nov", s.body)
    }

    @Test fun firstOfTheMonthGivesTheMonth() {
        val s = DailySummary.build(listOf(p("Riya", 11, 20), p("Dev", 11, 25)), LocalDate.of(2026, 11, 1), Feb29Rule.Feb28)!!
        assertEquals("November: 2 birthdays", s.title)
    }

    @Test fun nothingForAnEmptyListOrRemindersOff() {
        assertNull(DailySummary.build(emptyList(), sunday, Feb29Rule.Feb28))
        assertNull(DailySummary.build(listOf(p("A", 11, 9, on = false)), sunday, Feb29Rule.Feb28))
    }

    @Test fun sendsOncePerMorningBetweenNineAndNoon() {
        val day = "2026-10-30"
        assertFalse(DailySummary.shouldSendNow(LocalDateTime.of(2026, 10, 30, 8, 59), null))
        assertTrue(DailySummary.shouldSendNow(LocalDateTime.of(2026, 10, 30, 9, 0), null))
        assertFalse(DailySummary.shouldSendNow(LocalDateTime.of(2026, 10, 30, 9, 1), day)) // already sent
        assertFalse(DailySummary.shouldSendNow(LocalDateTime.of(2026, 10, 30, 12, 0), null)) // too late to be a morning summary
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 0), DailySummary.nextAt(LocalDateTime.of(2026, 10, 30, 7, 0), null))
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), DailySummary.nextAt(LocalDateTime.of(2026, 10, 30, 9, 0), day))
    }
}
