package com.caseforge.scanner.obd.j1850

/**
 * Phase 1 (READ ONLY) orchestrator for a Chrysler SKIM/SKREEM immobilizer
 * status read over the PCI-bus (SAE J1850 VPW, ELM327 ATSP2).
 *
 * This class only ever brings the adapter up into VPW and sends a single
 * status-style request. It never sends a key-programming, PIN-capture, or
 * any other write/security command - there is no code path in this module
 * that can write to the vehicle.
 */
class SkimVpwReader(private val transport: ElmSerialTransport) {

    /**
     * Runs the full read: open the adapter, bring it up in J1850 VPW,
     * send the SKIM status request, and hand the raw response to
     * [SkimResponseParser]. Always closes the transport before returning,
     * even on failure.
     */
    fun readSkimStatus(): SkreemReadResult {
        transport.open()
        try {
            val init = transport.elmInitVpw()
            if (!init.success || !init.protocolConfirmed) {
                return SkreemReadResult(
                    modulePresent = false,
                    immobilizerStatus = "NO_RESPONSE",
                    keyCount = null,
                    vinEcho = null,
                    rawHex = "",
                    outcome = SkimReadOutcome.NO_RESPONSE,
                    detail = "ELM327 J1850 VPW init failed: ${init.failureReason ?: "unknown reason"}"
                )
            }

            val rawResponse = transport.sendCommand(PROVISIONAL_SKIM_STATUS_REQUEST_HEX)
            return SkimResponseParser.parse(rawResponse)
        } finally {
            transport.close()
        }
    }

    companion object {
        /**
         * PROVISIONAL -- verify against real vehicle capture.
         *
         * Best-known request for a Chrysler SKIM ("Sentry Key Immobilizer
         * Module") status read over the PCI-bus, built from:
         *
         *  - A disassembly of a Chrysler PCM ROM's PCI-bus RX-ID lookup
         *    table, which lists PCI ID 0xB1 as "SKIM status", length 3
         *    (i.e. ID byte + ONE status data byte + CRC byte).
         *    Source: https://chryslerccdsci.wordpress.com/pci-bus/
         *    (Daniel Laszlo / "Chrysler Scanner" project; see also
         *    https://github.com/laszlodaniel/J1850VPWCore and
         *    https://github.com/laszlodaniel/ChryslerScanner for the wider
         *    open-source Chrysler CCD/PCI/SCI tooling this was drawn from).
         *  - That same table shows PCI ID 0xB1 in the PCM's RX table, i.e.
         *    something the PCM *listens for* - meaning SKIM is documented
         *    to transmit it unsolicited/periodically on its own. The
         *    safest read strategy is therefore to LISTEN for this frame;
         *    SkimResponseParser matches on PCI ID 0xB1 regardless of
         *    whether it arrived because we asked or because SKIM sent it
         *    on its own initiative.
         *  - The explicit "send a request and get a reply" byte sequence
         *    below is our best-effort construction, NOT a confirmed
         *    capture: it reuses the target PCI ID (0xB1) as the request's
         *    header byte with a single null data byte, following the
         *    generic "ID + data + CRC" shape documented for Chrysler's own
         *    PCI request messages (PCI ID 0x24 "PCI request" -> PCI ID
         *    0x26 "diagnostic response", same ROM disassembly). The CRC
         *    byte itself is deliberately NOT appended here: the ELM327
         *    datasheet documents that the adapter computes and appends
         *    the checksum automatically for outgoing legacy-protocol
         *    messages, so we only ever hand it header+data hex.
         *  - Explicitly NOT used: PCI ID 0x4F ("SKIM seed/key validation")
         *    and PCI ID 0x3F ("SKIM seed" TX) - those are the encrypted
         *    key-matching handshake between SKIM and PCM, i.e. security/
         *    write territory and out of scope for this read-only module.
         *
         * TODO(hardware-capture): confirm/replace with a byte-for-byte
         * capture from a real 2004-2006 Jeep PCI-bus (DRBIII, a J1850 VPW
         * logic analyzer capture, or a laszlodaniel ChryslerScanner-class
         * tool) before this is trusted for anything beyond "try it and
         * see". See README.md "Open items" for the full list.
         */
        const val PROVISIONAL_SKIM_STATUS_REQUEST_HEX: String = "B100"

        /** Chrysler PCI-bus message ID for "SKIM status" (see citation above). */
        const val SKIM_STATUS_PCI_ID: Int = 0xB1
    }
}
