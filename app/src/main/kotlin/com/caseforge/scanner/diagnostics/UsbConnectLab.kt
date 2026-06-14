package com.caseforge.scanner.diagnostics

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Active USB connection diagnostic ("USB Connect Lab").
 *
 * Unlike [UsbVciProbe] (read-only enumeration + OEM-lockdown sysprop analysis), this *actively*
 * climbs a connection ladder and reports exactly where the wired path dies on a locked tablet:
 *   1. Raw USB enumeration (UsbManager.deviceList) — does the OS see ANY device on the port?
 *   2. Per-interface class breakdown — is there a CDC/vendor serial interface to talk to?
 *   3. Permission request — does the grant dialog even appear, and is it granted?
 *   4. Multi-driver open — try every known serial driver + a forced CDC fallback.
 *   5. Raw control/bulk endpoint claim — prove the kernel will hand us the device at all.
 *   6. ELM327 handshake (ATZ/ATI/ATRV) — real bytes in/out over the opened port.
 *
 * Every rung emits a [Step] so the UI shows a live ladder and a copyable log.
 */
object UsbConnectLab {

    enum class Result { PASS, FAIL, WARN, SKIP, INFO }

    data class Step(
        val name: String,
        val result: Result,
        val detail: String,
        val elapsedMs: Long = 0,
    )

    data class Report(
        val ts: String,
        val device: String?,
        val steps: List<Step>,
        val handshake: String?,
        val verdict: String,
        val nextActions: List<String>,
        val rawLog: String,
    )

    private const val ACTION_PERM = "com.caseforge.scanner.USBLAB_PERMISSION"

    /** The three transports the lab can test. */
    enum class Transport(val label: String) {
        USB("USB / built-in cable"),
        BLUETOOTH("Bluetooth ELM327"),
        OEM_VCI("OEM VCI (factory app)"),
    }

    // ELM327 baud rates worth trying, fastest-first (most clones are 38400 or 115200).
    private val BAUDS = intArrayOf(115200, 38400, 9600, 57600, 230400)

    /**
     * Test every transport back to back and return one report per transport.
     */
    suspend fun runAll(context: Context, onStep: (Transport, Step) -> Unit): Map<Transport, Report> {
        val out = LinkedHashMap<Transport, Report>()
        out[Transport.USB] = runUsb(context) { onStep(Transport.USB, it) }
        out[Transport.BLUETOOTH] = runBluetooth(context) { onStep(Transport.BLUETOOTH, it) }
        out[Transport.OEM_VCI] = runOemVci(context) { onStep(Transport.OEM_VCI, it) }
        return out
    }

