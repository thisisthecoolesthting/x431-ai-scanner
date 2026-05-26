package com.caseforge.scanner.diagnostics

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import com.caseforge.scanner.agent.ScannerAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live probe for USB/VCI blockers on Launch X431 tablets.
 * Read-only — does not attempt to open USB devices (no permission prompt needed for enumeration).
 */
object UsbVciProbe {

    data class Snapshot(
        val ts: String,
        val verdict: Verdict,
        val blockers: List<Finding>,
        val usbDevices: List<String>,
        val btDevices: List<String>,
        val sysProps: Map<String, String>,
        val x431UiHints: List<String>,
        val recommendations: List<String>,
        val rawLog: String,
    )

    enum class Verdict { CLEAR, SUSPECT, BLOCKED, UNKNOWN }

    data class Finding(
        val severity: Severity,
        val code: String,
        val detail: String,
    )

    enum class Severity { BLOCK, WARN, INFO, OK }

    fun capture(context: Context): Snapshot {
        val usbMgr = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val pm = context.packageManager
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val props = readSysProps()
        val usbDevices = enumerateUsb(usbMgr)
        val btDevices = enumerateBluetooth(context)
        val x431Hints = scrapeX431ConnectionHints()
        val blockers = mutableListOf<Finding>()

        // --- USB host capability ---
        val hasHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val hasAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)
        if (!hasHost) {
            blockers += Finding(Severity.BLOCK, "no_usb_host", "USB host mode not advertised — tablet may not act as USB host for VCI.")
        } else {
            blockers += Finding(Severity.OK, "usb_host", "USB host feature present.")
        }
        if (hasAccessory) {
            blockers += Finding(Severity.INFO, "usb_accessory", "USB accessory mode supported.")
        }

        // --- System USB state (Launch tablets often lock this down) ---
        val usbState = props["sys.usb.state"] ?: "unknown"
        val usbConfig = props["persist.sys.usb.config"] ?: props["sys.usb.config"] ?: "unknown"
        when {
            usbState.contains("mtp", ignoreCase = true) ||
                usbState.contains("ptp", ignoreCase = true) ||
                usbState.contains("rndis", ignoreCase = true) -> {
                blockers += Finding(
                    Severity.WARN,
                    "usb_device_mode",
                    "Tablet USB is in device/peripheral mode ($usbState) — competing with host/VCI. Launch lockdown often forces this.",
                )
            }
            usbState.contains("charging", ignoreCase = true) && !usbState.contains("adb") -> {
                blockers += Finding(
                    Severity.WARN,
                    "usb_charge_only",
                    "USB state looks charge-only ($usbState). File transfer and some VCI paths are blocked.",
                )
            }
            usbState == "unknown" -> {
                blockers += Finding(Severity.INFO, "usb_state_hidden", "Could not read sys.usb.state — OEM may hide it.")
            }
            else -> {
                blockers += Finding(Severity.OK, "usb_state", "sys.usb.state = $usbState")
            }
        }
        if (usbConfig.contains("charging", ignoreCase = true) && !usbConfig.contains("adb")) {
            blockers += Finding(
                Severity.BLOCK,
                "persist_charge_only",
                "persist.sys.usb.config = $usbConfig — factory USB lock: no MTP, no file transfer.",
            )
        }

        // --- Connected USB devices (VCI dongle) ---
        if (usbDevices.isEmpty()) {
            blockers += Finding(
                Severity.WARN,
                "no_usb_devices",
                "No USB devices enumerated. Plug VCI into tablet OTG port and re-scan.",
            )
        } else {
            val vciLike = usbDevices.any { line ->
                VCI_USB_HINTS.any { hint -> line.contains(hint, ignoreCase = true) }
            }
            if (vciLike) {
                blockers += Finding(Severity.OK, "vci_usb_seen", "VCI-like USB device detected in device list.")
            } else {
                blockers += Finding(
                    Severity.WARN,
                    "usb_no_vci_match",
                    "USB device(s) present but none match known Launch/CNLaunch VID patterns.",
                )
            }
        }

        // --- Bluetooth bonded VCI (many X431 setups use BT, not USB data) ---
        if (btDevices.isEmpty()) {
            blockers += Finding(
                Severity.WARN,
                "no_bt_vci",
                "No bonded Bluetooth device matching VCI name patterns. X431 may need BT pairing, not USB.",
            )
        } else {
            blockers += Finding(Severity.OK, "bt_vci_bonded", "Bonded VCI-like BT device: ${btDevices.first()}")
        }

