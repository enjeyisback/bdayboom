package app.bdayboom.reminders

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.bdayboom.MainActivity
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The day alarm's full-screen page, shown over the lock screen like a clock alarm. The sound comes from the
 * alarm notification (repeating), so closing this page any way but Stop/Snooze/Wish leaves it in the shade.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val ids = intent.getStringArrayExtra(EXTRA_IDS)?.toList().orEmpty()
        val names = intent.getStringArrayExtra(EXTRA_NAMES)?.toList().orEmpty()
        val titles = intent.getStringArrayExtra(EXTRA_TITLES)?.toList().orEmpty()

        setContent {
            AppTheme(dark = true) {
                val c = AppTheme.colors
                val t = AppTheme.type
                // Matches the notification giving up, so a page nobody saw doesn't sit there all day.
                LaunchedEffect(Unit) { delay(Notifier.RING_MS); finish() }
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.heroStart, c.heroEnd)))
                        .safeDrawingPadding().padding(Space.s6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(Space.s8))
                    Text("🎂", fontSize = 72.sp)
                    Spacer(Modifier.height(Space.s3))
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = t.label, color = c.onHero.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(Space.s6))
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.s4),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        titles.forEachIndexed { i, title ->
                            Text(title, style = t.xl, color = c.onHero, textAlign = TextAlign.Center)
                            ids.getOrNull(i)?.let { id ->
                                AppButton(
                                    if (ids.size == 1) "Send wish" else "Wish ${names.getOrElse(i) { "them" }}",
                                    { wish(id) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Celebration,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.s4))
                    AppButton(
                        "Snooze ${AlarmActionReceiver.SNOOZE_MINUTES} min", { act(AlarmActionReceiver.ACTION_SNOOZE) },
                        Modifier.fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.Alarm,
                    )
                    Spacer(Modifier.height(Space.s2))
                    AppButton("Stop", { act(AlarmActionReceiver.ACTION_STOP) }, Modifier.fillMaxWidth(), ButtonKind.Neutral)
                }
            }
        }
    }

    private fun act(action: String) {
        sendBroadcast(Intent(this, AlarmActionReceiver::class.java).setAction(action))
        finish()
    }

    /** Stops the ringing, asks for the phone to be unlocked, then opens the wish screen. */
    private fun wish(personId: String) {
        sendBroadcast(Intent(this, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_STOP))
        val open = {
            startActivity(
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_WISH, personId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
            })
        } else open()
    }

    companion object {
        const val EXTRA_IDS = "ids"
        const val EXTRA_NAMES = "names"
        const val EXTRA_TITLES = "titles"
    }
}
