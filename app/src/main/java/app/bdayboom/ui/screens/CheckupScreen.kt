package app.bdayboom.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.bdayboom.data.AppSettings
import app.bdayboom.graph
import app.bdayboom.reminders.OemAutostart
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Health { Ok, Warn, Fail }

private data class Check(
    val title: String,
    val detail: String,
    val health: Health,
    val icon: ImageVector,
    val fixLabel: String? = null,
    val fix: (() -> Unit)? = null,
)

/** Reminder check-up: proves reminders will reach this phone, the thing people care about most. */
@Composable
fun CheckupScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val tick = rememberResumeTick()
    var asked by remember { mutableIntStateOf(0) }
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    val offsets by g.repo.offsets.collectAsState(initial = listOf(0))
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked++ }

    LaunchedEffect(Unit) { g.settings.setCheckupSeen() }

    fun open(intent: Intent) = runCatching { context.startActivity(intent) }

    val checks = remember(tick, asked, offsets) {
        val canNotify = g.notifier.canNotify()
        val exact = g.scheduler.canExact()
        val auto = OemAutostart.isEnabled(context)
        val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        buildList {
            add(
                Check(
                    "Notifications", if (canNotify) "Allowed" else "Blocked: reminders can't show",
                    if (canNotify) Health.Ok else Health.Fail, Icons.Outlined.NotificationsActive, "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                },
            )
            add(
                Check(
                    "On-time alarms",
                    if (exact) "Reminders arrive on the minute" else "Not allowed: reminders may be up to an hour late",
                    if (exact) Health.Ok else Health.Warn, Icons.Outlined.Alarm, "Allow",
                ) {
                    if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                },
            )
            if (OemAutostart.isXiaomi) {
                add(
                    Check(
                        "Autostart",
                        when (auto) { true -> "On: reminders come back after a restart"; false -> "Off: reminders stop after a restart"; null -> "Couldn't check. Make sure it's on." },
                        when (auto) { true -> Health.Ok; false -> Health.Fail; null -> Health.Warn }, Icons.Outlined.RestartAlt, "Open",
                    ) { open(OemAutostart.settingsIntent(context)) },
                )
            }
            add(
                Check(
                    "Battery saver",
                    if (battery) "Not restricted" else "Restricted: some phones delay reminders. Set to “Don't optimise” or “No restrictions”.",
                    if (battery) Health.Ok else Health.Warn, Icons.Outlined.BatteryAlert, "Open",
                ) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            )
            add(
                Check(
                    "Reminders turned on",
                    if (offsets.isEmpty()) "None chosen: you won't be reminded" else "${offsets.size} per birthday",
                    if (offsets.isEmpty()) Health.Fail else Health.Ok, Icons.Outlined.Timer, "Choose",
                ) { chrome.nav.navigate(Routes.SETTINGS) },
            )
        }
    }
    val problems = checks.count { it.health == Health.Fail }
    val warnings = checks.count { it.health == Health.Warn }
    val time = LocalTime.of(settings.reminderHour, settings.reminderMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Reminder check-up", "Will reminders reach you?")
        ScrollScreen {
            Column(
                Modifier.padding(horizontal = Space.s4, vertical = Space.s2).fillMaxWidth()
                    .appCard(fill = if (problems + warnings == 0) c.accentSoft else c.celebrateSoft).padding(Space.s4),
            ) {
                Text(
                    when {
                        problems > 0 -> if (problems == 1) "1 thing is stopping reminders" else "$problems things are stopping reminders"
                        warnings > 0 -> "Reminders should arrive. ${if (warnings == 1) "1 setting" else "$warnings settings"} could make them late."
                        else -> "You're all set. Reminders arrive at $time."
                    },
                    style = t.lg, color = if (problems + warnings == 0) c.onAccentSoft else c.onCelebrateSoft,
                )
            }

            SectionHeader("Your phone", icon = Icons.Outlined.NotificationsActive)
            Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                checks.forEach { CheckRow(it) }
            }

            SectionHeader("Try it", icon = Icons.Outlined.Science)
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Text("See and hear a reminder now, or test the real thing: close the app and one arrives in a minute.", style = t.sm, color = c.textMuted)
                AppButton("Send a test now", {
                    val ok = g.notifier.showTest(viaAlarm = false)
                    if (!ok) scope.launch { chrome.snackbar.showSnackbar("Notifications are blocked. Allow them first.") }
                }, Modifier.fillMaxWidth(), icon = Icons.Outlined.NotificationsActive)
                AppButton("Test with the app closed (1 min)", {
                    g.scheduler.scheduleTest(60)
                    scope.launch { chrome.snackbar.showSnackbar("Close the app now. A test reminder arrives in about a minute.") }
                }, Modifier.fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.Timer)
            }
            Spacer(Modifier.height(Space.s8))
        }
    }
}

@Composable
private fun CheckRow(ch: Check) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val (bg, fg) = when (ch.health) {
        Health.Ok -> c.success to c.surface
        Health.Warn -> c.warning to if (AppTheme.isDark) c.bg else c.surface
        Health.Fail -> c.danger to c.onDanger
    }
    Row(
        Modifier.fillMaxWidth().appCard().padding(Space.s3)
            .semantics(mergeDescendants = true) { stateDescription = when (ch.health) { Health.Ok -> "OK"; Health.Warn -> "Could be better"; Health.Fail -> "Needs fixing" } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(if (ch.health == Health.Ok) Icons.Outlined.Check else Icons.Outlined.PriorityHigh, null, Modifier.size(18.dp), tint = fg)
        }
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ch.icon, null, Modifier.size(16.dp), tint = c.textMuted)
                Spacer(Modifier.width(Space.s1))
                Text(ch.title, style = t.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.text)
            }
            Text(ch.detail, style = t.xs, color = c.textMuted)
        }
        if (ch.health != Health.Ok && ch.fix != null) {
            Spacer(Modifier.width(Space.s2))
            AppButton(ch.fixLabel ?: "Fix", ch.fix, kind = ButtonKind.Secondary)
        }
    }
}
