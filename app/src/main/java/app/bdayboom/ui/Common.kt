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
    when (u.person.type) {
        EntryType.Birthday -> "Turning $it"
        EntryType.Anniversary -> if (it == 1) "1 year" else "$it years"
        EntryType.Event -> null
    }
}

fun whenText(days: Int): String = when (days) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "in $days days"
}

fun rowModel(u: Upcoming, groups: List<Group>): PersonRowModel {
    val p = u.person
    val group = groups.firstOrNull { it.id == p.groupId }?.name
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
    return PersonRowModel(p.id, p.name, meta, group, date, spoken, p.type.kind(), u.daysLeft, p.photoPath)
}

/** Wish text in the BdayBoom voice. */
fun wishText(p: Person): String = when (p.type) {
    EntryType.Birthday -> "Happy birthday, ${firstName(p.name)}! 🎂 Hope this year brings you everything you're hoping for."
    EntryType.Anniversary -> "Happy anniversary, ${p.name}! 💕 Here's to many more years together."
    EntryType.Event -> "Thinking of you today: ${p.name}! 🎉"
}

fun shareWish(context: Context, p: Person) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, wishText(p))
    context.startActivity(Intent.createChooser(send, "Send a wish"))
}
