package app.bdayboom

import android.app.Application
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import app.bdayboom.data.Backup
import app.bdayboom.data.Repository
import app.bdayboom.data.SettingsStore
import app.bdayboom.data.db.AppDatabase
import app.bdayboom.reminders.DailyRefreshWorker
import app.bdayboom.reminders.Notifier
import app.bdayboom.reminders.ReminderScheduler
import app.bdayboom.reminders.ReminderTrail

class App : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        ReminderTrail.add(this, "process start")
        graph.notifier.ensureChannel()
        graph.scheduler.rescheduleAsync()
        DailyRefreshWorker.enqueue(this)
    }
}

/** Hand-wired dependencies: small app, no DI framework. */
class Graph(context: Context) {
    private val driver = AndroidSqliteDriver(
        schema = AppDatabase.Schema,
        context = context,
        name = "birthdays.db",
        callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
    val db = AppDatabase(driver)
    val settings = SettingsStore(context)
    val notifier = Notifier(context)
    val scheduler: ReminderScheduler = ReminderScheduler(context, { repo }, settings, notifier)
    val repo: Repository = Repository(db) { scheduler.rescheduleAsync() }
    val backup = Backup(db, settings, java.io.File(context.filesDir, "photos")) { scheduler.rescheduleAsync() }
}

val Context.graph: Graph get() = (applicationContext as App).graph