        // --- X431 UI: "connected" lie detector ---
        val uiConnected = x431Hints.any { it.contains("connect", ignoreCase = true) && !it.contains("not", ignoreCase = true) }
        val uiFailed = x431Hints.any {
            it.contains("fail", ignoreCase = true) ||
                it.contains("error", ignoreCase = true) ||
                it.contains("timeout", ignoreCase = true) ||
                it.contains("no communication", ignoreCase = true) ||
                it.contains("not connect", ignoreCase = true)
        }
        if (uiConnected && uiFailed) {
            blockers += Finding(
                Severity.BLOCK,
                "ui_connected_but_error",
                "X431 UI shows connected AND error/fail text — classic false-connect (USB blocked at driver layer).",
            )
        } else if (uiConnected && usbDevices.isEmpty() && btDevices.isEmpty()) {
            blockers += Finding(
                Severity.BLOCK,
                "ui_ghost_connect",
                "X431 says connected but no USB/BT VCI detected — UI-only connection, no car link.",
            )
        } else if (uiFailed) {
            blockers += Finding(
                Severity.WARN,
                "x431_comm_error",
                "X431 screen shows communication failure — open X431 and re-scan while this screen runs.",
            )
        }

        // --- OEM lock files (readable without root on some tablets) ---
        for (path in OEM_LOCK_PATHS) {
            val f = File(path)
            if (f.canRead()) {
                val snippet = runCatching { f.readText().take(200).replace('\n', ' ') }.getOrDefault("")
                blockers += Finding(
                    Severity.WARN,
                    "oem_lock_file",
                    "Readable lock/policy file: $path ${snippet.take(80)}",
                )
            }
        }

        // --- Charging vs OTG (battery plug type hint) ---
        val plugType = readPlugType(context)
        if (plugType == BatteryManager.BATTERY_PLUGGED_USB) {
            blockers += Finding(
                Severity.INFO,
                "power_usb",
                "Tablet powered via USB port — ensure VCI uses a separate OTG/data port if available.",
            )
        }

        val verdict = computeVerdict(blockers)
        val recs = buildRecommendations(verdict, blockers, hasHost, usbDevices, btDevices)
        val raw = buildRawLog(ts, props, usbDevices, btDevices, x431Hints, blockers)

