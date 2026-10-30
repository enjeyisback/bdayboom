package app.bdayboom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bdayboom.data.AppSettings
import app.bdayboom.data.Dates
import app.bdayboom.data.EntryType
import app.bdayboom.data.Upcoming
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.Avatar
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.Tag
import app.bdayboom.ui.components.TagTone
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.fullDate
import app.bdayboom.ui.initials
import app.bdayboom.ui.kind
import app.bdayboom.ui.milestone
import app.bdayboom.ui.monthName
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Radius
import app.bdayboom.ui.theme.Space
import app.bdayboom.ui.weekday
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T?) : Load<T>
}

/** S05 person detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: String) {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val state by remember(id) { g.repo.person(id).map { Load.Ready(it) } }.collectAsState(initial = Load.Loading)
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    var confirmDelete by remember { mutableStateOf(false) }

    val person = (state as? Load.Ready)?.value
    Column(Modifier.fillMaxSize()) {
        ScreenTop(
            "Details",
            actions = {
                if (person != null) {
                    IconButton({ chrome.nav.navigate(Routes.edit(person.id)) }) { Icon(Icons.Outlined.Edit, "Edit", tint = c.text) }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = c.text) }
                }
            },
        )
        when {
            state is Load.Loading -> return@Column
            person == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.PersonOff, "Not on your list", "This person was removed or never saved.") {
                        AppButton("Back to list", { chrome.nav.popBackStack(Routes.HOME, false) }, kind = ButtonKind.Secondary)
                    }
                }
                return@Column
            }
        }
        val p = person!!
        val today = LocalDate.now()
        val u = Upcoming.of(p, today, settings.feb29)
        val group = groups.firstOrNull { it.id == p.groupId }?.name

        ScrollScreen {
            // Hero
            Column(
                Modifier
                    .padding(horizontal = Space.s4, vertical = Space.s2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
                    .padding(Space.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(initials(p.name), p.type.kind(), size = 96.dp, ring = if (AppTheme.isDark) c.accent else c.onHero, badge = if (u.turning != null) Icons.Outlined.Cake else null, photo = p.photoPath)
                Spacer(Modifier.height(Space.s3))
                Text(p.name, style = t.xl, color = c.onHero, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    if (group != null) Tag(group, TagTone.OnHero)
                    Tag(fullDate(p), TagTone.OnHero)
                }
                Spacer(Modifier.height(Space.s4))
                val countdown = when (u.daysLeft) {
                    0 -> "TODAY"
                    1 -> "TOMORROW"
                    else -> "IN ${u.daysLeft} DAYS"
                }
                Row(
                    Modifier.clip(RoundedCornerShape(Radius.pill)).background(if (AppTheme.isDark) c.bg else c.surface).padding(horizontal = Space.s4, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(Space.s2))
                    Text(countdown, style = t.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.text)
                    Spacer(Modifier.width(Space.s2))
                    Text("(${weekday(u.next)})", style = t.sm, color = c.textMuted)
                }
            }

            Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                AppButton("Send wish", { chrome.nav.navigate(Routes.wishes(p.id)) }, Modifier.weight(1f), icon = Icons.Outlined.Mail)
                AppButton("Edit", { chrome.nav.navigate(Routes.edit(p.id)) }, Modifier.weight(1f), ButtonKind.Secondary, Icons.Outlined.Edit)
            }

            // Facts
            SectionHeader(
                when (p.type) { EntryType.Birthday -> "Birthday"; EntryType.Anniversary -> "Anniversary"; EntryType.Event -> "Event" },
                icon = Icons.Outlined.Cake,
            )
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                if (p.year != null) {
                    val born = Dates.occurrenceIn(p.year, p.month, p.day, settings.feb29)
                    Fact(
                        when (p.type) { EntryType.Birthday -> "Born on"; EntryType.Anniversary -> "Since"; EntryType.Event -> "First on" } +
                            " ${weekday(born)}, ${fullDate(p)}",
                    )
                }
                Fact("Next: ${weekday(u.next)}, ${u.next.dayOfMonth} ${monthName(u.next.monthValue)} ${u.next.year}" +
                    if (p.month == 2 && p.day == 29 && u.next.dayOfMonth != 29) " (no 29 February this year)" else "")
                milestone(u)?.let { Fact(it) }
                if (!p.remindersEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsOff, null, Modifier.size(16.dp), tint = c.textMuted)
                        Spacer(Modifier.width(Space.s2))
                        Text("Reminders are off for this person", style = t.sm, color = c.textMuted)
                    }
                }
            }

            if (p.secondMonth != null && p.secondDay != null) {
                val secLabel = p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary"
                val secUpcoming = Upcoming.of(
                    p, today, settings.feb29,
                    eventLabel = secLabel,
                    customMonth = p.secondMonth,
                    customDay = p.secondDay,
                    customYear = p.secondYear,
                )
                SectionHeader(secLabel, icon = Icons.Outlined.FavoriteBorder)
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    val secFull = "${p.secondDay} ${monthName(p.secondMonth!!)}" + (p.secondYear?.let { " $it" } ?: "")
                    if (p.secondYear != null) {
                        val firstOcc = Dates.occurrenceIn(p.secondYear!!, p.secondMonth!!, p.secondDay!!, settings.feb29)
                        Fact("Since ${weekday(firstOcc)}, $secFull")
                    } else {
                        Fact(secFull)
                    }
                    Fact("Next: ${weekday(secUpcoming.next)}, ${secUpcoming.next.dayOfMonth} ${monthName(secUpcoming.next.monthValue)} ${secUpcoming.next.year}")
                    milestone(secUpcoming)?.let { Fact(it) }
                    val secCountdown = when (secUpcoming.daysLeft) {
                        0 -> "TODAY"
                        1 -> "TOMORROW"
                        else -> "IN ${secUpcoming.daysLeft} DAYS"
                    }
                    Text(secCountdown, style = t.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.accent)
                }
            }

            if (p.type == EntryType.Birthday) {
                SectionHeader("Star signs", icon = Icons.Outlined.Star)
                FlowRow(Modifier.padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Tag(Dates.zodiac(p.month, p.day), TagTone.Highlight, Icons.Outlined.Star)
                    if (p.year != null) Tag(Dates.chineseZodiac(p.year), TagTone.Celebrate)
                }
            }

            SectionHeader("Gift ideas & notes", icon = Icons.Outlined.CardGiftcard)
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4)) {
                if (p.notes.isBlank()) {
                    Text("Nothing yet. Jot down what they've mentioned wanting.", style = t.sm, color = c.textMuted)
                    Spacer(Modifier.height(Space.s3))
                    AppButton("Add notes", { chrome.nav.navigate(Routes.edit(p.id)) }, kind = ButtonKind.Secondary)
                } else {
                    Text(p.notes, style = t.base, color = c.text)
                }
            }
            Spacer(Modifier.height(Space.s8))
        }
    }

    if (confirmDelete && person != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("Remove ${person.name}?", style = t.lg, color = c.text) },
            text = { Text("Their reminders stop too. You can undo this for a few seconds.", style = t.sm, color = c.textMuted) },
            confirmButton = {
                AppButton("Remove", {
                    confirmDelete = false
                    // App-wide scope: this screen closes below, and its own scope would cancel the Undo snackbar.
                    chrome.scope.launch {
                        g.repo.softDelete(person.id)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { chrome.nav.popBackStack() }
                        val r = chrome.snackbar.showSnackbar("${person.name} removed", actionLabel = "Undo")
                        if (r == SnackbarResult.ActionPerformed) g.repo.undoDelete(person.id)
                    }
                }, kind = ButtonKind.Danger)
            },
            dismissButton = { AppButton("Cancel", { confirmDelete = false }, kind = ButtonKind.Text) },
        )
    }
}

@Composable
private fun Fact(text: String) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(Space.s3))
        Text(text, style = AppTheme.type.base, color = c.text)
    }
}
