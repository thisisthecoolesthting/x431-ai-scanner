package com.caseforge.scanner.planb.coding

import com.caseforge.scanner.planb.PlanbMarque
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CodingSessionTest {

    @Test
    fun apply_and_rollback_fail_until_G4() {
        val ctx = RuntimeEnvironment.getApplication()
        val session = CodingSession(ctx, PlanbMarque.JEEP)
        val firstId = session.checklist!!.entries.first().id

        assertTrue(session.apply(firstId).isFailure)
        assertTrue(session.rollback(firstId).isFailure)
    }
}
