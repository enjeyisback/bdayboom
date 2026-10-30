package app.bdayboom.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MusicNote
import app.bdayboom.reminders.Notifier
import androidx.compose.material.icons.outlined.SystemUpdate
import app.bdayboom.update.UpdateManager
import app.bdayboom.update.UpdateState
import app.bdayboom.reminders.OemAutostart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.BuildConfig
import app.bdayboom.data.AppSettings
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.ThemeMode
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.AppChip
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.ReminderRow
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.SettingsItem
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val offsetChoices = listOf(
    0 to ("On the day" to "A wish first thing"),
    1 to ("1 day before" to "Time to sort a card"),
    3 to ("3 days before" to "A quick heads-up"),
    7 to ("1 week before" to "Plan something"),
    14 to ("2 weeks before" to "Shop for a gift"),
)

/** S15 settings. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    val offsets by g.repo.offsets.collectAsState(initial = emptyList())
    var timeOpen by remember { mutableStateOf(false) }
    var licencesOpen by remember { mutableStateOf(false) }

    // Re-read permission state whenever we come back from system settings.
    val resumeTick = rememberResumeTick()
    var granted by remember { mutableIntStateOf(0) }
    val canNotify = remember(resumeTick, granted) { g.notifier.canNotify() }
    val canExact = remember(resumeTick) { g.scheduler.canExact() }
    val autostart = remember(resumeTick) { OemAutostart.isEnabled(context) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted++ }

    val timeText = LocalTime.of(settings.reminderHour, settings.reminderMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Settings", "Reminders and look")
        ScrollScreen {
            SectionHeader("Reminders", icon = Icons.Outlined.NotificationsActive)
            SettingsItem("Reminder time", timeText, Icons.Outlined.AccessTime) { timeOpen = true }
            Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text("Remind me", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1, top = Space.s2))
                offsetChoices.forEach { (days, labels) ->
                    ReminderRow(labels.first, labels.second, days in offsets, { scope.launch { g.repo.setOffset(days, days !in offsets) } })
                }
                if (offsets.isEmpty()) Text("No reminders are on. You won't be notified.", style = t.sm, color = c.danger)
                Text("Birthdays on 29 February, in other years", style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1, top = Space.s3))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    AppChip("28 February", settings.feb29 == Feb29Rule.Feb28, { scope.launch { g.settings.setFeb29(Feb29Rule.Feb28); g.scheduler.rescheduleAsync() } })
                    AppChip("1 March", settings.feb29 == Feb29Rule.Mar1, { scope.launch { g.settings.setFeb29(Feb29Rule.Mar1); g.scheduler.rescheduleAsync() } })
                }
            }

            Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s2)) {
                ReminderRow(
                    "Keep today's reminders until I tap Wished", "Clearing notifications by habit won't lose a birthday",
                    settings.keepUntilWished, { scope.launch { g.settings.setKeepUntilWished(!settings.keepUntilWished) } }, useSwitch = true,
                )
            }
            SettingsItem("Reminder sound", "Pick a sound, or make it ring like an alarm", Icons.Outlined.MusicNote) {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL),
                )
            }

            SectionHeader("Can reminders reach you?", icon = Icons.Outlined.Notifications)
            SettingsItem("Reminder check-up", "Check everything in one place and send a test", Icons.Outlined.HealthAndSafety) { chrome.nav.navigate(Routes.CHECKUP) }
            SettingsItem("Notifications", if (canNotify) "On" else "Off: tap to turn on", Icons.Outlined.Notifications) {
                if (Build.VERSION.SDK_INT >= 33 && !canNotify) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SettingsItem("On-time alarms", if (canExact) "Allowed: reminders arrive on the minute" else "Not allowed: reminders may be up to an hour late", Icons.Outlined.Alarm) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
            if (OemAutostart.isXiaomi) {
                SettingsItem(
                    "Autostart",
                    when (autostart) {
                        true -> "On: reminders come back after a restart"
                        false -> "Off: reminders stop after a restart. Tap to fix"
                        null -> "Tap to check it's on, so reminders survive a restart"
                    },
                    Icons.Outlined.RestartAlt,
                ) { context.startActivity(OemAutostart.settingsIntent(context)) }
            }
            SettingsItem("Reminders not arriving?", "Battery savers on some phones block them", Icons.Outlined.HelpOutline) { chrome.nav.navigate(Routes.HELP) }

            SectionHeader("Look", icon = Icons.Outlined.Palette)
            FlowRow(Modifier.padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                listOf(ThemeMode.System to "Match phone", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark").forEach { (m, l) ->
                    AppChip(l, settings.theme == m, { scope.launch { g.settings.setTheme(m) } })
                }
            }

            SectionHeader("About", icon = Icons.Outlined.Info)
            val updates = remember { UpdateManager.get(context) }
            val updateState by updates.state.collectAsState()
            SettingsItem(
                "Version ${BuildConfig.VERSION_NAME}",
                when {
                    !updates.enabled -> "Updates come from the app store"
                    else -> when (val s = updateState) {
                        UpdateState.Idle -> "Check for updates"
                        UpdateState.Checking -> "Checking…"
                        UpdateState.UpToDate -> "You're on the latest version"
                        is UpdateState.Available -> "Version ${s.info.version} is ready. Tap to update"
                        is UpdateState.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                        is UpdateState.Failed -> s.message
                    }
                },
                Icons.Outlined.SystemUpdate,
                enabled = updates.enabled,
            ) {
                when (val s = updateState) {
                    is UpdateState.Available -> updates.installDownloaded(s.info)
                    else -> updates.check()
                }
            }
            SettingsItem("Open-source licences", "Fonts and libraries") { licencesOpen = true }
            Spacer(Modifier.height(Space.s8))
        }
    }

    if (timeOpen) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            containerColor = c.surface,
            title = { Text("Reminder time", style = t.lg, color = c.text) },
            text = {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.surface2,
                        selectorColor = c.accent,
                        timeSelectorSelectedContainerColor = c.accentSoft,
                        timeSelectorSelectedContentColor = c.onAccentSoft,
                        timeSelectorUnselectedContainerColor = c.surface2,
                        periodSelectorSelectedContainerColor = c.accentSoft,
                        periodSelectorSelectedContentColor = c.onAccentSoft,
                    ),
                )
            },
            confirmButton = {
                AppButton("Set", {
                    timeOpen = false
                    scope.launch { g.settings.setReminderTime(state.hour, state.minute); g.scheduler.rescheduleAsync() }
                })
            },
            dismissButton = { AppButton("Cancel", { timeOpen = false }, kind = ButtonKind.Text) },
        )
    }

    if (licencesOpen) {
        AlertDialog(
            onDismissRequest = { licencesOpen = false },
            containerColor = c.surface,
            title = { Text("Open-source licences", style = t.lg, color = c.text) },
            text = {
                Text(
                    "Fonts: Plus Jakarta Sans, Playfair Display and Manrope, under the SIL Open Font License 1.1.\n\n" +
                        "Icons: Material Symbols, Apache License 2.0.\n\n" +
                        "Libraries: AndroidX and Jetpack Compose (Apache 2.0), SQLDelight (Apache 2.0).",
                    style = t.sm, color = c.textMuted,
                )
            },
            confirmButton = { AppButton("Close", { licencesOpen = false }, kind = ButtonKind.Text) },
        )
    }
}
