package com.caseforge.scanner.vci

/**
 * VCI protocol opcode catalogue.
 *
 * SOURCE OF TRUTH:
 *   The opcode dispatch table lives in the native library (CommunicationCOM.receiveData JNI).
 *   All values in this file are INFERRED from DiagnoseConstants FEEDBACK_* constants and
 *   runtime behaviour.  They represent the "UI type" integers the SO uses to communicate
 *   back to the Java layer — NOT confirmed wire-level opcode bytes.
 *
 * WHAT WE KNOW FOR CERTAIN (decompile-verified):
 *   - Frame bytes[2..3] carry a 16-bit opcode word (big-endian).
 *   - CommunicationCOM.comReceiveData passes the decoded frame to receiveData(byte[], int)
 *     which dispatches on the opcode value.
 *   - DiagnoseConstants carries 100+ FEEDBACK_* integer constants (strings) that
 *     correspond to UI result types the native layer sends back to Java.
 *   - The mapping from DiagnoseConstants.UI_TYPE_* integers to wire opcodes is ONE-TO-ONE
 *     according to the protocol — but the exact numeric mapping requires packet capture.
 *
 * HOW TO FILL IN THE UNKNOWNS:
 *   1. Frida hook:  hook LocalSocketClient.send(byte[]) and recv() → log hex lines.
 *   2. Match the first 4 hex chars of each hex line (bytes[2..3]) to the DiagnoseConstants
 *      strings displayed on screen at that moment.
 *   3. Update KNOWN entries below and promote UNKNOWN ones to INFERRED.
 *
 * SPIKE STATUS:
 *   - HANDSHAKE_INIT: best guess from protocol framing (must verify).
 *   - OBD_MODE01_PID_REQ / OBD_MODE03_DTC_REQ: standard SAE J1979 mode bytes;
 *     the VCI likely uses these as the low byte of the opcode word (common in
 *     OEM VCI protocol dumps from open-source tools).
 *   - All UI_TYPE_* derived values: inferred from DiagnoseConstants string values
 *     cast to Int.  Plausible but unverified.
 */

// ------------------------------------------------------------------
// Known / strongly inferred opcodes
// ------------------------------------------------------------------

/**
 * Opcodes whose values have been confirmed or strongly inferred from the decompile.
 * Use these in production code paths once verified.
 */
enum class KnownOpcode(val value: Int, val direction: Direction, val notes: String) {

    // ---- Handshake / session management ----

    /**
     * Initial handshake sent by host → VCI after Bluetooth connects.
     * STATUS: INFERRED — common framing pattern in OEM VCI tools.
     * Payload: empty or device serial number bytes.
     * Expected response: HANDSHAKE_ACK (0x0001 assumed).
     */
    HANDSHAKE_INIT(
        value = 0x0000,
        direction = Direction.HOST_TO_VCI,
        notes = "INFERRED: session init handshake. Verify via packet capture."
    ),

    HANDSHAKE_ACK(
        value = 0x0001,
        direction = Direction.VCI_TO_HOST,
        notes = "INFERRED: ACK from VCI after handshake."
    ),

    // ---- OBD-II standard service requests (SAE J1979 mode bytes) ----
    // These are sent HOST → VCI and the VCI forwards them to the OBD-II bus.
    // The opcode high byte likely encodes the 'category' (OBD passthrough = 0x01?).

    /**
     * OBD Mode 01 — show current data / live PIDs.
     * STATUS: INFERRED from standard OBD-II and common OEM VCI protocol dumps.
     * Payload: [pid_byte] (1 byte, e.g. 0x0C for RPM, 0x0D for speed).
     */
    OBD_MODE01_PID_REQ(
        value = 0x0101,
        direction = Direction.HOST_TO_VCI,
        notes = "INFERRED: OBD-II mode 01 live PID request. High byte = category 0x01."
    ),

    OBD_MODE01_PID_RESP(
        value = 0x0181,
        direction = Direction.VCI_TO_HOST,
        notes = "INFERRED: OBD-II mode 01 response (0x40 + mode = 0x41 standard, but VCI may remap)."
    ),

    /**
     * OBD Mode 03 — request stored DTCs.
     * STATUS: INFERRED.
     * Payload: empty.
     * Response: DTC list encoded as pairs of bytes per code.
     */
    OBD_MODE03_DTC_REQ(
        value = 0x0103,
        direction = Direction.HOST_TO_VCI,
        notes = "INFERRED: OBD-II mode 03 read stored DTCs."
    ),

