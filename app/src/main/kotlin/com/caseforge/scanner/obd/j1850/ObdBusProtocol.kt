package com.caseforge.scanner.obd.j1850

/**
 * Supported OBD-II / legacy diagnostic bus protocols, keyed to the ELM327
 * "ATSP<n>" protocol-select command that puts an ELM327-compatible adapter
 * into that mode.
 *
 * Reference: ELM327 AT command set (ATSP0-ATSPC) - Elm Electronics ELM327
 * datasheet, https://www.elmelectronics.com/wp-content/uploads/2016/07//ELM327DS.pdf
 *
 * This module only ever *sets* a protocol explicitly (never ATSP0/"auto"
 * detect) so the app can be certain which bus it is talking on. ATSP0 is
 * documented here only as [AUTO_DETECT_ELM_CODE] for reference/telemetry -
 * nothing in this module sends it.
 */
enum class ObdBusProtocol(
    /** Single hex-digit/letter ELM327 accepts after "ATSP". */
    val elmProtocolChar: Char,
    val description: String
) {
    /**
     * ISO 15765-4 CAN. This is the transport the existing app read-path
     * (UDS 0x22 on 0x7E0/0x7E8) already assumes, and the reason it never
     * gets an answer from a pre-CAN Chrysler PCI-bus vehicle. ELM327
     * exposes four CAN sub-variants (ATSP6/7/8/9 = 11/29-bit ID x
     * 500/250 kbps); '6' (11-bit @ 500 kbps) is the common default and the
     * one matching the existing 0x7E0/0x7E8 IDs.
     */
    CAN_ISOTP('6', "ISO 15765-4 CAN (11 bit ID, 500 kbps)"),

    /**
     * SAE J1850 VPW, 10.4 kbps, single wire on OBD-II pin 2. This is the
     * bus the Chrysler PCI-bus / SKIM immobilizer module rides on for
     * 2004-2006 Jeep-era vehicles. THIS is the protocol this module exists
     * to speak - see ElmSerialTransport.elmInitVpw().
     */
    J1850_VPW('2', "SAE J1850 VPW (10.4 kbps)"),

    /** SAE J1850 PWM, 41.6 kbps, two wire - legacy Ford. Not used by SKIM. */
    J1850_PWM('1', "SAE J1850 PWM (41.6 kbps)"),

    /** ISO 9141-2, 5-baud init - legacy Chrysler/European/Asian pre-CAN. */
    ISO9141('3', "ISO 9141-2 (5 baud init)"),

    /**
     * ISO 14230-4 KWP2000. '5' = fast init (the common case); ELM327 also
     * exposes ATSP4 for 5-baud slow-init KWP, not modelled as a separate
     * enum value here.
     */
    KWP2000('5', "ISO 14230-4 KWP2000 (fast init)");

    /** The full "ATSPn" command string that selects this protocol. */
    val atspCommand: String get() = "ATSP$elmProtocolChar"

    companion object {
        /**
         * ELM327 code for "auto-detect protocol" (ATSP0). Never sent by
         * this module - elmInitVpw() always pins the protocol explicitly
         * with ATSP2 so the caller knows for certain which bus it got.
         * Kept here for reference/telemetry only.
         */
        const val AUTO_DETECT_ELM_CODE: Char = '0'
    }
}
