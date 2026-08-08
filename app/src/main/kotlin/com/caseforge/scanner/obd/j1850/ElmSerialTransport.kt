package com.caseforge.scanner.obd.j1850

/**
 * Abstraction over an ELM327-compatible serial/USB adapter (e.g. vLinker,
 * 115200 baud). Pure Kotlin/JVM - no Android imports - so the
 * orchestration logic in this module can be unit tested without a phone
 * or hardware attached.
 *
 * A real Android implementation backs this with a UsbSerialPort /
 * BluetoothSocket stream; [FakeElmTransport] backs it with scripted
 * fixture responses for tests.
 */
interface ElmSerialTransport {

    /** Opens the underlying serial connection. Must be called before [sendCommand]. */
    fun open()

    /**
     * Writes [cmd] followed by a carriage return ("\r") to the adapter,
     * then reads and returns everything the adapter sends back up to (but
     * not including) the trailing ELM ">" input-ready prompt.
     *
     * The returned string may contain multiple CR-separated lines (multiple
     * bus frames), or a single ELM status token such as "OK", "NO DATA",
     * "UNABLE TO CONNECT", "?", "STOPPED", "BUS INIT: ERROR".
     */
    fun sendCommand(cmd: String): String

    /** Closes the underlying serial connection. Safe to call more than once. */
    fun close()
}

/** Outcome of [elmInitVpw]. */
data class ElmInitResult(
    val success: Boolean,
    val protocolConfirmed: Boolean,
    val adapterIdLine: String?,
    val steps: List<ElmInitStep>,
    val failureReason: String? = null
)

/** One command/response pair issued during [elmInitVpw], kept for diagnostics/logging. */
data class ElmInitStep(val command: String, val response: String)

/**
 * Standard ELM327 -> J1850 VPW bring-up sequence used by SkimVpwReader
 * before any SKIM request is sent:
 *
 *  1. ATZ    - full reset (adapter replies with its ID/version banner)
 *  2. ATE0   - echo off (so responses don't repeat the command we sent)
 *  3. ATL0   - linefeeds off (CR-only line separation)
 *  4. ATS0   - spaces off (compact hex, "B1014B" not "B1 01 4B")
 *  5. ATH1   - headers on (show header/PCI-ID + CRC bytes, not just data -
 *              required to identify which module/PCI-ID a frame belongs to)
 *  6. ATSP2  - select protocol 2 = SAE J1850 VPW, 10.4 kbps
 *  7. ATDPN  - "describe protocol number", used to verify the adapter is
 *              actually sitting in VPW (should reply "2") rather than
 *              having silently failed or fallen back.
 *
 * Reference: ELM327 AT command set - Elm Electronics ELM327 datasheet,
 * https://www.elmelectronics.com/wp-content/uploads/2016/07//ELM327DS.pdf
 * and https://en.wikipedia.org/wiki/ELM327
 *
 * Each setup step's response is expected to contain "OK" (ELM327
 * convention for AT commands that don't return data). If any step fails
 * that check, or the adapter doesn't answer the reset at all, or ATDPN
 * doesn't confirm VPW, initialization is aborted immediately and
 * [ElmInitResult.success] is false - callers should not proceed to send
 * any bus request in that case.
 */
fun ElmSerialTransport.elmInitVpw(): ElmInitResult {
    val steps = mutableListOf<ElmInitStep>()

    fun step(cmd: String): String {
        val response = sendCommand(cmd)
        steps.add(ElmInitStep(cmd, response))
        return response
    }

    val resetResponse = step("ATZ")
    if (resetResponse.isBlank()) {
        return ElmInitResult(
            success = false,
            protocolConfirmed = false,
            adapterIdLine = null,
            steps = steps,
            failureReason = "No response to ATZ reset - adapter not answering."
        )
    }

    val setupSteps = listOf("ATE0", "ATL0", "ATS0", "ATH1", ObdBusProtocol.J1850_VPW.atspCommand)
    for (cmd in setupSteps) {
        val response = step(cmd)
        if (!response.contains("OK", ignoreCase = true)) {
            return ElmInitResult(
                success = false,
                protocolConfirmed = false,
                adapterIdLine = resetResponse.trim(),
                steps = steps,
                failureReason = "Command '$cmd' was not acknowledged with OK (got: '${response.trim()}')."
            )
        }
    }

    val protocolReport = step("ATDPN").trim()
    val protocolConfirmed = protocolReport.contains(ObdBusProtocol.J1850_VPW.elmProtocolChar)
    if (!protocolConfirmed) {
        return ElmInitResult(
            success = false,
            protocolConfirmed = false,
            adapterIdLine = resetResponse.trim(),
            steps = steps,
            failureReason = "Adapter did not confirm J1850 VPW (ATDPN returned '$protocolReport', expected to contain '2')."
        )
    }

    return ElmInitResult(
        success = true,
        protocolConfirmed = true,
        adapterIdLine = resetResponse.trim(),
        steps = steps,
        failureReason = null
    )
}

/**
 * Test double for [ElmSerialTransport] that replays scripted fixture
 * responses instead of talking to real hardware. Matching is by exact
 * command text first, then by prefix (useful if a fixture author wants to
 * match a command regardless of trailing arguments/whitespace), falling
 * back to [defaultResponse] if nothing matches.
 */
class FakeElmTransport(
    private val scriptedResponses: Map<String, String> = emptyMap(),
    private val defaultResponse: String = "NO DATA"
) : ElmSerialTransport {

    private var opened = false

    /** Every command sent via [sendCommand], in order - for test assertions. */
    val sentCommands: MutableList<String> = mutableListOf()

    var closeCallCount: Int = 0
        private set

    override fun open() {
        opened = true
    }

    override fun sendCommand(cmd: String): String {
        check(opened) { "sendCommand('$cmd') called before open()" }
        sentCommands.add(cmd)
        scriptedResponses[cmd]?.let { return it }
        scriptedResponses.entries.firstOrNull { cmd.startsWith(it.key) }?.let { return it.value }
        return defaultResponse
    }

    override fun close() {
        closeCallCount++
        opened = false
    }
}
