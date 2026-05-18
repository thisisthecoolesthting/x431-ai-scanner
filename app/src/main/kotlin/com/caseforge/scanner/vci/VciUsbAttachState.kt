package com.caseforge.scanner.vci

import android.hardware.usb.UsbDevice

/** Latest USB VCI plug-in from [android.hardware.usb.action.USB_DEVICE_ATTACHED]. */
object VciUsbAttachState {
    @Volatile
    var pendingDevice: UsbDevice? = null

    fun consumePending(): UsbDevice? {
        val d = pendingDevice
        pendingDevice = null
        return d
    }
}
