package app.bdayboom.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.data.AppSettings
import app.bdayboom.data.EntryType
import app.bdayboom.data.Upcoming
import app.bdayboom.graph
import app.bdayboom.reminders.OemAutostart
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.produceState
import androidx.core.content.ContextCompat
import app.bdayboom.data.ContactsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.Banner
import app.bdayboom.ui.components.BannerKind
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.ChipRow
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.PersonRow
import app.bdayboom.ui.components.SearchField
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.SpotlightCard
import app.bdayboom.ui.components.StatBanner
import app.bdayboom.ui.monthName
import app.bdayboom.ui.rowModel
import app.bdayboom.ui.shareWish
import app.bdayboom.ui.theme.Space
import app.bdayboom.ui.whenText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** S01 upcoming list, with S02 search and S03 group filter inline. */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val people by g.repo.people.collectAsState(initial = null)
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    var query by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var byName by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val tick = rememberResumeTick()
    val remindersOk = remember(tick) { g.notifier.canNotify() }
    val autostartOff = remember(tick) { OemAutostart.isEnabled(context) == false }
    // Contacts gained or changed a birthday since the last import (fixes.md #2). Only if access was already granted.
    val newInContacts by produceState(0, tick, people) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        value = if (!granted || people == null) 0 else withContext(Dispatchers.IO) {
            runCatching { ContactsReader.newOrChanged(context, g.repo.importedContacts()) }.getOrDefault(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("BdayBoom", "Coming up", tab = true)
        val all = people
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.Cake, "Nobody here yet", "Add the people you love, or bring them in from your contacts. We'll make sure no birthday sneaks past you.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        AppButton("Add birthday", { chrome.nav.navigate(Routes.edit()) })
                        AppButton("Import", { chrome.nav.navigate(Routes.IMPORT) }, kind = ButtonKind.Secondary)
                    }
                }
            }
            return@Column
        }

        val upcoming = all.map { Upcoming.of(it, today, settings.feb29) }.sortedWith(compareBy({ it.daysLeft }, { it.person.name.lowercase() }))
        val chipLabels = listOf("All (${all.size})") + groups.map { gr -> "${gr.name} (${all.count { it.groupId == gr.id }})" }
        val selectedLabel = groupFilter?.let { id -> groups.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { chipLabels[it + 1] } } ?: chipLabels[0]
        val shown = upcoming.filter { u ->
            (groupFilter == null || u.person.groupId == groupFilter) &&
                (query.isBlank() || u.person.name.contains(query.trim(), ignoreCase = true))
        }
        val todays = shown.filter { it.isToday }
        val week = if (byName) emptyList() else shown.filter { it.daysLeft in 1..7 }
        val later = if (byName) emptyMap() else shown.filter { it.daysLeft > 7 }.groupBy { YearMonth.from(it.next) }
        val alphabetical = if (byName) shown.filter { !it.isToday }.sortedBy { it.person.name.lowercase() } else emptyList()

        fun delete(u: Upcoming) {
            scope.launch {
                g.repo.softDelete(u.person.id)
                val r = chrome.snackbar.showSnackbar("${u.person.name} removed", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) g.repo.undoDelete(u.person.id)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(Space.s2))
                SearchField(query, { query = it }, "Search by name", {
                    byName = !byName
                    scope.launch { chrome.snackbar.showSnackbar(if (byName) "Sorted A to Z" else "Sorted by next date") }
                })
            }
            val reminderBanner = when {
                !remindersOk -> "Notifications are off. Fix it" to BannerKind.Warning
                autostartOff -> "Autostart is off: reminders stop after a restart. Fix it" to BannerKind.Warning
                !settings.checkupSeen -> "Take 1 minute to make sure reminders always arrive" to BannerKind.Info
                else -> null
            }
            reminderBanner?.let { (text, kind) ->
                item { Spacer(Modifier.height(Space.s3)); Banner(text, kind) { chrome.nav.navigate(Routes.CHECKUP) } }
            }
            if (newInContacts > 0) {
                item {
                    Spacer(Modifier.height(Space.s3))
                    Banner(
                        if (newInContacts == 1) "1 birthday to add or update from Contacts" else "$newInContacts birthdays to add or update from Contacts",
                        BannerKind.Info,
                    ) { chrome.nav.navigate(Routes.IMPORT) }
                }
            }
            item {
                Spacer(Modifier.height(Space.s3))
                val next = upcoming.firstOrNull { !it.isToday }
                StatBanner(
                    "${all.size} ${if (all.size == 1) "person" else "people"} to celebrate",
                    next?.let { "Next ${whenText(it.daysLeft)}" } ?: "All set",
                    Icons.Outlined.Celebration,
                )
            }
            item {
                Spacer(Modifier.height(Space.s2))
                ChipRow(chipLabels, selectedLabel, { label ->
                    val i = chipLabels.indexOf(label)
                    groupFilter = if (i <= 0) null else groups[i - 1].id
                })
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.SearchOff, "No one matches", if (query.isNotBlank()) "Nobody called \"$query\" in this view." else "No one in this group yet.") {
                        AppButton("Show everyone", { query = ""; groupFilter = null }, kind = ButtonKind.Secondary)
                    }
                }
            }
            items(todays, key = { "today-" + it.person.id }) { u ->
                Spacer(Modifier.height(Space.s4))
                val turning = when {
                    u.turning != null && u.person.type == EntryType.Birthday -> "Turning ${u.turning}"
                    u.turning != null -> "${u.turning} years"
                    u.person.type == EntryType.Anniversary -> "Anniversary today"
                    else -> "Today"
                }
                SpotlightCard(
                    u.person.name, turning,
                    if (u.person.type == EntryType.Birthday) "It's their day! A quick wish goes a long way." else "A day worth marking. Say something nice.",
                    onWish = { shareWish(context, u.person) },
                    onGift = { chrome.nav.navigate(Routes.person(u.person.id)) },
                    secondaryLabel = "Open",
                    secondaryIcon = Icons.Outlined.OpenInNew,
                    photo = u.person.photoPath,
                )
            }
            if (week.isNotEmpty()) {
                item { SectionHeader("This week", icon = Icons.Outlined.Cake, trailing = count(week.size)) }
                items(week, key = { it.person.id }) { u ->
                    PersonRow(rowModel(u, groups), { chrome.nav.navigate(Routes.person(u.person.id)) }, { delete(u) }, Modifier.animateItem())
                }
            }
            later.forEach { (ym, list) ->
                item(key = "h-$ym") {
                    val title = monthName(ym.monthValue) + if (ym.year != today.year) " ${ym.year}" else ""
                    SectionHeader(title, icon = Icons.Outlined.CalendarMonth, trailing = count(list.size))
                }
                items(list, key = { it.person.id }) { u ->
                    PersonRow(rowModel(u, groups), { chrome.nav.navigate(Routes.person(u.person.id)) }, { delete(u) }, Modifier.animateItem())
                }
            }
            if (alphabetical.isNotEmpty()) {
                item { SectionHeader("A to Z", icon = Icons.Outlined.Cake, trailing = count(alphabetical.size)) }
                items(alphabetical, key = { it.person.id }) { u ->
                    PersonRow(rowModel(u, groups), { chrome.nav.navigate(Routes.person(u.person.id)) }, { delete(u) }, Modifier.animateItem())
                }
            }
            item { Spacer(Modifier.height(Space.s8)) }
        }
    }
}

private fun count(n: Int) = if (n == 1) "1 celebration" else "$n celebrations"
