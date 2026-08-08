package com.caseforge.scanner.planb.gateway

/**
 * Subset of ISO 14229-1 negative response codes (NRC) present in SID `0x7F` payloads
 * ({requestSid}, `{nrc}`). Useful for interpreting gateway/firewall rejects before full DTC decode.
 *
 * Codes not listed resolve via [fromNrc] as `null` — extend the enum as golden logs expose more.
 */
@Suppress("MagicNumber") // Explicit UDS-defined constants.
enum class UdsNegativeResponse(val nrc: Int) {
    GENERAL_REJECT(0x10),
    SERVICE_NOT_SUPPORTED(0x11),
    SUB_FUNCTION_NOT_SUPPORTED(0x12),
    INCORRECT_MESSAGE_LENGTH_OR_INVALID_FORMAT(0x13),
    RESPONSE_TOO_LONG(0x14),
    BUSY_REPEAT_REQUEST(0x21),
    CONDITIONS_NOT_CORRECT(0x22),
    REQUEST_SEQUENCE_ERROR(0x24),
    REQUEST_OUT_OF_RANGE(0x31),
    SECURITY_ACCESS_DENIED(0x33),
    INVALID_KEY(0x35),
    EXCEED_NUMBER_OF_ATTEMPTS(0x36),
    REQUIRED_TIME_DELAY_NOT_EXPIRED(0x37),
    UPLOAD_DOWNLOAD_NOT_ACCEPTED(0x70),
    TRANSFER_DATA_SUSPENDED(0x71),
    GENERAL_PROGRAMMING_FAILURE(0x72),
    WRONG_BLOCK_SEQUENCE_COUNTER(0x73),
    RESPONSE_PENDING(0x78),
    SUB_FUNCTION_NOT_SUPPORTED_IN_ACTIVE_SESSION(0x7E),
    SERVICE_NOT_SUPPORTED_IN_ACTIVE_SESSION(0x7F),
    RPM_TOO_HIGH(0x81),
    RPM_TOO_LOW(0x82),
    ENGINE_IS_RUNNING(0x83),
    ENGINE_IS_NOT_RUNNING(0x84),
    ENGINE_RUN_TIME_TOO_LOW(0x85),
    TEMPERATURE_TOO_HIGH(0x86),
    TEMPERATURE_TOO_LOW(0x87),
    VEHICLE_SPEED_TOO_HIGH(0x88),
    VEHICLE_SPEED_TOO_LOW(0x89),
    ;

    companion object {
        fun fromNrc(byte: Byte): UdsNegativeResponse? {
            val v = byte.toInt() and 0xFF
            return entries.find { it.nrc == v }
        }
    }
}
