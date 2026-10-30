package app.bdayboom.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import app.bdayboom.data.Photos
import app.bdayboom.ui.components.Avatar
import app.bdayboom.ui.initials
import app.bdayboom.ui.kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bdayboom.data.Dates
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.AppChip
import app.bdayboom.ui.components.AppTextField
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.ReminderRow
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.SelectField
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.monthName
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** S06 add / edit, with S07 type picker inline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(id: String?, presetMonth: Int?, presetDay: Int?) {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    val thisYear = LocalDate.now().year

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var type by rememberSaveable { mutableStateOf(EntryType.Birthday) }
    var name by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf(presetMonth ?: LocalDate.now().monthValue) }
    var day by rememberSaveable { mutableStateOf(presetDay ?: LocalDate.now().dayOfMonth) }
    var withYear by rememberSaveable { mutableStateOf(true) }
    var year by rememberSaveable { mutableStateOf(thisYear - 30) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var reminders by rememberSaveable { mutableStateOf(true) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Person?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var hasSecondDate by rememberSaveable { mutableStateOf(false) }
    var secondLabel by rememberSaveable { mutableStateOf("Anniversary") }
    var secondMonth by rememberSaveable { mutableStateOf(1) }
    var secondDay by rememberSaveable { mutableStateOf(1) }
    var secondWithYear by rememberSaveable { mutableStateOf(false) }
    var secondYear by rememberSaveable { mutableStateOf(thisYear - 5) }
    var secondDateError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var newGroupOpen by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            g.repo.person(id).first()?.let { p ->
                original = p
                type = p.type; name = p.name; month = p.month; day = p.day
                withYear = p.year != null; year = p.year ?: (thisYear - 30)
                hasSecondDate = p.secondMonth != null && p.secondDay != null
                secondLabel = p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary"
                secondMonth = p.secondMonth ?: 1
                secondDay = p.secondDay ?: 1
                secondWithYear = p.secondYear != null
                secondYear = p.secondYear ?: (thisYear - 5)
                groupId = p.groupId; notes = p.notes; reminders = p.remindersEnabled; photo = p.photoPath
            }
            loaded = true
        } else if (id != null) {
            original = g.repo.person(id).first()
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoBusy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { Photos.importFrom(context, uri) }.getOrNull() }
            photoBusy = false
            if (path != null) photo = path else chrome.snackbar.showSnackbar("Couldn't use that picture. Try another.")
        }
    }

    fun save() {
        if (saving) return // a second tap before the button shows "loading" must not save a copy
        nameError = if (name.isBlank()) "Add a name" else null
        dateError = when {
            !Dates.isValid(month, day) -> "${monthName(month)} has no day $day"
            withYear && month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong()) -> "$year had no 29 February"
            withYear && type == EntryType.Birthday && LocalDate.of(year, month, minOf(day, 28)).isAfter(LocalDate.now()) -> "That date is in the future"
            else -> null
        }
        if (hasSecondDate) {
            secondDateError = when {
                !Dates.isValid(secondMonth, secondDay) -> "${monthName(secondMonth)} has no day $secondDay"
                secondWithYear && secondMonth == 2 && secondDay == 29 && !java.time.Year.isLeap(secondYear.toLong()) -> "$secondYear had no 29 February"
                else -> null
            }
        } else {
            secondDateError = null
        }
        if (nameError != null || dateError != null || secondDateError != null) return
        saving = true
        val base = original ?: Person(id = "", type = type, name = name, month = month, day = day, year = null, groupId = null, notes = "", remindersEnabled = true)
        val toSave = base.copy(
            type = type, name = name.trim(), month = month, day = day, year = if (withYear) year else null,
            secondLabel = if (hasSecondDate) secondLabel.trim().ifBlank { "Anniversary" } else null,
            secondMonth = if (hasSecondDate) secondMonth else null,
            secondDay = if (hasSecondDate) secondDay else null,
            secondYear = if (hasSecondDate && secondWithYear) secondYear else null,
            groupId = groupId, notes = notes.trim(), remindersEnabled = reminders, photoPath = photo,
        )
        val confirmation = if (original == null) "${toSave.name} is in. We'll remind you on the day." else "Saved"
        // App-wide scope: this screen closes below, and its own scope would cancel the confirmation.
        chrome.scope.launch {
            g.repo.save(toSave)
            withContext(Dispatchers.Main.immediate) { // navigation must happen on the main thread
                if (Build.VERSION.SDK_INT >= 33 && !g.notifier.canNotify()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                chrome.nav.popBackStack()
            }
            chrome.snackbar.showSnackbar(confirmation)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop(if (id == null) "New celebration" else "Edit", if (id == null) "Add someone" else name.ifBlank { null }) {
            IconButton({ chrome.nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Cancel", tint = c.text) }
        }
        if (!loaded) return@Column
        ScrollScreen {
            Column(Modifier.imePadding()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = Space.s4),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    Avatar(initials(name.ifBlank { "?" }), type.kind(), size = 96.dp, ring = c.accent, photo = photo)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        AppButton(
                            if (photo == null) "Add photo" else "Change photo",
                            { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            kind = ButtonKind.Secondary, icon = Icons.Outlined.AddAPhoto, loading = photoBusy,
                        )
                        if (photo != null) AppButton("Remove", { photo = null }, kind = ButtonKind.Text)
                    }
                }
                SectionHeader("Who are we celebrating?", icon = Icons.Outlined.FavoriteBorder)
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    Column {
                        Text("Type", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            listOf(EntryType.Birthday to "Birthday", EntryType.Anniversary to "Anniversary", EntryType.Event to "Event").forEach { (k, l) ->
                                AppChip(l, type == k, { type = k })
                            }
                        }
                    }
                    AppTextField(
                        when (type) { EntryType.Birthday -> "Name or nickname"; EntryType.Anniversary -> "Couple or names"; EntryType.Event -> "What is it?" },
                        name, { name = it; nameError = null },
                        placeholder = when (type) { EntryType.Birthday -> "e.g. Alex Morgan"; EntryType.Anniversary -> "e.g. Priya & Sam"; EntryType.Event -> "e.g. Graduation" },
                        error = nameError,
                    )
                    Column {
                        Text("Group", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            AppChip("None", groupId == null, { groupId = null })
                            groups.forEach { gr -> AppChip(gr.name, groupId == gr.id, { groupId = gr.id }) }
                            AppChip("New group", false, { newGroupOpen = true }, icon = Icons.Outlined.Add)
                        }
                    }
                }

                SectionHeader("The big date", icon = Icons.Outlined.Cake)
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Picker("Month", monthName(month, full = false), (1..12).map { it to monthName(it) }, { month = it; day = minOf(day, Dates.maxDay(it)); dateError = null }, Modifier.weight(1.3f))
                        Picker("Day", "$day", (1..Dates.maxDay(month)).map { it to "$it" }, { day = it; dateError = null }, Modifier.weight(1f))
                        if (withYear) {
                            Picker("Year", "$year", ((thisYear + (if (type == EntryType.Event) 5 else 0)) downTo 1900).map { it to "$it" }, { year = it; dateError = null }, Modifier.weight(1.3f))
                        }
                    }
                    if (dateError != null) Text(dateError!!, style = t.xs, color = c.danger)
                    ReminderRow(
                        "I know the year", if (type == EntryType.Birthday) "Shows their age and star signs" else "Shows how many years",
                        withYear, { withYear = !withYear; dateError = null }, useSwitch = true,
                    )
                }

                SectionHeader("Second date", icon = Icons.Outlined.Event)
                if (!hasSecondDate) {
                    Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth()) {
                        AppButton(
                            "+ Add second date (Anniversary / Event)",
                            { hasSecondDate = true },
                            kind = ButtonKind.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4),
                        verticalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text("Second date", style = t.base.copy(fontWeight = FontWeight.Bold), color = c.text)
                            AppButton("Remove", { hasSecondDate = false; secondDateError = null }, kind = ButtonKind.Text)
                        }
                        Text("Label", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            listOf("Anniversary", "Work anniversary", "Wedding", "Custom").forEach { lbl ->
                                val isCustom = lbl == "Custom" && secondLabel !in listOf("Anniversary", "Work anniversary", "Wedding")
                                val isSelected = if (lbl == "Custom") isCustom else secondLabel == lbl
                                AppChip(lbl, isSelected, {
                                    if (lbl != "Custom") secondLabel = lbl else if (!isCustom) secondLabel = "Milestone"
                                })
                            }
                        }
                        if (secondLabel !in listOf("Anniversary", "Work anniversary", "Wedding")) {
                            AppTextField("Custom label", secondLabel, { secondLabel = it }, placeholder = "e.g. Engagement")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            Picker("Month", monthName(secondMonth, full = false), (1..12).map { it to monthName(it) }, { secondMonth = it; secondDay = minOf(secondDay, Dates.maxDay(it)); secondDateError = null }, Modifier.weight(1.3f))
                            Picker("Day", "$secondDay", (1..Dates.maxDay(secondMonth)).map { it to "$it" }, { secondDay = it; secondDateError = null }, Modifier.weight(1f))
                            if (secondWithYear) {
                                Picker("Year", "$secondYear", ((thisYear + 5) downTo 1900).map { it to "$it" }, { secondYear = it; secondDateError = null }, Modifier.weight(1.3f))
                            }
                        }
                        if (secondDateError != null) Text(secondDateError!!, style = t.xs, color = c.danger)
                        ReminderRow(
                            "I know the year", "Shows how many years celebrated",
                            secondWithYear, { secondWithYear = !secondWithYear; secondDateError = null }, useSwitch = true,
                        )
                    }
                }

                SectionHeader("Gift ideas & notes", icon = Icons.Outlined.CardGiftcard)
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4)) {
                    AppTextField("Notes", notes, { notes = it }, placeholder = "What have they mentioned wanting?", minLines = 3)
                }

                SectionHeader("Reminders", icon = Icons.Outlined.NotificationsActive)
                Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth()) {
                    ReminderRow("Remind me", "At the time and days set in Settings", reminders, { reminders = !reminders }, useSwitch = true)
                }

                Spacer(Modifier.height(Space.s6))
                AppButton(
                    if (id == null) "Save and remind me" else "Save changes", ::save,
                    Modifier.padding(horizontal = Space.s4).fillMaxWidth(), ButtonKind.Cta, Icons.Outlined.Celebration, loading = saving,
                )
                Spacer(Modifier.height(Space.s8))
            }
        }
    }

    if (newGroupOpen) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newGroupOpen = false },
            containerColor = c.surface,
            title = { Text("New group", style = t.lg, color = c.text) },
            text = { AppTextField("Name", groupName, { groupName = it }, placeholder = "e.g. School friends") },
            confirmButton = {
                AppButton("Create", {
                    if (groupName.isNotBlank()) scope.launch { groupId = g.repo.addGroup(groupName).id; newGroupOpen = false }
                }, enabled = groupName.isNotBlank())
            },
            dismissButton = { AppButton("Cancel", { newGroupOpen = false }, kind = ButtonKind.Text) },
        )
    }
}

@Composable
private fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SelectField(label, value, { open = true })
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 320.dp), containerColor = AppTheme.colors.surface) {
            options.forEach { (v, text) ->
                DropdownMenuItem(text = { Text(text, style = AppTheme.type.base, color = AppTheme.colors.text) }, onClick = { onPick(v); open = false })
            }
        }
    }
}
