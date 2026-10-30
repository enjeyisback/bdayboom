package app.bdayboom.ui

import android.content.Context
import android.content.Intent
import app.bdayboom.data.EntryType
import app.bdayboom.data.Group
import app.bdayboom.data.Person
import app.bdayboom.data.Upcoming
import app.bdayboom.ui.components.EntryKind
import app.bdayboom.ui.components.PersonRowModel
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val locale get() = Locale.getDefault()
private val shortDate get() = DateTimeFormatter.ofPattern("MMM d", locale)

fun monthName(m: Int, full: Boolean = true): String =
    Month.of(m).getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, locale)

fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

fun shortDate(d: LocalDate): String = d.format(shortDate)

/** "14 March 1995", or "14 March" when the year is unknown. */
fun fullDate(p: Person): String = "${p.day} ${monthName(p.month)}" + (p.year?.let { " $it" } ?: "")

fun firstName(name: String) = name.trim().split(" ").first()

fun initials(name: String) = name.split(" ").mapNotNull { w -> w.firstOrNull { it.isLetter() } }.joinToString("")

fun EntryType.kind() = when (this) {
    EntryType.Birthday -> EntryKind.Birthday
    EntryType.Anniversary -> EntryKind.Anniversary
    EntryType.Event -> EntryKind.Event
}

/** "Turning 32", "6 years", or null. */
fun milestone(u: Upcoming): String? = u.turning?.let {
    when {
        u.eventLabel != null -> if (it == 1) "1 year" else "$it years"
        u.person.type == EntryType.Birthday -> "Turning $it"
        u.person.type == EntryType.Anniversary -> if (it == 1) "1 year" else "$it years"
        else -> null
    }
}

fun whenText(days: Int): String = when (days) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "in $days days"
}

fun rowModel(u: Upcoming, groups: List<Group>): PersonRowModel {
    val p = u.person
    val group = u.eventLabel ?: groups.firstOrNull { it.id == p.groupId }?.name
        ?: when (p.type) { EntryType.Anniversary -> "Anniversary"; EntryType.Event -> "Event"; else -> "No group" }
    val date = shortDate(u.next)
    val ms = milestone(u)
    val meta = listOfNotNull(date, ms).joinToString(" · ")
    val spoken = buildString {
        append(p.name).append(", ").append(group)
        if (ms != null) append(", ").append(ms.lowercase())
        append(" on ").append(u.next.dayOfMonth).append(' ').append(monthName(u.next.monthValue))
        append(", ").append(whenText(u.daysLeft))
    }
    val kind = if (u.eventLabel != null) EntryKind.Anniversary else p.type.kind()
    return PersonRowModel(p.id, p.name, meta, group, date, spoken, kind, u.daysLeft, p.photoPath)
}

/** Ready-made wishes in the BdayBoom voice. "{name}" becomes the person's name. The first of each is the default. */
val readyWishes: Map<EntryType, List<String>> = mapOf(
    EntryType.Birthday to listOf(
        "Happy birthday, {name}! 🎂 Hope this year brings you everything you're hoping for.",
        "Happy birthday, {name}! 🎉 Wishing you a day as fun as you are.",
        "Many happy returns of the day, {name}! 🎂 Have a brilliant year ahead.",
        "Happy birthday, {name}! 🎈 Eat the cake, skip the calories, enjoy every minute.",
        "Another year awesomer! 🎉 Happy birthday, {name}.",
        "Happy birthday, {name}! 🌟 Thanks for making life brighter. Have the best day.",
        "Wishing you health, laughs and a big slice of cake today, {name}! 🎂",
        "Janamdin ki dher saari shubhkaamnayein, {name}! 🎂 Khush raho, hamesha muskurate raho.",
        "Happy birthday, {name}! 🎉 Party hard, you've earned it.",
        "Cheers to you, {name}! 🥳 May this year be your best one yet.",
    ),
    EntryType.Anniversary to listOf(
        "Happy anniversary, {name}! 💕 Here's to many more years together.",
        "Happy anniversary, {name}! 🥂 Wishing you love, laughs and lots more adventures.",
        "Congratulations on another year together, {name}! 💕",
        "Happy anniversary, {name}! 🌹 Your story keeps getting better.",
        "Shaadi ki saalgirah mubarak ho, {name}! 💕 Yunhi saath bane rahein.",
    ),
    EntryType.Event to listOf(
        "Thinking of you today: {name}! 🎉",
        "Big day today, {name}! 🎉 Wishing you all the best.",
        "Congratulations, {name}! 🌟 Hope today is a great one.",
    ),
)

/** Birthdays use the first name; anniversaries often hold two names, so they keep the full one. */
fun wishName(p: Person): String = if (p.type == EntryType.Birthday) firstName(p.name) else p.name.trim()

fun fillWish(template: String, p: Person?): String = template.replace("{name}", p?.let(::wishName) ?: "friend")

/** Default wish text, used where there's no time to pick (e.g. Home's spotlight). */
fun wishText(p: Person): String = fillWish(readyWishes.getValue(p.type).first(), p)

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Send a wish"))
}

