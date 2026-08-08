package com.caseforge.scanner.vci

import android.hardware.usb.UsbDevice
import com.caseforge.scanner.agent.discovery.UsbSerialChipIds
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * USB serial prober helper. Uses the library default prober until operator-supplied
 * VCI VID/PID entries are confirmed (see 036 Phase B).
 */
object UsbSerialProberFactory {

    /** Common ELM327 USB adapter product IDs paired with known bridge VIDs. */
    private val ELM327_PRODUCT_IDS = mapOf(
        UsbSerialChipIds.FTDI_VID to intArrayOf(0x6001, 0x6010, 0x6011, 0x6014, 0x6015),
        UsbSerialChipIds.CH340_VID to intArrayOf(0x7523, 0x5523, 0x5512, 0x5513),
        UsbSerialChipIds.PL2303_VID to intArrayOf(0x2303, 0x23a3, 0x23b3, 0x23c3, 0x23d3),
        UsbSerialChipIds.CP21XX_VID to intArrayOf(0xea60, 0xea61, 0xea70, 0xea71),
    )

    fun createProber(): UsbSerialProber = UsbSerialProber.getDefaultProber()

    fun isKnownElm327Usb(vendorId: Int, productId: Int): Boolean {
        val pids = ELM327_PRODUCT_IDS[vendorId] ?: return false
        return productId in pids
    }

    /**
     * Auto-connect tries FTDI first (vLinker FS and similar ELM327 USB cables), then other
     * known ELM327 bridge chips.
     */
    fun sortUsbDevicesForAutoConnect(devices: List<UsbDevice>): List<UsbDevice> =
        devices.sortedWith(compareByDescending { scoreForAutoConnect(it) })

    internal fun scoreForAutoConnect(device: UsbDevice): Int {
        var score = 0
        if (device.vendorId == UsbSerialChipIds.FTDI_VID) score += 100
        if (isKnownElm327Usb(device.vendorId, device.productId)) score += 50
        return score
    }
}