    /**
     * Run the full USB ladder. [onStep] is called as each rung completes so the UI updates live.
     * Suspends; safe to cancel.
     */
    suspend fun runUsb(context: Context, onStep: (Step) -> Unit): Report {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val steps = mutableListOf<Step>()
        val log = StringBuilder()
        fun emit(s: Step) {
            steps += s
            log.appendLine("[${s.result}] ${s.name} — ${s.detail}")
            onStep(s)
        }

        log.appendLine("=== USB Connect Lab $ts ===")
        log.appendLine("Tablet: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usb == null) {
            emit(Step("USB service", Result.FAIL, "UsbManager unavailable — OS denies USB host service."))
            return finish(ts, null, steps, null, log)
        }

        // Rung 1: raw enumeration
        val t1 = System.currentTimeMillis()
        val rawDevices = usb.deviceList.values.toList()
        if (rawDevices.isEmpty()) {
            emit(
                Step(
                    "Enumerate USB", Result.FAIL,
                    "OS sees NO USB device on the port. Either the cable/VCI is on a charge-only port, " +
                        "OTG/host mode is disabled, or the port is hardware-blocked.",
                    System.currentTimeMillis() - t1,
                ),
            )
            // still run the lockdown probe for sysprop evidence
            appendLockdownEvidence(context, log, ::emit)
            return finish(ts, null, steps, null, log)
        }
        emit(
            Step(
                "Enumerate USB", Result.PASS,
                "OS sees ${rawDevices.size} USB device(s) on the port.",
                System.currentTimeMillis() - t1,
            ),
        )

        // Pick the most serial-looking device.
        val target = pickTarget(rawDevices)
        val devLabel = describe(target)
        log.appendLine("Target: $devLabel")

        // Rung 2: interface class breakdown
        val ifaceSummary = (0 until target.interfaceCount).joinToString(", ") { i ->
            val itf = target.getInterface(i)
            "if$i=${className(itf.interfaceClass)}/${itf.endpointCount}ep"
        }
        val hasSerialIface = (0 until target.interfaceCount).any {
            val c = target.getInterface(it).interfaceClass
            c == UsbConstants.USB_CLASS_CDC_DATA || c == UsbConstants.USB_CLASS_COMM || c == UsbConstants.USB_CLASS_VENDOR_SPEC
        }
        emit(
            Step(
                "Inspect interfaces",
                if (hasSerialIface) Result.PASS else Result.WARN,
                "vid=0x${target.vendorId.toString(16)} pid=0x${target.productId.toString(16)} | $ifaceSummary" +
                    if (hasSerialIface) "" else " — no obvious serial (CDC/vendor) interface.",
            ),
        )

        // Rung 3: permission (this is where lockdown tablets silently fail — no dialog)
        val tPerm = System.currentTimeMillis()
        val granted = if (usb.hasPermission(target)) {
            emit(Step("USB permission", Result.PASS, "Already granted for this device."))
            true
        } else {
            emit(Step("USB permission", Result.INFO, "Requesting permission — watch for the system dialog…"))
            val ok = requestPermission(context, usb, target)
            emit(
                Step(
                    "USB permission", if (ok) Result.PASS else Result.FAIL,
                    if (ok) "Granted." else
                        "NOT granted. If no dialog appeared, the tablet's USB policy is blocking app access " +
                            "to the port (classic OEM lockdown).",
                    System.currentTimeMillis() - tPerm,
                ),
            )
            ok
        }

        if (!granted) {
            appendLockdownEvidence(context, log, ::emit)
            return finish(ts, devLabel, steps, null, log)
        }

        // Rung 4: open the raw connection (proves kernel hands us the fd)
        val conn: UsbDeviceConnection? = usb.openDevice(target)
        if (conn == null) {
            emit(
                Step(
                    "Open device", Result.FAIL,
                    "openDevice() returned null even with permission — driver/kernel refused the handle. " +
                        "Strong sign of a USB host restriction on this tablet.",
                ),
            )
            appendLockdownEvidence(context, log, ::emit)
            return finish(ts, devLabel, steps, null, log)
        }
        emit(Step("Open device", Result.PASS, "Raw USB connection handle obtained."))

        // Rung 5: multi-driver serial open + ELM327 handshake
        val drivers = candidateDrivers(target)
        log.appendLine("Driver candidates: ${drivers.joinToString { it.javaClass.simpleName }}")
        var handshake: String? = null
        var opened = false

        for (driver in drivers) {
            val port = driver.ports.firstOrNull() ?: continue
            for (baud in BAUDS) {
                val tOpen = System.currentTimeMillis()
                val res = runCatching {
                    port.open(conn)
                    port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    runCatching { port.dtr = true; port.rts = true }
                }
                if (res.isFailure) {
                    runCatching { port.close() }
                    continue
                }
                // try a handshake
                val hs = elmHandshake(port)
                if (hs != null && hs.bytesIn > 0) {
                    opened = true
                    handshake = hs.transcript
                    emit(
                        Step(
                            "Serial open + handshake", Result.PASS,
                            "${driver.javaClass.simpleName} @ ${baud}baud — ${hs.bytesIn} bytes in. " +
                                "Adapter responded: ${hs.id}",
                            System.currentTimeMillis() - tOpen,
                        ),
                    )
                    runCatching { port.close() }
                    break
                } else {
                    runCatching { port.close() }
                }
            }
            if (opened) break
        }

        if (!opened) {
            emit(
                Step(
                    "Serial open + handshake", Result.FAIL,
                    "Port opened but the adapter never answered ATZ/ATI on any driver/baud. " +
                        "Either it's not an ELM327-class adapter, the OBD port has no power (key off), " +
                        "or the USB data lines are blocked while power passes.",
                ),
            )
            appendLockdownEvidence(context, log, ::emit)
        }

        runCatching { conn.close() }
        return finish(ts, devLabel, steps, handshake, log)
    }

