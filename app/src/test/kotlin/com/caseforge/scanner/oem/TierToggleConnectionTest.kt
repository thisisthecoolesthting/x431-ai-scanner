package com.caseforge.scanner.oem

import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.obd.ObdCanFrame
import com.caseforge.scanner.obd.ObdTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TierToggleConnectionTest {

    /** Delegates to [inner] but counts [disconnect] invocations. */
    private class CountingTransport(
        val inner: RecordingStubTransport,
    ) : ObdTransport {
        var disconnectCount = 0

        override fun connect() = inner.connect()

        override fun disconnect() {
            disconnectCount++
            inner.disconnect()
        }

        override fun isConnected(): Boolean = inner.isConnected()

        override fun sendFrame(canId: Int, payload: ByteArray) = inner.sendFrame(canId, payload)

        override fun setFrameListener(listener: com.caseforge.scanner.obd.ObdFrameListener?) =
            inner.setFrameListener(listener)

        override fun describe(): String = inner.describe()
    }

    /** Minimal stub that accepts connect/disconnect without ISO-TP RX (empty VIN path OK for facade refresh). */
    private class RecordingStubTransport : ObdTransport {
        private var connected = false
        private var listener: com.caseforge.scanner.obd.ObdFrameListener? = null

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            connected = false
            listener = null
        }

        override fun isConnected(): Boolean = connected

        override fun sendFrame(canId: Int, payload: ByteArray) {
            // No simulated ECU reply — ObdEngine tolerates empty VIN for this test's purpose.
        }

        override fun setFrameListener(listener: com.caseforge.scanner.obd.ObdFrameListener?) {
            this.listener = listener
        }

        @Suppress("unused")
        fun deliver(frame: ObdCanFrame) {
            listener?.onFrame(frame)
        }
    }

    @Test
    fun preserveConnection_skipsDisconnectBetweenRefreshes() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val settings = SettingsRepo(ctx).apply {
            nativeObdExperimental = true
            directVciExperimental = false
            nativeObdUseVci = false
        }
        val inner = RecordingStubTransport()
        val counting = CountingTransport(inner)
        val facade = OemEngineFacade(ctx, settings) { counting }

        facade.refreshSuspendPreserveConnection()
        assertTrue(inner.isConnected())
        assertEquals(0, counting.disconnectCount)

        facade.refreshSuspendPreserveConnection()
        assertTrue(inner.isConnected())
        assertEquals(0, counting.disconnectCount)

        facade.refreshSuspend(preserveConnection = false)
        assertEquals(1, counting.disconnectCount)
    }
}
