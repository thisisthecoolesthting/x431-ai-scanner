package com.caseforge.scanner.obd

/**
 * Single CAN frame for OBD/ISO-TP over CAN (11-bit or 29-bit [id] with [isExtended]).
 */
data class ObdCanFrame(
    val id: Int,
    val data: ByteArray,
    val isExtended: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObdCanFrame) return false
        return id == other.id &&
            isExtended == other.isExtended &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + isExtended.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
