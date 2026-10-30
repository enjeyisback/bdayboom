package app.bdayboom

import app.bdayboom.update.UpdateManager.Companion.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun newerVersions() {
        assertTrue(isNewer("0.2.0", "0.1.0"))
        assertTrue(isNewer("1.10.0", "1.9.2")) // numeric, not alphabetical
        assertTrue(isNewer("1.0.1", "1.0"))
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("1.0", "1.0.0"))
        assertFalse(isNewer("0.9.9", "1.0.0"))
    }
}