    /**
     * Bluetooth ELM327 ladder:
     *   1. Adapter present + enabled?
     *   2. Permissions (BLUETOOTH_CONNECT on API 31+)?
     *   3. Any bonded OBD-like device?
     *   4. RFCOMM/SPP connect + ELM327 handshake (real bytes over the air).
     */
    suspend fun runBluetooth(context: Context, onStep: (Step) -> Unit): Report {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val steps = mutableListOf<Step>()
        val log = StringBuilder()
        fun emit(s: Step) {
            steps += s
            log.appendLine("[${s.result}] ${s.name} — ${s.detail}")
            onStep(s)
        }
        log.appendLine("=== Bluetooth Connect Lab $ts ===")

        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            emit(Step("BT adapter", Result.FAIL, "No Bluetooth adapter on this tablet."))
            return finishBt(ts, steps, null, log)
        }
        if (!adapter.isEnabled) {
            emit(Step("BT adapter", Result.FAIL, "Bluetooth is OFF — turn it on in Android settings, then re-run."))
            return finishBt(ts, steps, null, log)
        }
        emit(Step("BT adapter", Result.PASS, "Bluetooth adapter present and enabled."))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            emit(
                Step(
                    "BT permission", if (granted) Result.PASS else Result.WARN,
                    if (granted) "BLUETOOTH_CONNECT granted."
                    else "BLUETOOTH_CONNECT not granted — grant it for device names/connect.",
                ),
            )
        }

        val bonded = runCatching {
            @Suppress("MissingPermission") adapter.bondedDevices?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val obdLike = bonded.filter { d ->
            val n = (runCatching { @Suppress("MissingPermission") d.name }.getOrNull() ?: "").uppercase()
            n.contains("OBD") || n.contains("ELM") || n.contains("VLINK") || n.contains("VEEPEAK") ||
                n.contains("VGATE") || n.contains("DBSCAR") || n.contains("VCI") || n.startsWith("V-LINK")
        }
        if (bonded.isEmpty()) {
            emit(Step("Paired devices", Result.FAIL, "No bonded Bluetooth devices. Pair your ELM327 in Android Settings (PIN 1234/0000)."))
            return finishBt(ts, steps, null, log)
        }
        if (obdLike.isEmpty()) {
            emit(
                Step(
                    "Paired devices", Result.WARN,
                    "${bonded.size} paired device(s) but none look like an OBD adapter. " +
                        "Will still try them all.",
                ),
            )
        } else {
            emit(
                Step(
                    "Paired devices", Result.PASS,
                    "Found OBD-like adapter: " + obdLike.joinToString {
                        runCatching { @Suppress("MissingPermission") it.name }.getOrNull() ?: it.address
                    },
                ),
            )
        }

        // Try connecting via the shared tool, which does SPP RFCOMM + reflection fallback.
        val tConn = System.currentTimeMillis()
        val status = runCatching {
            com.caseforge.scanner.agent.ObdBluetoothTool.scanAndConnect()
        }.getOrElse { "connect threw: ${it.message}" }
        val connected = runCatching { com.caseforge.scanner.agent.ObdBluetoothTool.isConnected() }.getOrDefault(false)

        if (!connected) {
            emit(
                Step(
                    "SPP connect + handshake", Result.FAIL,
                    "RFCOMM connect failed: $status. Adapter may be out of range, off, or already bonded to another phone.",
                    System.currentTimeMillis() - tConn,
                ),
            )
            return finishBt(ts, steps, null, log)
        }

        // Confirm real link with voltage + protocol probe.
        val volt = runCatching { com.caseforge.scanner.agent.ObdBluetoothTool.readPid("42") }.getOrDefault("")
        val name = runCatching { com.caseforge.scanner.agent.ObdBluetoothTool.connectedDeviceName() }.getOrNull() ?: "adapter"
        val transcript = "Connected to $name\n$status\nControl-module voltage PID42: $volt"
        emit(
            Step(
                "SPP connect + handshake", Result.PASS,
                "Connected to $name and adapter answered. $status",
                System.currentTimeMillis() - tConn,
            ),
        )
        runCatching { com.caseforge.scanner.agent.ObdBluetoothTool.disconnect() }
        return finishBt(ts, steps, transcript, log)
    }

    /**
     * OEM VCI path: we can't open the factory VCI ourselves, but we can detect whether the OEM
     * diagnostic app + its accessibility hook are present and whether a VCI is visible to it.
     * Leans on [UsbVciProbe] for the heavy lifting.
     */
    suspend fun runOemVci(context: Context, onStep: (Step) -> Unit): Report {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val steps = mutableListOf<Step>()
        val log = StringBuilder()
        fun emit(s: Step) {
            steps += s
            log.appendLine("[${s.result}] ${s.name} — ${s.detail}")
            onStep(s)
        }
        log.appendLine("=== OEM VCI Lab $ts ===")

        val probe = runCatching { UsbVciProbe.capture(context) }.getOrNull()
        if (probe == null) {
            emit(Step("OEM probe", Result.FAIL, "Probe failed to run."))
            return finishBt(ts, steps, null, log)
        }
        log.appendLine(probe.rawLog)

        // USB VCI seen?
        val usbVci = probe.blockers.any { it.code == "vci_usb_seen" }
        val btVci = probe.blockers.any { it.code == "bt_vci_bonded" }
        emit(
            Step(
                "VCI visible", if (usbVci || btVci) Result.PASS else Result.WARN,
                when {
                    usbVci -> "A VCI-like USB device is enumerated."
                    btVci -> "A VCI-like Bluetooth device is bonded."
                    else -> "No OEM VCI detected on USB or Bluetooth yet."
                },
            ),
        )

        // Ghost-connect / lockdown findings
        val ghost = probe.blockers.firstOrNull { it.code.startsWith("ui_") }
        if (ghost != null) {
            emit(Step("OEM app state", Result.FAIL, ghost.detail))
        }
        val hardBlocks = probe.blockers.filter { it.severity == UsbVciProbe.Severity.BLOCK }
        if (hardBlocks.isNotEmpty()) {
            emit(Step("Lockdown analysis", Result.FAIL, hardBlocks.joinToString("; ") { it.detail }))
        } else {
            emit(Step("Lockdown analysis", Result.INFO, "Probe verdict: ${probe.verdict}."))
        }

        val rep = Report(
            ts = ts,
            device = null,
            steps = steps,
            handshake = null,
            verdict = "OEM VCI verdict: ${probe.verdict}. " +
                (probe.recommendations.firstOrNull() ?: ""),
            nextActions = probe.recommendations,
            rawLog = log.toString(),
        )
        return rep
    }

    private fun finishBt(ts: String, steps: List<Step>, handshake: String?, log: StringBuilder): Report {
        val next = mutableListOf<String>()
        val verdict: String
        when {
            handshake != null -> {
                verdict = "BLUETOOTH WORKS — adapter answered over the air."
                next += "Use the Bluetooth path in the main app; it's confirmed good on this tablet."
            }
            steps.any { it.name == "Paired devices" && it.result == Result.FAIL } -> {
                verdict = "NO ADAPTER PAIRED."
                next += "Pair the ELM327 in Android Settings → Bluetooth (PIN 1234 or 0000), then re-run."
            }
            steps.any { it.name == "BT adapter" && it.result == Result.FAIL } -> {
                verdict = "BLUETOOTH OFF / UNAVAILABLE."
                next += "Turn Bluetooth on and re-run."
            }
            else -> {
                verdict = "BLUETOOTH CONNECT FAILED."
                next += "Make sure the adapter is powered (plugged into the OBD port, key ON) and not bonded to a phone."
            }
        }
        return Report(ts, null, steps, handshake, verdict, next, log.toString())
    }

    // ---- helpers ----

    private data class Handshake(val id: String, val bytesIn: Int, val transcript: String)

    private suspend fun elmHandshake(port: UsbSerialPort): Handshake? {
        val transcript = StringBuilder()
        var totalIn = 0
        var id = ""
        suspend fun cmd(c: String): String {
            runCatching {
                port.write((c + "\r").toByteArray(Charsets.US_ASCII), 600)
            }.onFailure { return "" }
            val buf = ByteArray(256)
            val sb = StringBuilder()
            val out = withTimeoutOrNull(1500) {
                val deadline = System.currentTimeMillis() + 1200
                while (System.currentTimeMillis() < deadline) {
                    val n = runCatching { port.read(buf, 300) }.getOrDefault(0)
                    if (n > 0) {
                        totalIn += n
                        sb.append(String(buf, 0, n, Charsets.US_ASCII))
                        if (sb.contains('>')) break
                    } else {
                        delay(20)
                    }
                }
                sb.toString()
            } ?: ""
            transcript.append("> $c\n$out\n")
            return out
        }
        // reset, identify, voltage
        cmd("ATZ")
        val ati = cmd("ATI")
        id = ati.replace("\r", " ").replace(">", "").trim().ifBlank { "(no id)" }
        cmd("ATE0")
        cmd("ATRV")
        cmd("0100")
        return if (totalIn > 0) Handshake(id, totalIn, transcript.toString()) else null
    }

    private fun candidateDrivers(device: UsbDevice): List<UsbSerialDriver> {
        // First the default prober (vid/pid table). Then force each known driver, and a CDC fallback
        // for adapters that don't advertise CDC but speak it (many OBD clones).
        val out = mutableListOf<UsbSerialDriver>()
        val mgrProber = UsbSerialProber.getDefaultProber()
        mgrProber.probeDevice(device)?.let { out += it }

        fun tryDriver(make: (UsbDevice) -> UsbSerialDriver) {
            runCatching { make(device) }.getOrNull()?.let { d ->
                if (out.none { it.javaClass == d.javaClass }) out += d
            }
        }
        tryDriver { CdcAcmSerialDriver(it) }
        tryDriver { FtdiSerialDriver(it) }
        tryDriver { ProlificSerialDriver(it) }
        tryDriver { Ch34xSerialDriver(it) }
        tryDriver { Cp21xxSerialDriver(it) }

        // Custom prober that maps ANY vid/pid to CDC-ACM (last-ditch for unknown OBD clones).
        val table = ProbeTable().apply {
            addProduct(device.vendorId, device.productId, CdcAcmSerialDriver::class.java)
        }
        UsbSerialProber(table).probeDevice(device)?.let { d ->
            if (out.none { it.javaClass == d.javaClass && it.device.productId == d.device.productId }) out += d
        }
        return out
    }

    private fun pickTarget(devices: List<UsbDevice>): UsbDevice {
        // Prefer a device exposing a serial-ish interface; fall back to the first.
        return devices.firstOrNull { dev ->
            (0 until dev.interfaceCount).any {
                val c = dev.getInterface(it).interfaceClass
                c == UsbConstants.USB_CLASS_CDC_DATA || c == UsbConstants.USB_CLASS_COMM ||
                    c == UsbConstants.USB_CLASS_VENDOR_SPEC
            }
        } ?: devices.first()
    }

    private suspend fun requestPermission(context: Context, usb: UsbManager, device: UsbDevice): Boolean =
        withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        if (intent.action != ACTION_PERM) return
                        runCatching { context.unregisterReceiver(this) }
                        val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(ok)
                    }
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else 0
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, IntentFilter(ACTION_PERM), flags)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        context.registerReceiver(receiver, IntentFilter(ACTION_PERM))
                    }
                }
                val pi = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_PERM).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                usb.requestPermission(device, pi)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            }
        } ?: false

    private fun appendLockdownEvidence(context: Context, log: StringBuilder, emit: (Step) -> Unit) {
        val probe = runCatching { UsbVciProbe.capture(context) }.getOrNull() ?: return
        log.appendLine("-- OEM lockdown probe --")
        log.appendLine(probe.rawLog)
        val blocks = probe.blockers.filter { it.severity == UsbVciProbe.Severity.BLOCK }
        if (blocks.isNotEmpty()) {
            emit(
                Step(
                    "Lockdown analysis", Result.FAIL,
                    "Probe verdict: ${probe.verdict}. " + blocks.joinToString("; ") { it.detail },
                ),
            )
        } else {
            emit(Step("Lockdown analysis", Result.INFO, "Probe verdict: ${probe.verdict}. No hard USB-policy block found in syprops."))
        }
    }

    private fun finish(
        ts: String,
        device: String?,
        steps: List<Step>,
        handshake: String?,
        log: StringBuilder,
    ): Report {
        val failed = steps.filter { it.result == Result.FAIL }
        val verdict: String
        val next = mutableListOf<String>()
        when {
            handshake != null -> {
                verdict = "USB PATH WORKS — adapter answered over the cable."
                next += "Connection is good. If the main app still won't connect, it's a software path issue, not the port."
            }
            steps.none { it.name == "Enumerate USB" && it.result == Result.PASS } -> {
                verdict = "PORT DEAD — OS sees no device."
                next += "Try the other USB port (OEM tablets often have a dedicated VCI/OTG port separate from charge-only)."
                next += "Use a known-good OTG cable; test the same cable on a phone to rule out the cable."
                next += "If a built-in cable: it may route to the OEM VCI chip, not host USB — Bluetooth may be the only app path."
            }
            failed.any { it.name == "USB permission" } -> {
                verdict = "PERMISSION BLOCKED — tablet policy denies app USB access."
                next += "This is the lockdown. The OEM firmware reserves USB for its own app."
                next += "Workaround: use the Bluetooth ELM327 path instead of wired."
                next += "If rooted: persist.sys.usb.config may be forcing device-mode; that's the lock."
            }
            failed.any { it.name == "Open device" } -> {
                verdict = "OPEN REFUSED — kernel won't hand over the device."
                next += "USB host is restricted at the driver layer. Bluetooth is the reliable path on this tablet."
            }
            failed.any { it.name.startsWith("Serial open") } -> {
                verdict = "DATA LINES SILENT — power passes, data doesn't."
                next += "Turn the ignition to ON (OBD port is unpowered with key off)."
                next += "Confirm it's an ELM327-class adapter; the built-in OEM cable may not be."
                next += "Try the Bluetooth adapter to isolate cable vs. tablet."
            }
            else -> {
                verdict = "INCONCLUSIVE — see steps."
                next += "Share the log so we can pinpoint the rung that failed."
            }
        }
        return Report(
            ts = ts,
            device = device,
            steps = steps,
            handshake = handshake,
            verdict = verdict,
            nextActions = next,
            rawLog = log.toString(),
        )
    }

    private fun describe(d: UsbDevice): String =
        "vid=0x${d.vendorId.toString(16)} pid=0x${d.productId.toString(16)} " +
            "${d.manufacturerName.orEmpty()} ${d.productName.orEmpty()}".trim()

    private fun className(c: Int): String = when (c) {
        UsbConstants.USB_CLASS_CDC_DATA -> "CDC_DATA"
        UsbConstants.USB_CLASS_COMM -> "COMM"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "VENDOR"
        UsbConstants.USB_CLASS_HID -> "HID"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "MASS_STORAGE"
        UsbConstants.USB_CLASS_HUB -> "HUB"
        else -> "0x${c.toString(16)}"
    }
}
