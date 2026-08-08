package com.caseforge.scanner.ai

import kotlinx.serialization.Serializable

/**
 * A single NHTSA recall match for a given VIN.
 *
 * @param campaignId The NHTSA recall campaign ID (e.g., "07E055000")
 * @param summary Brief description of the recall defect
 * @param consequence Description of potential consequences if the defect is not addressed
 * @param relatedDtcs List of DTC codes found in the recall defect/consequence text
 * @param isOpenForVin Whether the recall is still open (not yet remedied) for this VIN
 * @param manufacturer Vehicle manufacturer name
 * @param modelYear Model year of the vehicle
 * @param make Vehicle make (brand)
 * @param model Vehicle model
 */
@Serializable
data class RecallMatch(
    val campaignId: String,
    val summary: String,
    val consequence: String = "",
    val relatedDtcs: List<String> = emptyList(),
    val isOpenForVin: Boolean = false,
    val manufacturer: String = "",
    val modelYear: Int = 0,
    val make: String = "",
    val model: String = "",
)
