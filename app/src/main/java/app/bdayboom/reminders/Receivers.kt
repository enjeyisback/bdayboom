package app.bdayboom.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.bdayboom.data.Photos
import app.bdayboom.graph
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The single exact alarm went off: show what's due, set the next one. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runInBackground(context) {
        if (intent.action == ACTION_TEST) {
            ReminderTrail.add(context, "test alarm fired, shown=${it.notifier.showTest(viaAlarm = true)}")
        } else {
            ReminderTrail.add(context, "alarm fired, next ${it.scheduler.reschedule()}")
        }
    }

    companion object {
        const val ACTION_TEST = "app.bdayboom.TEST_REMINDER"
    }
}

/** "Wished ✓" on a sticky on-the-day reminder: clears it. */
class WishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        androidx.core.app.NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_ID, 0))
    }

    companion object {
        const val EXTRA_ID = "notificationId"
    }
}

/** Boot, time or zone change, app update, exact-alarm permission change: alarms must be rebuilt. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(ReminderScheduler.TAG, "reschedule on ${intent.action}")
        ReminderTrail.add(context, "receiver ${intent.action}")
        runInBackground(context) { ReminderTrail.add(context, "next ${it.scheduler.reschedule()}") }
    }
}

private fun BroadcastReceiver.runInBackground(context: Context, block: (app.bdayboom.Graph) -> Unit) {
    val pending = goAsync()
    thread(name = "reminders") {
        try {
            block(context.graph)
        } catch (e: Exception) {
            Log.e(ReminderScheduler.TAG, "receiver failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * A short on-device trail of process starts and reschedule triggers (last 50 lines, app-private).
 * Survives reboots, unlike logcat, so "why didn't my reminder come?" can be answered afterwards.
 */
object ReminderTrail {
    private const val FILE = "reminder-trail.log"

    @Synchronized
    fun add(context: Context, line: String) {
        runCatching {
            val f = java.io.File(context.filesDir, FILE)
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "${java.time.LocalDateTime.now()} $line"
            f.writeText(lines.takeLast(50).joinToString("\n", postfix = "\n"))
        }
    }
}

/** Daily safety net: reschedule (in case an alarm was dropped) and purge old rows. */
class DailyRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        g.repo.purgeNow()
        Photos.deleteOrphans(applicationContext, g.db.entryQueries.photoPaths().executeAsList().filterNotNull().toSet())
        g.scheduler.reschedule()
        app.bdayboom.data.AutoBackup.runIfDue(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyRefreshWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
