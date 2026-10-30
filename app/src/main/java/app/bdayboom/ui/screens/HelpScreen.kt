package app.bdayboom.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import app.bdayboom.reminders.OemAutostart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space

/** S16 why reminders go missing, with shortcuts into the right system screens. Our own wording. */
@Composable
fun HelpScreen() {
    val context = LocalContext.current
    val c = AppTheme.colors
    val t = AppTheme.type
    val maker = Build.MANUFACTURER.lowercase()

    val steps = when {
        "xiaomi" in maker || "redmi" in maker || "poco" in maker -> listOf(
            "Open Autostart settings below and switch this app on, so reminders survive a restart.",
            "In App info, Battery saver: choose No restrictions.",
            "Open recent apps, long-press this app and tap the lock, so it isn't cleared.",
        )
        "samsung" in maker -> listOf(
            "Open App info below, then Battery: choose Unrestricted.",
            "In phone Settings, Battery, Background usage limits: make sure this app isn't in Sleeping or Deep sleeping apps.",
        )
        "huawei" in maker || "honor" in maker -> listOf(
            "Phone Settings, Battery, App launch: find this app, switch to Manage manually and turn on all three options.",
        )
        "oppo" in maker || "realme" in maker || "oneplus" in maker || "vivo" in maker -> listOf(
            "Open App info below, then Battery: allow background activity and auto launch.",
        )
        else -> listOf("Open App info below, then Battery: choose Unrestricted or Not optimised.")
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Reminders not arriving?", "Fix it in a minute")
        ScrollScreen {
            SectionHeader("Why it happens", icon = Icons.Outlined.BatteryAlert)
            Text(
                "Some phones stop apps in the background to save battery, and that can stop reminders too. " +
                    "A couple of settings tell your phone this app is allowed to wake up on time.",
                style = t.base, color = c.text, modifier = Modifier.padding(horizontal = Space.s4),
            )
            SectionHeader("On your ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}", icon = Icons.Outlined.PhoneAndroid)
            Column(
                Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                steps.forEachIndexed { i, s -> Text("${i + 1}.  $s", style = t.base, color = c.text) }
            }
            Spacer(Modifier.height(Space.s4))
            if (OemAutostart.isXiaomi) {
                AppButton(
                    "Open Autostart settings", { context.startActivity(OemAutostart.settingsIntent(context)) },
                    Modifier.padding(horizontal = Space.s4).fillMaxWidth(), icon = Icons.Outlined.RestartAlt,
                )
                Spacer(Modifier.height(Space.s2))
            }
            AppButton(
                "Open App info", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                },
                Modifier.padding(horizontal = Space.s4).fillMaxWidth(), icon = Icons.Outlined.Settings,
            )
            Spacer(Modifier.height(Space.s2))
            AppButton(
                "Battery optimisation list", {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                Modifier.padding(horizontal = Space.s4).fillMaxWidth(), ButtonKind.Secondary,
            )
            Spacer(Modifier.height(Space.s8))
        }
    }
}
