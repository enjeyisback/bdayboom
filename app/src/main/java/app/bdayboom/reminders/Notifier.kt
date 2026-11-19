package app.bdayboom.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.bdayboom.MainActivity
import app.bdayboom.R
import app.bdayboom.data.Dates
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person

class Notifier(private val context: Context) {

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Birthday reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "On the day and the days before, at the time you choose"
            },
        )
        // Its own channel, so muting the summary in Android settings never mutes birthday reminders.
        nm.createNotificationChannel(
            NotificationChannel(SUMMARY_CHANNEL, "Morning summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "At 9:00: who's celebrating today and this week"
            },
        )
        // Alarm sound on the alarm volume, like a clock alarm. Only used on the day of a celebration.
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        nm.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL, "Birthday alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings like an alarm on the day of a birthday or event"
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        // While the floating popup rings, the shade only holds a quiet copy (no second sound or banner).
        nm.createNotificationChannel(
            NotificationChannel(ALARM_QUIET_CHANNEL, "Birthday alarm (popup showing)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps Snooze and Stop in the notification shade while the alarm popup is on screen"
            },
        )
    }

    /**
     * The day alarm: full screen over the lock screen (when the phone is idle) and a sound that repeats until
     * Stop, Snooze or a wish. Gives up after [RING_MS] so it never rings all morning; the normal reminder stays.
     */
    fun showAlarm(dues: List<Due>): Boolean {
        if (dues.isEmpty()) return false
        val items = dues.distinctBy { it.person.id to it.label }
            .map { AlarmItem(it.person.id, it.person.name.trim().substringBefore(' '), title(it.person, it), it.person.photoPath) }
        return ring(items)
    }

    fun showAlarmTest(): Boolean = ring(listOf(AlarmItem(null, "Test", "Test: this is how the birthday alarm rings", null)))

    /** Unlocked and in use: floating popup with its own sound. Locked or not allowed: full-screen page + ringing notification. */
    private fun ring(items: List<AlarmItem>): Boolean {
        if (!canNotify()) return false
        ensureChannel()
        // A fresh post, not an update: Android only opens the full-screen page for a new notification.
        NotificationManagerCompat.from(context).cancel(ALARM_ID)
        AlarmPopup.dismiss()
        val popup = AlarmPopup.shouldUse(context) && AlarmPopup.show(context, items)
        val ids = items.mapNotNull { it.id }
        val names = items.filter { it.id != null }.map { it.name }
        val titles = items.map { it.title }
        val page = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmActivity.EXTRA_IDS, ids.toTypedArray())
            .putExtra(AlarmActivity.EXTRA_NAMES, names.toTypedArray())
            .putExtra(AlarmActivity.EXTRA_TITLES, titles.toTypedArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val full = PendingIntent.getActivity(context, ALARM_ID, page, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, req: Int) = PendingIntent.getBroadcast(
            context, req, Intent(context, AlarmActionReceiver::class.java).setAction(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, if (popup) ALARM_QUIET_CHANNEL else ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(titles.first() + if (titles.size > 1) " +${titles.size - 1} more" else "")
            .setContentText("Tap to wish them now 🎉")
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_MS)
            .addAction(0, "Snooze ${AlarmActionReceiver.SNOOZE_MINUTES} min", action(AlarmActionReceiver.ACTION_SNOOZE, ALARM_ID + 1))
            .addAction(0, "Stop", action(AlarmActionReceiver.ACTION_STOP, ALARM_ID + 2))
        if (popup) return post(ALARM_ID, b.build())
        b.setFullScreenIntent(full, true)
        val n = b.build().apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
        return post(ALARM_ID, n)
    }

    /** Android 14+: full-screen pages need a permission the user can switch off. */
    fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun showSummary(s: Summary) {
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, SUMMARY_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            SUMMARY_ID,
            NotificationCompat.Builder(context, SUMMARY_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_cake)
                .setContentTitle(s.title)
                .setContentText(s.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(s.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [keep]: on-the-day reminders stay until "Wished" is tapped, so a reflex swipe-clear doesn't lose them. */
    fun show(due: Due, keep: Boolean) {
        if (!canNotify()) return
        ensureChannel()
        val p = due.person
        val id = "${p.id}|${due.occurrence}|${due.label ?: ""}|${due.daysBefore}".hashCode()
        val sticky = keep && due.daysBefore == 0

        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ENTRY, p.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Opens the wish screen (edit, pick a ready wish); sending there marks them wished.
        val share = PendingIntent.getActivity(
            context, id + 1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WISH, p.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(title(p, due))
            .setContentText(if (due.daysBefore == 0) "Tap to wish them now 🎉" else "Plenty of time to plan something")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(!sticky)
            .setOngoing(sticky)
            .setContentIntent(open)
            .addAction(0, "Send wish", share)
        if (sticky) {
            val wished = PendingIntent.getBroadcast(
                context, id + 2,
                Intent(context, WishedReceiver::class.java).putExtra(WishedReceiver.EXTRA_ID, id)
                    .putExtra(WishedReceiver.EXTRA_PERSON, p.id).putExtra(WishedReceiver.EXTRA_DATE, due.occurrence.toString()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Wished ✓", wished)
        }
        post(id, b.build())
    }

    /** Clears today's on-the-day reminders for [p] (birthday and second date), e.g. once a wish is sent. */
    fun clearOnTheDay(p: Person, date: java.time.LocalDate) {
        val nm = NotificationManagerCompat.from(context)
        listOf(null, p.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary").forEach { label ->
            nm.cancel("${p.id}|$date|${label ?: ""}|0".hashCode())
        }
    }

    /** Proves the whole path end to end: channel, permission, sound. */
    fun showTest(viaAlarm: Boolean): Boolean {
        if (!canNotify()) return false
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(if (viaAlarm) "Test reminder arrived on time" else "Test reminder")
            .setContentText(if (viaAlarm) "Birthday reminders will reach you like this, even with the app closed." else "This is how a birthday reminder looks and sounds.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        return post(TEST_ID, n)
    }

    private fun post(id: Int, n: android.app.Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(id, n)
        true
    } catch (_: SecurityException) {
        false // permission revoked between the check and the call
    }

    private fun title(p: Person, due: Due): String {
        val yr = due.year ?: p.year
        val n = Dates.turning(yr, due.occurrence)
        val whenText = when (due.daysBefore) {
            0 -> "today"
            1 -> "tomorrow"
            7 -> "in a week"
            14 -> "in two weeks"
            30 -> "in a month"
            else -> "in ${due.daysBefore} days"
        }
        if (due.label != null) {
            return if (n != null) "${p.name}: $n years ${due.label.lowercase()} $whenText" else "${p.name}'s ${due.label.lowercase()} is $whenText"
        }
        return when (p.type) {
            EntryType.Birthday -> if (n != null) "${p.name} turns $n $whenText" else "${p.name}'s birthday is $whenText"
            EntryType.Anniversary -> if (n != null) "${p.name}: $n years $whenText" else "${p.name}'s anniversary is $whenText"
            EntryType.Event -> "${p.name} is $whenText"
        }
    }

    companion object {
        const val CHANNEL = "reminders"
        const val SUMMARY_CHANNEL = "summary"
        const val ALARM_CHANNEL = "day_alarm"
        const val ALARM_QUIET_CHANNEL = "day_alarm_quiet"
        const val ALARM_ID = 4250
        const val RING_MS = 60_000L
        const val SUMMARY_ID = 4243
        const val TEST_ID = 4242
    }
}
