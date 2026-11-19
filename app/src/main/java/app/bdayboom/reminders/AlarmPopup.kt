package app.bdayboom.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.bdayboom.MainActivity
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.Avatar
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.initials
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Space

/** One celebration on the day alarm. [id] is null for the test alarm (no wish button). */
data class AlarmItem(val id: String?, val name: String, val title: String, val photo: String?)

/**
 * The day alarm while the phone is unlocked and in use: a floating card over whatever app is open (like a
 * society-gate visitor popup), with its own looping alarm sound. Needs "Display over other apps"; without it,
 * or when the phone is locked, the notification's full-screen page and sound take over instead.
 */
object AlarmPopup {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val timeout = Runnable { dismiss() }

    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))

    /** True when the popup fits now: allowed, screen on and unlocked (a locked phone gets the full-screen page). */
    fun shouldUse(context: Context): Boolean {
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return canDraw(context) && screenOn && !locked
    }

    /** Any thread. Returns false if the window couldn't be added (permission revoked meanwhile). */
    fun show(context: Context, items: List<AlarmItem>): Boolean {
        val app = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching { attach(app, items) }.isSuccess
        val done = java.util.concurrent.CountDownLatch(1)
        var ok = false
        main.post {
            ok = runCatching { attach(app, items) }.onFailure { android.util.Log.e(ReminderScheduler.TAG, "popup failed", it) }.isSuccess
            done.countDown()
        }
        done.await(3, java.util.concurrent.TimeUnit.SECONDS)
        return ok
    }

    /** Any thread: removes the card and stops the sound. Safe to call when nothing is showing. */
    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { dismiss() }; return }
        main.removeCallbacks(timeout)
        ringtone?.stop(); ringtone = null
        vibrator?.cancel(); vibrator = null
        view?.let { v -> runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) } }
        owner?.destroy()
        view = null; owner = null
    }

    private fun attach(context: Context, items: List<AlarmItem>) {
        dismiss()
        val wm = context.getSystemService(WindowManager::class.java)
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { AppTheme { Card(items) { action -> onAction(context, action) } } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        wm.addView(v, params)
        view = v; owner = o
        main.postDelayed(timeout, Notifier.RING_MS)
        ring(context)
    }

    private fun ring(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        // Vibration is a bonus: never let it stop the popup.
        vibrator = runCatching {
            context.getSystemService(Vibrator::class.java)?.apply {
                @Suppress("DEPRECATION")
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }.getOrNull()
    }

    private sealed interface Action {
        data object Stop : Action
        data object Snooze : Action
        data class Wish(val id: String) : Action
    }

    private fun onAction(context: Context, a: Action) {
        when (a) {
            Action.Stop -> context.sendBroadcast(Intent(context, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_STOP))
            Action.Snooze -> context.sendBroadcast(Intent(context, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_SNOOZE))
            is Action.Wish -> {
                context.sendBroadcast(Intent(context, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_STOP))
                context.startActivity(
                    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_WISH, a.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
        dismiss()
    }

    @Composable
    private fun Card(items: List<AlarmItem>, act: (Action) -> Unit) {
        val c = AppTheme.colors
        val t = AppTheme.type
        val pop = remember { Animatable(0.85f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(Space.s5), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().scale(pop.value).clip(RoundedCornerShape(28.dp)).background(c.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header: the celebration, big, on the app's hero gradient.
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(c.heroStart, c.heroEnd))).padding(vertical = Space.s6, horizontal = Space.s4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🎉  Celebration today  🎂", style = t.label, color = c.onHero.copy(alpha = 0.9f))
                    Spacer(Modifier.height(Space.s4))
                    if (items.size == 1) {
                        val it = items[0]
                        if (it.id != null) Avatar(initials(it.name), size = 96.dp, ring = c.onHero, photo = it.photo) else Text("🎂", fontSize = 64.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                            items.take(4).forEach { Avatar(initials(it.name), size = 64.dp, ring = c.onHero, photo = it.photo) }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(Space.s5),
                    verticalArrangement = Arrangement.spacedBy(Space.s3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items.forEach { item ->
                        Text(item.title, style = if (items.size == 1) t.xl else t.name, color = c.text, textAlign = TextAlign.Center)
                        item.id?.let { id ->
                            AppButton(
                                if (items.size == 1) "Send wish" else "Wish ${item.name}", { act(Action.Wish(id)) },
                                Modifier.fillMaxWidth(), icon = Icons.Outlined.Celebration,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = Space.s5, end = Space.s5, bottom = Space.s5), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    AppButton("Snooze", { act(Action.Snooze) }, Modifier.weight(1f), ButtonKind.Secondary, Icons.Outlined.Alarm)
                    AppButton("Stop", { act(Action.Stop) }, Modifier.weight(1f), ButtonKind.Neutral)
                }
            }
        }
    }
}

/** A window added by WindowManager has no Activity, so Compose needs its own lifecycle. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
