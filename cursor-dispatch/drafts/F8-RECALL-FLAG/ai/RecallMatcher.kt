package com.caseforge.scanner.ai

import android.util.Log
import com.caseforge.scanner.engine.Dtc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// NHTSA API response models
// ---------------------------------------------------------------------------

@Serializable
data class NhtsaRecallsResponse(
    @SerialName("results")
    val results: List<NhtsaRecallRecord> = emptyList(),
)

@Serializable
data class NhtsaRecallRecord(
    @SerialName("Campaign Number")
    val campaignId: String = "",
    @SerialName("Defect Summary")
    val defectSummary: String = "",
    @SerialName("Consequence Summary")
    val consequenceSummary: String = "",
    @SerialName("Manufacturer Name")
    val manufacturerName: String = "",
    @SerialName("Model Year")
    val modelYear: Int = 0,
)

// ---------------------------------------------------------------------------
// RecallMatcher — queries NHTSA and cross-references DTCs
// ---------------------------------------------------------------------------

/**
 * Queries NHTSA's public recalls endpoint to find open recalls for a given VIN,
 * then cross-references the recall defect/consequence text against a list of DTCs.
 *
 * No authentication required; NHTSA APIs are publicly accessible.
 * Uses OkHttpClient pattern consistent with CapabilityRegistry.
 *
 * ### Workflow
 * 1. Decode VIN to extract make, model, modelYear (assumed passed in by caller)
 * 2. Query NHTSA's recallsByVehicle endpoint: make, model, modelYear
 * 3. For each recall, scan defect & consequence text for DTC codes (P####, B####, C####, U####)
 * 4. Return ranked list of RecallMatch sorted by relevance (most DTC hits first)
 */
class RecallMatcher(
    private val http: OkHttpClient,
    private val timeoutMs: Long = 5_000L,
) {
    companion object {
        private const val TAG = "RecallMatcher"
        private const val NHTSA_BASE = "https://api.nhtsa.gov/recalls/recallsByVehicle"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Matches open NHTSA recalls against detected DTCs for a vehicle.
     *
     * @param vin The VIN string (used for logging; caller must decode to make/model/year)
     * @param make Vehicle make (e.g., "Honda")
     * @param model Vehicle model (e.g., "Accord")
     * @param modelYear Model year (e.g., 2020)
     * @param dtcs List of detected diagnostic trouble codes
     *
     * @return Sorted list of RecallMatch objects, ranked by DTC hit count (descending).
     *         Empty list if no matches found or API unavailable.
     */
    suspend fun match(
        vin: String,
        make: String,
        model: String,
        modelYear: Int,
        dtcs: List<Dtc>,
    ): List<RecallMatch> = withContext(Dispatchers.IO) {
        if (dtcs.isEmpty()) {
            Log.d(TAG, "match: no DTCs to cross-reference, returning empty matches")
            return@withContext emptyList()
        }

        val recalls = fetchRecalls(make, model, modelYear)
        if (recalls.isEmpty()) {
            Log.d(TAG, "match: no recalls found for $make $model $modelYear")
            return@withContext emptyList()
        }

        val dtcCodes = dtcs.map { it.code.uppercase() }.toSet()
        val matches = mutableListOf<RecallMatch>()

        for (recall in recalls) {
            val relatedCodes = findDtcsInText(
                recall.defectSummary + " " + recall.consequenceSummary,
                dtcCodes
            )
            if (relatedCodes.isNotEmpty()) {
                matches.add(
                    RecallMatch(
                        campaignId = recall.campaignId,
                        summary = recall.defectSummary,
                        consequence = recall.consequenceSummary,
                        relatedDtcs = relatedCodes,
                        isOpenForVin = true,
                        manufacturer = recall.manufacturerName,
                        modelYear = recall.modelYear,
                        make = make,
                        model = model,
                    )
                )
            }
        }

        // Sort by DTC hit count descending
        matches.sortByDescending { it.relatedDtcs.size }
        Log.d(TAG, "match: found ${matches.size} recalls for $vin (from ${recalls.size} total)")
        matches
    }

    /**
     * Performs HTTP GET against NHTSA's recallsByVehicle endpoint.
     *
     * @return List of NhtsaRecallRecord; empty on any error (logged, not thrown).
     */
    private fun fetchRecalls(
        make: String,
        model: String,
        modelYear: Int,
    ): List<NhtsaRecallRecord> {
        val url = "$NHTSA_BASE?make=${urlEncode(make)}&model=${urlEncode(model)}&modelYear=$modelYear"

        val client = http.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "fetchRecalls: HTTP ${response.code} from NHTSA")
                    return@use emptyList()
                }
                val body = response.body?.string()
                    ?: run {
                        Log.w(TAG, "fetchRecalls: empty response body from NHTSA")
                        return@use emptyList()
                    }
                try {
                    val parsed = json.decodeFromString(NhtsaRecallsResponse.serializer(), body)
                    parsed.results
                } catch (e: SerializationException) {
                    Log.w(TAG, "fetchRecalls: malformed JSON from NHTSA", e)
                    emptyList()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "fetchRecalls: network error from NHTSA", e)
            emptyList()
        }
    }

    /**
     * Scans text for DTC codes matching patterns P/B/C/U followed by 4 digits.
     *
     * @param text The text to scan (defect + consequence summary)
     * @param dtcCodes Set of DTC codes to match against
     *
     * @return List of matching DTC codes found in the text
     */
    private fun findDtcsInText(text: String, dtcCodes: Set<String>): List<String> {
        // Pattern: P/B/C/U followed by exactly 4 digits
        val pattern = Regex("""[PBCU]\d{4}""")
        val found = mutableListOf<String>()

        pattern.findAll(text.uppercase()).forEach { match ->
            val code = match.value
            if (code in dtcCodes) {
                found.add(code)
            }
        }

        return found.distinct()
    }

    /**
     * Simple URL encoding for query parameters (handles spaces and special chars).
     */
    private fun urlEncode(value: String): String {
        return value.replace(" ", "%20")
            .replace(",", "%2C")
            .replace("-", "%2D")
    }
}
