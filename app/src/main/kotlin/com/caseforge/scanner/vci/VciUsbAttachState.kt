package com.caseforge.scanner.vci

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Latest USB VCI plug-in from [android.hardware.usb.action.USB_DEVICE_ATTACHED]. */
object VciUsbAttachState {
    @Volatile
    var pendingDevice: UsbDevice? = null

    @Volatile
    private var permissionSignal: CompletableDeferred<Unit>? = null

    fun consumePending(): UsbDevice? {
        val d = pendingDevice
        pendingDevice = null
        return d
    }

    /** Called from [com.caseforge.scanner.MainActivity] when the system USB permission dialog is accepted. */
    fun signalPermissionGranted(device: UsbDevice? = null) {
        device?.let { pendingDevice = it }
        permissionSignal?.complete(Unit)
    }

    /** Suspend until USB permission is granted or [timeoutMs] elapses. */
    suspend fun awaitPermissionGrant(timeoutMs: Long): Boolean {
        val signal = CompletableDeferred<Unit>()
        permissionSignal = signal
        return try {
            withTimeoutOrNull(timeoutMs) { signal.await() } != null
        } finally {
            if (permissionSignal === signal) permissionSignal = null
        }
    }
}