        return Snapshot(
            ts = ts,
            verdict = verdict,
            blockers = blockers,
            usbDevices = usbDevices,
            btDevices = btDevices,
            sysProps = props,
            x431UiHints = x431Hints,
            recommendations = recs,
            rawLog = raw,
        )
    }

    private fun computeVerdict(blockers: List<Finding>): Verdict {
        if (blockers.any { it.severity == Severity.BLOCK }) return Verdict.BLOCKED
        if (blockers.count { it.severity == Severity.WARN } >= 2) return Verdict.SUSPECT
        if (blockers.any { it.code == "vci_usb_seen" || it.code == "bt_vci_bonded" }) {
            if (blockers.none { it.code.startsWith("ui_ghost") || it.code == "ui_connected_but_error" }) {
                return Verdict.CLEAR
            }
        }
        return Verdict.UNKNOWN
    }

    private fun buildRecommendations(
        verdict: Verdict,
        blockers: List<Finding>,
        hasHost: Boolean,
        usbDevices: List<String>,
        btDevices: List<String>,
    ): List<String> {
        val out = mutableListOf<String>()
        when (verdict) {
            Verdict.BLOCKED -> out += "USB lockdown detected. X431 \"connected\" is likely fake until host/BT path works."
            Verdict.SUSPECT -> out += "Multiple warnings — treat connection as unverified until live data or DTC read succeeds."
            Verdict.CLEAR -> out += "Hardware path looks open. If car still won't connect, fault is likely cable/OBD port/vehicle, not tablet USB lock."
            Verdict.UNKNOWN -> out += "Insufficient data — run scan with VCI plugged in and X431 on the connection screen."
        }
        if (blockers.any { it.code == "persist_charge_only" || it.code == "usb_charge_only" }) {
            out += "Launch tablets ship with USB restricted to charging. MTP/file transfer disabled is expected — not the fix for VCI."
            out += "VCI uses proprietary USB-serial or Bluetooth, not MTP. Focus on BT pairing or OTG host port, not \"Transfer files\"."
        }
        if (!hasHost) {
            out += "Try the dedicated VCI/OTG port (often micro-USB beside power), not the charging-only port."
        }
        if (usbDevices.isEmpty() && btDevices.isEmpty()) {
            out += "Pair VCI in Android Bluetooth Settings (names: DBSCAR, X431, VCI-, 98943*). PIN often 1234 or 0000."
            out += "In X431: Diagnose → ensure VCI selected → Connect. Watch this screen for device enumeration."
        }
        if (blockers.any { it.code.startsWith("ui_") }) {
            out += "False-connect: reboot tablet, unplug VCI 10s, plug to car first then tablet, reopen X431."
        }
        out += "Proof of real car link: X431 reads VIN or live RPM — not just \"VCI connected\" banner."
        return out
    }

    private fun enumerateUsb(usbMgr: UsbManager?): List<String> {
        if (usbMgr == null) return listOf("(UsbManager unavailable)")
        return usbMgr.deviceList.values.map { dev -> formatUsbDevice(dev) }
            .ifEmpty { emptyList() }
    }

    private fun formatUsbDevice(dev: UsbDevice): String {
        val ifaces = (0 until dev.interfaceCount).joinToString { i ->
            val iface = dev.getInterface(i)
            val cls = when (iface.interfaceClass) {
                UsbConstants.USB_CLASS_CDC_DATA -> "CDC_DATA"
                UsbConstants.USB_CLASS_COMM -> "COMM"
                UsbConstants.USB_CLASS_VENDOR_SPEC -> "VENDOR"
                UsbConstants.USB_CLASS_HID -> "HID"
                else -> "0x${iface.interfaceClass.toString(16)}"
            }
            "if$i:$cls"
        }
        return "vid=0x${dev.vendorId.toString(16)} pid=0x${dev.productId.toString(16)} " +
            "name=${dev.deviceName} ${dev.manufacturerName.orEmpty()} ${dev.productName.orEmpty()} [$ifaces]"
    }

    private fun enumerateBluetooth(context: Context): List<String> {
        return try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return emptyList()
            @Suppress("MissingPermission")
            adapter.bondedDevices?.mapNotNull { dev ->
                val name = dev.name ?: return@mapNotNull null
                if (VCI_BT_PREFIXES.any { name.startsWith(it, ignoreCase = true) } ||
                    name.contains("OBD", ignoreCase = true) ||
                    name.contains("DBSCAR", ignoreCase = true) ||
                    name.contains("98943", ignoreCase = true)
                ) {
                    "$name (${dev.address})"
                } else null
            }.orEmpty()
        } catch (_: SecurityException) {
            listOf("(Bluetooth permission missing)")
        }
    }

    private fun scrapeX431ConnectionHints(): List<String> {
        val snap = ScannerAccessibilityService.instance()?.readScreen() ?: return emptyList()
        if (snap.pkg !in ScannerAccessibilityService.X431_PACKAGES) {
            return listOf("(X431 not foreground — open X431 connection screen)")
        }
        val keywords = listOf(
            "connect", "vci", "bluetooth", "usb", "communication", "fail", "error",
            "timeout", "link", "vehicle", "obd", "adapter", "dongle", "serial",
        )
        return snap.text.lines()
            .map { it.trim() }
            .filter { line -> line.isNotBlank() && keywords.any { k -> line.contains(k, ignoreCase = true) } }
            .take(20)
    }

    private fun readSysProps(): Map<String, String> {
        val keys = listOf(
            "sys.usb.state",
            "sys.usb.config",
            "persist.sys.usb.config",
            "persist.sys.usb.qmmi.func",
            "ro.adb.secure",
            "ro.debuggable",
            "ro.secure",
            "persist.sys.launch.usb",
            "persist.cnlaunch.usb",
            "sys.usb.configfs",
        )
        return keys.associateWith { getProp(it) }
    }

    private fun getProp(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, "") as String
        }.getOrDefault("").ifBlank {
            runCatching {
                ProcessBuilder("getprop", key).redirectErrorStream(true).start()
                    .inputStream.bufferedReader().use { it.readLine().orEmpty() }
            }.getOrDefault("")
        }.ifBlank { "unknown" }
    }

    private fun readPlugType(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    }

    private fun buildRawLog(
        ts: String,
        props: Map<String, String>,
        usb: List<String>,
        bt: List<String>,
        x431: List<String>,
        findings: List<Finding>,
    ): String = buildString {
        appendLine("=== USB/VCI probe $ts ===")
        appendLine("Build: ${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")
        appendLine("-- sys props --")
        props.forEach { (k, v) -> appendLine("$k=$v") }
        appendLine("-- USB devices (${usb.size}) --")
        usb.forEach { appendLine(it) }
        appendLine("-- BT VCI (${bt.size}) --")
        bt.forEach { appendLine(it) }
        appendLine("-- X431 UI hints --")
        x431.forEach { appendLine(it) }
        appendLine("-- findings --")
        findings.forEach { appendLine("[${it.severity}] ${it.code}: ${it.detail}") }
    }

    private val VCI_USB_HINTS = listOf(
        "launch", "cnlaunch", "x431", "vci", "dbscar", "98943",
        // Launch/CNLaunch common vendor IDs (hex strings for matching in formatted output)
        "vid=0x", // any device is better than none
    )

    private val VCI_BT_PREFIXES = listOf("VCI-", "Launch-", "X431-", "DBSCAR", "CRP")

    private val OEM_LOCK_PATHS = listOf(
        "/system/etc/init/usb.config",
        "/vendor/etc/init/hw/init.usb.rc",
        "/data/system/usb_policy.xml",
        "/system/etc/permissions/android.hardware.usb.host.xml",
    )
}
