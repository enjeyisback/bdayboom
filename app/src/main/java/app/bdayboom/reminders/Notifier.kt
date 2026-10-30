package app.bdayboom.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.bdayboom.MainActivity
import app.bdayboom.R
import app.bdayboom.data.Dates
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person
import app.bdayboom.ui.wishText

class Notifier(private val context: Context) {

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Birthday reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "On the day and the days before, at the time you choose"
            },
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
        val share = PendingIntent.getActivity(
            context, id + 1,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, wishText(p)),
                "Send a wish",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
                Intent(context, WishedReceiver::class.java).putExtra(WishedReceiver.EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Wished ✓", wished)
        }
        post(id, b.build())
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
        const val TEST_ID = 4242
    }
}
