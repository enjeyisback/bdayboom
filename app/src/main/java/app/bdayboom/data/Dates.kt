package app.bdayboom.data

import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

/** What a Feb 29 birthday becomes in a non-leap year. */
enum class Feb29Rule { Feb28, Mar1 }

/**
 * Date maths for floating dates (month + day, optional year). Pure, no Android: unit-tested in DatesTest.
 */
object Dates {

    fun isValid(month: Int, day: Int): Boolean =
        month in 1..12 && day in 1..maxDay(month)

    /** Longest a month can be; February allows 29 so leap-day birthdays can be stored. */
    fun maxDay(month: Int): Int = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun occurrenceIn(year: Int, month: Int, day: Int, rule: Feb29Rule): LocalDate =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
            if (rule == Feb29Rule.Feb28) LocalDate.of(year, 2, 28) else LocalDate.of(year, 3, 1)
        } else {
            LocalDate.of(year, month, day)
        }

    /** First occurrence on or after [from]. */
    fun nextOccurrence(month: Int, day: Int, from: LocalDate, rule: Feb29Rule): LocalDate {
        val thisYear = occurrenceIn(from.year, month, day, rule)
        return if (!thisYear.isBefore(from)) thisYear else occurrenceIn(from.year + 1, month, day, rule)
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()

    /** Age reached (or years married) on [occurrence]; null when the year is unknown or in the future. */
    fun turning(year: Int?, occurrence: LocalDate): Int? =
        year?.let { occurrence.year - it }?.takeIf { it >= 0 }

    private val westernSigns = listOf(
        // sign to the last day it covers, in (month, day)
        Triple("Capricorn", 1, 19), Triple("Aquarius", 2, 18), Triple("Pisces", 3, 20),
        Triple("Aries", 4, 19), Triple("Taurus", 5, 20), Triple("Gemini", 6, 20),
        Triple("Cancer", 7, 22), Triple("Leo", 8, 22), Triple("Virgo", 9, 22),
        Triple("Libra", 10, 22), Triple("Scorpio", 11, 21), Triple("Sagittarius", 12, 21),
    )

    fun zodiac(month: Int, day: Int): String {
        val (name, m, last) = westernSigns[month - 1]
        return if (month == m && day <= last) name else westernSigns[month % 12].first
    }

    private val animals = listOf("Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig")
    private val elements = listOf("Metal", "Metal", "Water", "Water", "Wood", "Wood", "Fire", "Fire", "Earth", "Earth")

    /**
     * Chinese zodiac by calendar year. Approximate: people born in January or early February
     * before the lunar new year belong to the previous year's animal.
     */
    fun chineseZodiac(year: Int): String {
        val animal = animals[Math.floorMod(year - 1900, 12)]
        val element = elements[Math.floorMod(year, 10)]
        return "$animal · $element"
    }
}
