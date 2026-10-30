package app.bdayboom.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person
import app.bdayboom.data.ThemeMode
import app.bdayboom.graph
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * Debug-only test hooks, driven over adb (this phone blocks input injection):
 *   am broadcast -n app.bdayboom.debug/app.bdayboom.debug.DebugReceiver -a seed
 *   ... -a clear | -a status | -a time --ei h 9 --ei m 30 | -a add --es name Maya --ei month 3 --ei day 11 [--ei year 1995]
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                runBlocking { handle(context, intent) }
            } catch (e: Exception) {
                Log.e(TAG, "debug action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val g = context.graph
        when (intent.action) {
            "seed" -> {
                val today = LocalDate.now()
                fun p(name: String, d: LocalDate, year: Int?, group: String?, type: EntryType = EntryType.Birthday, notes: String = "") =
                    Person("", type, name, d.monthValue, d.dayOfMonth, year, group, notes, true)
                listOf(
                    p("Maya Chen", today, 1995, "grp-friends", notes = "Loves ceramics. Mentioned a filter coffee dripper."),
                    p("Rohan Iyer", today.plusDays(1), 1998, "grp-work"),
                    p("Chloe Bennett", today.plusDays(5), 1999, "grp-friends"),
                    p("Priya & Sam", today.plusDays(12), 2019, null, EntryType.Anniversary),
                    p("Grandpa Joe", today.plusDays(40), 1946, "grp-family"),
                    p("Aunt Leela", today.plusDays(95), null, "grp-family"),
                    p("Team offsite", today.plusDays(150), null, "grp-work", EntryType.Event),
                    p("Leap-day Leo", LocalDate.of(2024, 2, 29), 2000, "grp-friends"),
                ).forEach { g.repo.save(it) }
            }
            "clear" -> g.repo.deleteAllNow()
            "time" -> g.settings.setReminderTime(intent.getIntExtra("h", 9), intent.getIntExtra("m", 0))
            "theme" -> g.settings.setTheme(ThemeMode.valueOf(intent.getStringExtra("mode") ?: "System"))
            "add" -> g.repo.save(
                Person(
                    "", EntryType.Birthday, intent.getStringExtra("name") ?: "Test",
                    intent.getIntExtra("month", 1), intent.getIntExtra("day", 1),
                    intent.getIntExtra("year", 0).takeIf { it > 0 }, null, "", true,
                ),
            )
            // Same code path as S17, minus the system file picker (which needs taps).
            "backup" -> java.io.File(context.filesDir, "test-backup.zip").outputStream().use { g.backup.write(it, "debug") }
            "restore" -> {
                val r = java.io.File(context.filesDir, "test-backup.zip").inputStream().use { g.backup.restore(it, intent.getBooleanExtra("replace", false)) }
                Log.i(TAG, "restore added=${r.added} skipped=${r.skipped}")
            }
            // Gives a person a generated picture through the real import path (the photo picker needs taps).
            "photo" -> {
                val who = intent.getStringExtra("name") ?: return
                val p = g.repo.peopleNow().firstOrNull { it.name == who } ?: return
                val bmp = android.graphics.Bitmap.createBitmap(900, 600, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bmp).apply {
                    drawColor(android.graphics.Color.rgb(255, 179, 71))
                    drawCircle(450f, 300f, 220f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 50, 59) })
                }
                val src = java.io.File(context.cacheDir, "test-photo.png").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                val path = app.bdayboom.data.Photos.importFrom(context, android.net.Uri.fromFile(src))
                g.repo.save(p.copy(photoPath = path))
                Log.i(TAG, "photo for ${p.name}: $path (${java.io.File(path).length()} bytes)")
            }
            // What S18's Import button does with everything new or changed selected.
            "importcontacts" -> {
                val found = app.bdayboom.data.ContactsReader.read(context)
                val imported = g.repo.importedContacts()
                val pick = found.filter { c -> imported[c.key].let { it == null || it.month != c.month || it.day != c.day || it.year != c.year } }
                Log.i(TAG, "importcontacts found=${found.size} new-or-changed=${pick.size} imported=${g.repo.importContacts(pick)}")
            }
            "deleteprefix" -> {
                val prefix = intent.getStringExtra("prefix")?.takeIf { it.length >= 3 } ?: return
                g.repo.peopleNow().filter { it.name.startsWith(prefix) }.forEach { g.repo.softDelete(it.id); Log.i(TAG, "deleted ${it.name}") }
            }
            "testalarm" -> g.scheduler.scheduleTest(intent.getIntExtra("s", 20).toLong())
            "wished" -> context.sendBroadcast(
                Intent(context, app.bdayboom.reminders.WishedReceiver::class.java)
                    .putExtra(app.bdayboom.reminders.WishedReceiver.EXTRA_ID, intent.getIntExtra("id", 0)),
            )
            // The morning summary as it would read today (the Monday-only rule can make it empty: then a sample).
            "summary" -> g.notifier.showSummary(
                app.bdayboom.reminders.DailySummary.build(g.repo.peopleNow(), LocalDate.now(), g.settings.now().feb29)
                    ?: app.bdayboom.reminders.Summary("(debug) nothing to say today", "A quiet day that isn't Monday: no summary is sent."),
            )
            "status" -> Unit
        }
        val next = g.scheduler.reschedule()
        val fired = g.db.reminderQueries.recentFired().executeAsList()
        Log.i(TAG, "action=${intent.action} people=${g.repo.peopleNow().size} offsets=${g.repo.offsetsNow()} next=$next exact=${g.scheduler.canExact()} notify=${g.notifier.canNotify()}")
        fired.forEach { Log.i(TAG, "fired ${it.entry_id.take(8)} occ=${it.occurrence_date} before=${it.days_before} at=${it.fired_at}") }
        g.repo.peopleNow().forEach { Log.i(TAG, "person ${it.id} ${it.name}") }
    }

    companion object {
        const val TAG = "BirthdaysDebug"
    }
}
