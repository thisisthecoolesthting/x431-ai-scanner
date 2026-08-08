package com.caseforge.scanner.agent.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbSerialChipIdsTest {

    @Test
    fun classify_known_obd_chips() {
        assertEquals("CH340", UsbSerialChipIds.classify(UsbSerialChipIds.CH340_VID)!!.family)
        assertEquals("FTDI", UsbSerialChipIds.classify(UsbSerialChipIds.FTDI_VID)!!.family)
        assertEquals("PL2303", UsbSerialChipIds.classify(UsbSerialChipIds.PL2303_VID)!!.family)
        assertEquals("CP21xx", UsbSerialChipIds.classify(UsbSerialChipIds.CP21XX_VID)!!.family)
    }

    @Test
    fun classify_unknown_returns_null() {
        assertNull(UsbSerialChipIds.classify(0x9999))
    }

    @Test
    fun isKnownObdUsbChip_matches_filter_vids() {
        assertTrue(UsbSerialChipIds.isKnownObdUsbChip(0x1a86))
        assertFalse(UsbSerialChipIds.isKnownObdUsbChip(0x1234))
    }
}
