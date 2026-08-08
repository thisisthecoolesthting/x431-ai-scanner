package com.caseforge.scanner.agent.discovery

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryReport(
    val windstarProfileId: String? = null,
    val vehicleLabel: String? = null,
    val recommendedTier: Int = 0,
    val devices: List<DiscoveredDevice> = emptyList(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val missingItems: List<String> = emptyList(),
    val recommendedAction: String = "",
    val linkHintsSummary: String = "",
    val androidLimitsNote: String = ANDROID_USB_LIMIT_NOTE,
    val operatorSteps: List<String> = emptyList(),
) {
    companion object {
        const val ANDROID_USB_LIMIT_NOTE =
            "Android uses in-process USB-serial (usb-serial-for-android); it cannot install " +
                "Windows CH340/FTDI kernel drivers. Use USB OTG + grant permission, or a paired BT ELM327."
    }
}

@Serializable
data class DiscoveredDevice(
    val kind: String,
    val label: String,
    val identifier: String,
    val chipFamily: String? = null,
    val permissionGranted: Boolean? = null,
    val obdLikely: Boolean = false,
)

@Serializable
data class PermissionSnapshot(
    val bluetoothEnabled: Boolean = false,
    val bluetoothConnectGranted: Boolean = false,
    val usbHostAvailable: Boolean = true,
)
