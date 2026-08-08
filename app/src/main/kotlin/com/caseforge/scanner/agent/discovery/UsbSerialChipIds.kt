package com.caseforge.scanner.agent.discovery

/**
 * Known USB-UART chips used by ELM327 USB-OBD adapters (matches [usb_device_filter.xml]).
 */
object UsbSerialChipIds {

    const val FTDI_VID = 0x0403
    const val CH340_VID = 0x1a86
    const val PL2303_VID = 0x067b
    const val CP21XX_VID = 0x10c4

    data class ChipMatch(val family: String, val typicalUse: String)

    fun classify(vendorId: Int): ChipMatch? = when (vendorId) {
        FTDI_VID -> ChipMatch("FTDI", "ELM327 USB / FT232")
        CH340_VID -> ChipMatch("CH340", "ELM327 USB (common clone)")
        PL2303_VID -> ChipMatch("PL2303", "ELM327 USB / Prolific")
        CP21XX_VID -> ChipMatch("CP21xx", "ELM327 USB / Silicon Labs")
        else -> null
    }

    fun isKnownObdUsbChip(vendorId: Int): Boolean = classify(vendorId) != null
}
