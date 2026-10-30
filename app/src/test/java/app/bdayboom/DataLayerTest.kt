package app.bdayboom

import app.bdayboom.data.AppSettings
import app.bdayboom.data.Backup
import app.bdayboom.data.BackupException
import app.bdayboom.data.BackupSettings
import app.bdayboom.data.ContactBirthday
import app.bdayboom.data.EntryType
import app.bdayboom.data.Feb29Rule
import app.bdayboom.data.Person
import app.bdayboom.data.Repository
import app.bdayboom.data.db.AppDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Data layer against real SQLite (in memory), the same schema and queries the app ships. */
class DataLayerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var backup: Backup
    private var changes = 0
    private val settings = object : BackupSettings {
        var s = AppSettings()
        override fun now() = s
        override fun restore(hour: Int?, minute: Int, feb29: Feb29Rule?) {
            s = s.copy(reminderHour = hour ?: s.reminderHour, reminderMinute = if (hour != null) minute else s.reminderMinute, feb29 = feb29 ?: s.feb29)
        }
    }

    @Before fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        AppDatabase.Schema.create(driver)
        db = AppDatabase(driver)
        repo = Repository(db) { changes++ }
        backup = Backup(db, settings, tmp.newFolder("photos")) { changes++ }
    }

    private fun person(name: String, m: Int, d: Int, y: Int? = null, notes: String = "", group: String? = null) =
        Person("", EntryType.Birthday, name, m, d, y, group, notes, true)

    private fun contact(key: String, m: Int, d: Int, y: Int? = 1990) = ContactBirthday(key, "C $key", EntryType.Birthday, m, d, y)

    // F03-E1
    @Test fun reimportingContactsAddsNoDuplicates() = runBlocking {
        val found = listOf(contact("k1", 5, 12), contact("k2", 8, 23, null))
        repo.importContacts(found)
        repo.importContacts(found)
        assertEquals(2, repo.peopleNow().size)
    }

    // F03-E2
    @Test fun changedContactDateUpdatesButKeepsNotesAndGroup() = runBlocking {
        repo.importContacts(listOf(contact("k1", 5, 12)))
        val p = repo.peopleNow().single()
        repo.save(p.copy(notes = "loves tea", groupId = "grp-family"))
        repo.importContacts(listOf(contact("k1", 6, 15)))
        val after = repo.peopleNow().single()
        assertEquals(6 to 15, after.month to after.day)
        assertEquals("loves tea", after.notes)
        assertEquals("grp-family", after.groupId)
    }

    // F07-E2: a removed person no longer feeds the reminder engine
    @Test fun softDeletedPeopleAreNotScheduled() = runBlocking {
        val id = repo.save(person("Gone", 10, 30))
        repo.softDelete(id)
        assertTrue(repo.peopleNow().isEmpty())
        repo.undoDelete(id)
        assertEquals(1, repo.peopleNow().size)
    }

    // reminders fire once per (person, occurrence, offset)
    @Test fun markFiredIsIdempotent() = runBlocking {
        val id = repo.save(person("Once", 10, 30))
        assertTrue(repo.markFiredNow(id, "2026-10-30", 0))
        assertFalse(repo.markFiredNow(id, "2026-10-30", 0))
        assertTrue(repo.markFiredNow(id, "2026-10-30", 1))
    }

    private fun backupBytes(): ByteArray = ByteArrayOutputStream().also { backup.write(it, "test") }.toByteArray()

    // F08-H1
    @Test fun replaceRestoreRoundTripsPeopleAndSettings() = runBlocking {
        repo.save(person("Zoë 🎂 Ñandú", 2, 29, null, notes = "leap"))
        repo.save(person("Asha", 1, 5, 1990))
        repo.setOffset(7, true)
        settings.s = settings.s.copy(reminderHour = 8, reminderMinute = 15)
        val bytes = backupBytes()

        repo.deleteAllNow()
        settings.s = AppSettings()
        val r = backup.restore(ByteArrayInputStream(bytes), replace = true)

        assertEquals(2, r.added)
        assertEquals(setOf("Zoë 🎂 Ñandú", "Asha"), repo.peopleNow().map { it.name }.toSet())
        assertEquals("leap", repo.peopleNow().first { it.day == 29 }.notes)
        assertEquals(listOf(0, 7), repo.offsetsNow())
        assertEquals(8 to 15, settings.s.reminderHour to settings.s.reminderMinute)
    }

    // F08-E1
    @Test fun addRestoreTwiceAddsNoDuplicates() = runBlocking {
        repo.save(person("Asha", 1, 5))
        val bytes = backupBytes()
        assertEquals(0, backup.restore(ByteArrayInputStream(bytes), replace = false).added)
        assertEquals(1, repo.peopleNow().size)
    }

    // F08-E2: found on device earlier (3 duplicate notifications); keep it fixed
    @Test fun replaceRestoreKeepsAlreadyNotifiedRows() = runBlocking {
        val id = repo.save(person("Today", 10, 30))
        repo.markFiredNow(id, "2026-10-30", 0)
        backup.restore(ByteArrayInputStream(backupBytes()), replace = true)
        assertFalse("would notify again", repo.markFiredNow(id, "2026-10-30", 0))
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    // F08-N1
    @Test fun badFilesAreRefusedAndTheListIsUntouched() = runBlocking {
        repo.save(person("Keep me", 3, 3))
        val bad = listOf(
            zipOf("notes.txt" to "hello"), // not a backup
            zipOf("backup.json" to "{ broken"), // damaged
            zipOf("backup.json" to """{"format":"birthdays-backup","schema_version":99,"entries":[]}"""), // newer app
            "not even a zip".toByteArray(),
        )
        for (b in bad) {
            try {
                backup.restore(ByteArrayInputStream(b), replace = true)
                throw AssertionError("restore should have refused this file")
            } catch (e: BackupException) { /* refused, as it should be */ }
        }
        assertEquals(listOf("Keep me"), repo.peopleNow().map { it.name })
    }

    // BUG-001: before restoring, show what's in the file so the wrong backup isn't picked by accident
    @Test fun previewShowsWhatsInsideWithoutChangingAnything() = runBlocking {
        repo.save(person("A", 1, 1))
        repo.save(person("B", 2, 2))
        val bytes = backupBytes()
        repo.save(person("C", 3, 3))

        val p = backup.preview(ByteArrayInputStream(bytes))

        assertEquals(2, p.people)
        assertEquals(0, p.photos)
        assertTrue(p.exportedAt.startsWith("20"))
        assertEquals(3, repo.peopleNow().size) // nothing changed
    }
}