    OBD_MODE03_DTC_RESP(
        value = 0x0143,
        direction = Direction.VCI_TO_HOST,
        notes = "INFERRED: OBD-II mode 03 DTC response payload."
    ),

    /**
     * OBD Mode 04 — clear/reset DTCs and MIL.
     * STATUS: INFERRED.
     * Payload: empty.
     */
    OBD_MODE04_CLEAR_DTC(
        value = 0x0104,
        direction = Direction.HOST_TO_VCI,
        notes = "INFERRED: OBD-II mode 04 clear stored DTCs."
    ),

    OBD_MODE04_CLEAR_RESP(
        value = 0x0144,
        direction = Direction.VCI_TO_HOST,
        notes = "INFERRED: mode 04 clear confirmation."
    ),

    /**
     * OBD Mode 09 — request vehicle information (VIN etc.).
     * STATUS: INFERRED.
     * Payload: [infoType] e.g. 0x02 = VIN.
     */
    OBD_MODE09_VEH_INFO_REQ(
        value = 0x0109,
        direction = Direction.HOST_TO_VCI,
        notes = "INFERRED: OBD-II mode 09 vehicle info."
    ),

    OBD_MODE09_VEH_INFO_RESP(
        value = 0x0149,
        direction = Direction.VCI_TO_HOST,
        notes = "INFERRED: mode 09 response."
    ),

    // ---- Proprietary / OEM diagnostic commands ----

    /**
     * Full auto-scan trigger.
     * Derived from DiagnoseConstants.FEEDBACK_FAULTCODES = "27" and
     * UI_TYPE_FAULTCODE = "700".  The exact wire opcode for "start full scan" is unknown;
     * the Java layer navigates UI menus and the SO mediates.
     * STATUS: UNKNOWN — placeholder 0x1B = decimal 27 (FEEDBACK_FAULTCODES value).
     */
    PROPRIETARY_FAULT_CODE(
        value = 0x001B,          // 27 decimal = FEEDBACK_FAULTCODES
        direction = Direction.VCI_TO_HOST,
        notes = "UNKNOWN: Maps to DiagnoseConstants.FEEDBACK_FAULTCODES=27. Wire byte unconfirmed."
    ),

    PROPRIETARY_FAULT_CODE_ACTIVE(
        value = 0x003A,          // 58 decimal = FEEDBACK_FAULTCODES_ACTIVE
        direction = Direction.VCI_TO_HOST,
        notes = "UNKNOWN: Maps to FEEDBACK_FAULTCODES_ACTIVE=58."
    ),

    PROPRIETARY_DATASTREAM_SELECT(
        value = 0x0011,          // 17 decimal = FEEDBACK_DATASTREAM_SELECT_MENU
        direction = Direction.VCI_TO_HOST,
        notes = "UNKNOWN: Maps to FEEDBACK_DATASTREAM_SELECT_MENU=17."
    ),

    PROPRIETARY_DATASTREAM_DATA(
        value = 0x0012,          // 18 decimal = FEEDBACK_DATASTREAM
        direction = Direction.VCI_TO_HOST,
        notes = "UNKNOWN: Maps to FEEDBACK_DATASTREAM=18."
    ),

    PROPRIETARY_ACTIVE_TEST(
        value = 0x0009,          // 9 decimal = FEEDBACK_ACTIVITYTEST
        direction = Direction.HOST_TO_VCI,
        notes = "UNKNOWN: Maps to FEEDBACK_ACTIVITYTEST=9."
    ),

    PROPRIETARY_GET_VIN(
        value = 0x0030,          // 48 decimal = FEEDBACK_GET_VIN
        direction = Direction.VCI_TO_HOST,
        notes = "UNKNOWN: Maps to FEEDBACK_GET_VIN=48."
    ),

    PROPRIETARY_SET_VIN(
        value = 0x002F,          // 47 decimal = FEEDBACK_SET_VIN
        direction = Direction.HOST_TO_VCI,
        notes = "UNKNOWN: Maps to FEEDBACK_SET_VIN=47."
    ),

    ;

    enum class Direction { HOST_TO_VCI, VCI_TO_HOST, BIDIRECTIONAL }

    companion object {
        fun fromValue(value: Int): KnownOpcode? = entries.firstOrNull { it.value == value }

        /** True if this opcode's value is confirmed from decompile evidence. */
        fun isConfirmed(opcode: KnownOpcode): Boolean = opcode in emptySet<KnownOpcode>()
            // Add verified opcodes to the set as packet capture confirms them.
    }
}

// ------------------------------------------------------------------
// Unknown opcode wrapper
// ------------------------------------------------------------------

