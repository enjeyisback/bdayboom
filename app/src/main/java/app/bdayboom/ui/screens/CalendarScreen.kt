package app.bdayboom.ui.screens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.data.AppSettings
import app.bdayboom.data.Dates
import app.bdayboom.data.Upcoming
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.DotKind
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.MonthGrid
import app.bdayboom.ui.components.MonthHeader
import app.bdayboom.ui.components.PersonRow
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.monthName
import app.bdayboom.ui.rowModel
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import app.bdayboom.ui.weekday
import java.time.LocalDate
import java.time.YearMonth

private val milestones = setOf(1, 16, 18, 21, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)

/** S08 month view. */
@Composable
fun CalendarScreen() {
    val g = LocalContext.current.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val people by g.repo.people.collectAsState(initial = emptyList())
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    val today = remember { LocalDate.now() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)

    // Each person's occurrence inside the shown month (Feb 29 follows the user's rule).
    val inMonth = people.flatMap { p ->
        val occ1 = Dates.occurrenceIn(month.year, p.month, p.day, settings.feb29)
        val l1 = if (YearMonth.from(occ1) == month) listOf(Triple(p, occ1, null as String?)) else emptyList()
        val l2 = if (p.secondMonth != null && p.secondDay != null) {
            val occ2 = Dates.occurrenceIn(month.year, p.secondMonth, p.secondDay, settings.feb29)
            if (YearMonth.from(occ2) == month) listOf(Triple(p, occ2, p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary")) else emptyList()
        } else emptyList()
        l1 + l2
    }
    val dots = inMonth.groupBy({ it.second.dayOfMonth }) { (p, occ, label) ->
        val yr = if (label != null) p.secondYear else p.year
        if (Dates.turning(yr, occ) in milestones) DotKind.Milestone else DotKind.Upcoming
    }
    val onDay = inMonth.filter { it.second == selected }.map { (p, occ, label) ->
        Upcoming.of(
            p, selected, settings.feb29,
            eventLabel = label,
            customMonth = occ.monthValue,
            customDay = occ.dayOfMonth,
            customYear = if (label != null) p.secondYear else p.year,
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Calendar", "Month view", tab = true)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(vertical = Space.s3)) {
                    MonthHeader(
                        month, inMonth.size,
                        onPrev = { monthText = month.minusMonths(1).toString() },
                        onNext = { monthText = month.plusMonths(1).toString() },
                    )
                    MonthGrid(month, dots, selected, today, { selectedText = it.toString() })
                }
            }
            item {
                Text(
                    "${weekday(selected)}, ${selected.dayOfMonth} ${monthName(selected.monthValue)}".uppercase(),
                    style = t.micro, color = c.accent,
                    modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s6, bottom = Space.s2),
                )
            }
            if (onDay.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.EventAvailable, "Nothing on this day", "Tap another day, or add someone whose day this is.") {
                        AppButton("Add on this day", { chrome.nav.navigate(Routes.edit(month = selected.monthValue, day = selected.dayOfMonth)) }, kind = ButtonKind.Secondary)
                    }
                }
            }
            items(onDay, key = { "${it.person.id}-${it.eventLabel ?: "primary"}" }) { u ->
                // daysLeft is relative to the selected day here; show the real countdown from today instead
                val real = if (u.eventLabel != null) {
                    Upcoming.of(
                        u.person, today, settings.feb29,
                        eventLabel = u.eventLabel,
                        customMonth = u.person.secondMonth,
                        customDay = u.person.secondDay,
                        customYear = u.person.secondYear,
                    )
                } else {
                    Upcoming.of(u.person, today, settings.feb29)
                }
                PersonRow(rowModel(real, groups), { chrome.nav.navigate(Routes.person(u.person.id)) })
            }
            item { Spacer(Modifier.height(Space.s8)) }
        }
    }
}
