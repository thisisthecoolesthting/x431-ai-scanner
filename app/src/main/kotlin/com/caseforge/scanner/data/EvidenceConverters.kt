package com.caseforge.scanner.data

import androidx.room.TypeConverter
import com.caseforge.scanner.evidence.EvidenceType

class EvidenceConverters {
    @TypeConverter
    fun fromType(value: EvidenceType): String = value.name

    @TypeConverter
    fun toType(value: String): EvidenceType = EvidenceType.valueOf(value)
}
