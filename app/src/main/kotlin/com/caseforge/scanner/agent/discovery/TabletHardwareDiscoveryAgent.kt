package com.caseforge.scanner.agent.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import androidx.core.content.ContextCompat
import com.caseforge.scanner.agent.ObdBluetoothTool
import com.caseforge.scanner.agent.ObdUsbTool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Read-only scan of tablet USB/BT hardware and permissions for OBD connection readiness.
 * Does not install drivers or flash modules — reports honestly what Android can and cannot do.
 */
class TabletHardwareDiscoveryAgent(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
) {

    fun scan(profileId: String? = VehicleProfileLoader.DEFAULT_WINDSTAR_ID): DiscoveryReport {
        val profile = profileId?.let { VehicleProfileLoader.load(context, it) }
        val usbTool = ObdUsbTool(context)
        val usbDevices = usbTool.listDevices()
        val devices = buildList {
            addAll(usbDevices.map { toUsbDiscovered(it, usbTool.hasPermission(it)) })
            addAll(bluetoothObdDevices())
        }

        val btEnabled = bluetoothAdapter()?.isEnabled == true
        val btConnectGranted = hasBluetoothConnectPermission()
        val permissions = PermissionSnapshot(
            bluetoothEnabled = btEnabled,
            bluetoothConnectGranted = btConnectGranted,
            usbHostAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST),
        )

        val missing = buildMissingItems(
            profile = profile,
            usbDevices = usbDevices,
            usbTool = usbTool,
            permissions = permissions,
            devices = devices,
        )

        val recommendedAction = buildRecommendedAction(
            profile = profile,
            missing = missing,
            usbDevices = usbDevices,
            usbTool = usbTool,
            permissions = permissions,
        )

        val linkSummary = profile?.linkHints?.let { hints ->
            val s = hints.serial
            "${hints.transportMode} @ ${s.baud} ${s.dataBits}${s.parity.first().uppercase()}${s.stopBits}"
        } ?: "elm327_usb or auto @ 115200 8N1"

        return DiscoveryReport(
            windstarProfileId = profile?.id ?: profileId,
            vehicleLabel = profile?.let { "${it.modelYearStart} ${it.marque} ${it.model}" },
            recommendedTier = profile?.recommendedTier ?: 0,
            devices = devices,
            permissions = permissions,
            missingItems = missing,
            recommendedAction = recommendedAction,
            linkHintsSummary = linkSummary,
            operatorSteps = profile?.operatorSteps ?: emptyList(),
        )
    }

    fun formatForAgent(report: DiscoveryReport): String = buildString {
        appendLine("=== Connection readiness ===")
        report.vehicleLabel?.let { appendLine("Vehicle: $it (profile=${report.windstarProfileId})") }
            ?: report.windstarProfileId?.let { appendLine("Vehicle profile: $it") }
        appendLine("Recommended tier: ${report.recommendedTier} (OBD trunk first)")
        appendLine("Link hints: ${report.linkHintsSummary}")
        appendLine()
        appendLine("Permissions:")
        appendLine("  USB host: ${if (report.permissions.usbHostAvailable) "yes" else "no"}")
        appendLine("  Bluetooth enabled: ${if (report.permissions.bluetoothEnabled) "yes" else "no"}")
        appendLine("  BLUETOOTH_CONNECT: ${if (report.permissions.bluetoothConnectGranted) "granted" else "missing"}")
        appendLine()
        appendLine("Devices (${report.devices.size}):")
        if (report.devices.isEmpty()) {
            appendLine("  (none — attach ELM327 USB via OTG or pair BT dongle)")
        } else {
            report.devices.forEach { d ->
                val perm = d.permissionGranted?.let { if (it) "permission=granted" else "permission=NEEDED" } ?: ""
                val chip = d.chipFamily?.let { "chip=$it" } ?: ""
                val extras = listOfNotNull(chip, perm).joinToString(" ")
                appendLine("  [${d.kind}] ${d.label} ($d.identifier)${if (extras.isNotBlank()) " — $extras" else ""}")
            }
        }
        appendLine()
        if (report.missingItems.isNotEmpty()) {
            appendLine("Missing / action needed:")
            report.missingItems.forEach { appendLine("  • $it") }
            appendLine()
        }
        appendLine("Recommended: ${report.recommendedAction}")
        appendLine()
        appendLine("Android limits: ${report.androidLimitsNote}")
        if (report.operatorSteps.isNotEmpty()) {
            appendLine()
            appendLine("Operator steps (Windstar):")
            report.operatorSteps.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
        }
    }

    fun formatJson(report: DiscoveryReport): String = json.encodeToString(report)

    private fun toUsbDiscovered(device: UsbDevice, permissionGranted: Boolean): DiscoveredDevice {
        val chip = UsbSerialChipIds.classify(device.vendorId)
        return DiscoveredDevice(
            kind = "usb",
            label = "${chip?.family ?: "USB serial"} vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)}",
            identifier = device.deviceName,
            chipFamily = chip?.family,
            permissionGranted = permissionGranted,
            obdLikely = chip != null,
        )
    }

    private fun bluetoothObdDevices(): List<DiscoveredDevice> {
        val bonded = ObdBluetoothTool.listBondedObdDevices()
        return bonded.map { (name, address) ->
            val obdLike = name.contains("OBD", ignoreCase = true) ||
                name.contains("ELM", ignoreCase = true) ||
                name.contains("VLINK", ignoreCase = true) ||
                name.contains("VEEPEAK", ignoreCase = true)
            DiscoveredDevice(
                kind = "bluetooth",
                label = name,
                identifier = address,
                obdLikely = obdLike,
            )
        }
    }

    private fun buildMissingItems(
        profile: VehicleProfile?,
        usbDevices: List<UsbDevice>,
        usbTool: ObdUsbTool,
        permissions: PermissionSnapshot,
        devices: List<DiscoveredDevice>,
    ): List<String> {
        val missing = mutableListOf<String>()
        if (!permissions.usbHostAvailable) {
            missing += "Tablet may lack USB OTG host — try Bluetooth ELM327 instead"
        }
        if (usbDevices.isEmpty() && devices.none { it.kind == "bluetooth" && it.obdLikely }) {
            missing += "No USB serial OBD adapter detected and no paired ELM327-like Bluetooth device"
        }
        usbDevices.filter { !usbTool.hasPermission(it) }.forEach { dev ->
            val chip = UsbSerialChipIds.classify(dev.vendorId)?.family ?: "USB"
            missing += "Grant USB permission for $chip adapter (${dev.deviceName})"
        }
        if (devices.any { it.kind == "bluetooth" } && !permissions.bluetoothEnabled) {
            missing += "Enable Bluetooth in Android settings"
        }
        if (devices.any { it.kind == "bluetooth" } && !permissions.bluetoothConnectGranted) {
            missing += "Grant BLUETOOTH_CONNECT to TCW (Settings → App permissions)"
        }
        profile?.notSupported?.forEach { ns ->
            missing += "Not supported on this vehicle profile: $ns"
        }
        return missing.distinct()
    }

    private fun buildRecommendedAction(
        profile: VehicleProfile?,
        missing: List<String>,
        usbDevices: List<UsbDevice>,
        usbTool: ObdUsbTool,
        permissions: PermissionSnapshot,
    ): String {
        if (missing.any { it.startsWith("Grant USB permission") }) {
            return "Tap USB permission when prompted, then connect with ${profile?.linkHints?.transportMode ?: "elm327_usb"}."
        }
        if (usbDevices.isNotEmpty() && usbDevices.all { usbTool.hasPermission(it) }) {
            val mode = profile?.linkHints?.transportMode ?: "elm327_usb"
            val baud = profile?.linkHints?.serial?.baud ?: 115200
            return "USB adapter ready — connect using $mode @ $baud 8N1; keep Tier 0 on, tiers 1–4 off for first Windstar link."
        }
        if (permissions.bluetoothEnabled && permissions.bluetoothConnectGranted) {
            return "Pair ELM327 in Android Bluetooth settings if not listed; then connect from Scan vehicle screen."
        }
        if (!permissions.bluetoothEnabled) {
            return "Attach ELM327 via USB OTG, or enable Bluetooth and pair an ELM327 dongle."
        }
        return "Attach ELM327 USB (OTG) or pair Bluetooth ELM327; enable Tier 0 Native OBD in Settings."
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
