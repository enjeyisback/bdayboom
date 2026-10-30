package app.bdayboom.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.bdayboom.data.Repository
import app.bdayboom.data.SettingsStore
import app.bdayboom.widget.WidgetRefresh
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Keeps exactly one alarm set: the soonest pending reminder or morning summary. Every data change, boot, time change and
 * the daily worker call [reschedule]; runs are serialised on one thread so overlapping triggers collapse.
 */
class ReminderScheduler(
    private val context: Context,
    private val repo: () -> Repository,
    private val settings: SettingsStore,
    private val notifier: Notifier,
) {
    private val worker = Executors.newSingleThreadExecutor()

    fun rescheduleAsync() {
        worker.execute { runCatching { reschedule() }.onFailure { Log.e(TAG, "reschedule failed", it) } }
    }

    /** Blocking. Shows anything due now, then sets the alarm for the next one. */
    @Synchronized
    fun reschedule(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val s = settings.now()
        val time = LocalTime.of(s.reminderHour, s.reminderMinute)
        val people = repo().peopleNow()
        val offsets = repo().offsetsNow()

        for (due in ReminderEngine.dueNow(people, offsets, time, s.feb29, now)) {
            val key = "${due.occurrence}${due.label?.let { "#$it" } ?: ""}"
            if (repo().markFiredNow(due.person.id, key, due.daysBefore)) notifier.show(due, s.keepUntilWished)
        }

        // Morning summary: shares this one alarm, so it needs no extra wake-ups or permissions.
        if (s.dailySummary && DailySummary.shouldSendNow(now, s.summaryLast)) {
            DailySummary.build(people, now.toLocalDate(), s.feb29)?.let { notifier.showSummary(it) }
            kotlinx.coroutines.runBlocking { settings.setSummaryLast(now.toLocalDate()) }
        }

        val nextReminder = ReminderEngine.nextAt(people, offsets, time, s.feb29, now)
        val nextSummary = if (s.dailySummary) DailySummary.nextAt(now, settings.now().summaryLast) else null
        val next = listOfNotNull(nextReminder, nextSummary).minOrNull()
        setAlarm(next)
        WidgetRefresh.all(context)
        Log.i(TAG, "next reminder at $next (exact=${canExact()})")
        return next
    }

    /** Check-up's test: a real alarm in [seconds], through the same receiver birthdays use. */
    fun scheduleTest(seconds: Long = 60) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + seconds * 1000
        try {
            if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canExact(): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun setAlarm(at: LocalDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                // No exact-alarm permission: Android may deliver this up to about an hour late.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    companion object {
        const val TAG = "Reminders"
    }
}