/**
 * Wraps a raw opcode integer that doesn't match any [KnownOpcode].
 * Used by VciCommunicator to surface unrecognised frames for logging.
 */
@JvmInline
value class UnknownOpcode(val rawValue: Int) {
    override fun toString(): String =
        "UnknownOpcode(0x${rawValue.toString(16).uppercase().padStart(4, '0')})"
}

/**
 * Discriminated union: either a known opcode or an unknown one.
 * VciFrame.resolveOpcode() returns this.
 */
sealed class ResolvedOpcode {
    data class Known(val opcode: KnownOpcode) : ResolvedOpcode()
    data class Unknown(val raw: UnknownOpcode) : ResolvedOpcode()
}

fun VciFrame.resolveOpcode(): ResolvedOpcode =
    KnownOpcode.fromValue(opcode)
        ?.let { ResolvedOpcode.Known(it) }
        ?: ResolvedOpcode.Unknown(UnknownOpcode(opcode))

// ------------------------------------------------------------------
// DiagnoseConstants feedback ID → opcode mapping table
// (for cross-reference during packet capture sessions)
// ------------------------------------------------------------------

/**
 * Human-readable cross-reference table derived directly from DiagnoseConstants.
 * Used during packet capture to label frames in logs.
 *
 * Key   = numeric value of the FEEDBACK_* constant (string cast to Int)
 * Value = description string
 */
val FEEDBACK_ID_LABELS: Map<Int, String> = mapOf(
    1    to "FEEDBACK_NORMAL_MENU",
    3    to "FEEDBACK_MASK",
    6    to "FEEDBACK_NORMAL_BUTTON",
    7    to "FEEDBACK_INPUTBOX_TEXT",
    8    to "FEEDBACK_INPUTSTRING",
    9    to "FEEDBACK_ACTIVITYTEST",
    14   to "FEEDBACK_FREEZEFRAME",
    15   to "FEEDBACK_INPUT_NUMBER",
    16   to "FEEDBACK_INPUTSTRING_EX",
    17   to "FEEDBACK_DATASTREAM_SELECT_MENU",
    18   to "FEEDBACK_DATASTREAM",
    19   to "FEEDBACK_DATASTREAM_VW",
    27   to "FEEDBACK_FAULTCODES",
    30   to "FEEDBACK_ARGING_WINDOW",
    34   to "FEEDBACK_DATASTREAM_PAGE",
    35   to "FEEDBACK_DATASTREAM_PAGE_MASK",
    36   to "FEEDBACK_SPECIA_FUNCTION",
    44   to "FEEDBACK_MESSAGEBOX",
    46   to "FEEDBACK_COMBINATION_MENU",
    47   to "FEEDBACK_SET_VIN",
    48   to "FEEDBACK_GET_VIN",
    54   to "FEEDBACK_DISPLAY_VERSION",
    56   to "FEEDBACK_RESET_CARICON_MENU",
    58   to "FEEDBACK_FAULTCODES_ACTIVE",
    59   to "FEEDBACK_PARALLEL_MENU",
    60   to "FEEDBACK_PARALLEL_TROUBLE_CODE",
    61   to "FEEDBACK_PARALLEL_DATASTREAM",
    63   to "FEEDBACK_DIAG_RECORD",
    64   to "FEEDBACK_PARALLEL_SUB_MENU",
    65   to "FEEDBACK_CURRENT_MENU_PATH",
    66   to "FEEDBACK_SPT_SET_DIAG_FUN_INFO",
    67   to "FEEDBACK_SPECIADATASTREAM",
    68   to "FEEDBACK_SELECT_FILEDIALOG",
    69   to "FEEDBACK_DATASTREAM_ID_EX_STANDARDVALUE",
    70   to "FEEDBACK_MESSAGEBOX_TEXT_CUSTOMBUTTON",
    71   to "FEEDBACK_TROUBLE_CODE_ID_EX_RETURN_VALUE",
    72   to "FEEDBACK_DIAG_CALL_SERVICE_ALGORITHM_BASE",
    73   to "FEEDBACK_SPT_DTC_HELP",
    74   to "FEEDBACK_SPT_FUNCTION_HELP",
    75   to "FEEDBACK_SPT_DOWNLOAD_FILE",
    104  to "FEEDBACK_SPT_TRANS_DIAG_INFO",
    105  to "FEEDBACK_HIS_RECORD",
    106  to "FEEDBACK_SPT_CUSTOM_USE_ID",
    107  to "FEEDBACK_SPT_GET_LASTEST_VER",
    200  to "FEEDBACK_NEW_DIAGLOG_BUTTON",
)
