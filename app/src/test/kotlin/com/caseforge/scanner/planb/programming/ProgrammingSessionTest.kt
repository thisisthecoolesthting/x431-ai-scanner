package com.caseforge.scanner.planb.programming

import com.caseforge.scanner.planb.PlanbMarque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProgrammingSessionTest {

    @Test
    fun readChecklist_loads_bundled_jeep_asset() {
        val ctx = RuntimeEnvironment.getApplication()
        val session = ProgrammingSession(ctx, PlanbMarque.JEEP)
        assertNotNull(session.readChecklist())
        assertEquals("jeep", session.readChecklist()!!.marqueId)
    }

    @Test
    fun requestFlash_alwaysFailsWithTier4Blocked_forKnownOp() {
        val ctx = RuntimeEnvironment.getApplication()
        val session = ProgrammingSession(ctx, PlanbMarque.JEEP)
        val entry = session.readChecklist()!!.entries.first()
        val outcome = session.requestFlash(entry)

        assertTrue(outcome.isFailure)
        assertEquals(
            ProgrammingGate.TIER4_BLOCKED,
            outcome.exceptionOrNull(),
        )
    }

    @Test
    fun requestFlash_failsFor_unknownOp() {
        val ctx = RuntimeEnvironment.getApplication()
        val session = ProgrammingSession(ctx, PlanbMarque.JEEP)
        val bogus = FlashOp(id = "not-listed", title = "", description = "")
        val outcome = session.requestFlash(bogus)
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull() is IllegalArgumentException)
    }
}
