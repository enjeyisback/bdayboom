package app.bdayboom

import app.bdayboom.data.AutoBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoBackupTest {
    @Test fun keepsTheNewestFiveAndNeverTouchesOtherFiles() {
        val autos = (1..7).map { "BdayBoom-auto-2026-10-%02d-0900.zip".format(it) }
        val others = listOf("BdayBoom-backup-2026-01-01-1200.zip", "photo.jpg", "BdayBoom-auto-notes.txt")
        val pruned = AutoBackup.toPrune((autos + others).shuffled())
        assertEquals(listOf("BdayBoom-auto-2026-10-02-0900.zip", "BdayBoom-auto-2026-10-01-0900.zip"), pruned)
    }

    @Test fun dueAfterAWeekWithSlackForTheWorker() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertTrue(AutoBackup.isDue(0, 100 * day)) // never backed up
        assertFalse(AutoBackup.isDue(10 * day, 16 * day))
        assertTrue(AutoBackup.isDue(10 * day, 17 * day - TimeUnit.HOURS.toMillis(1)))
    }
}
