package com.caseforge.scanner.ai

import android.util.Log
import com.caseforge.scanner.engine.ScrapedDtc
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

@Serializable
data class NhtsaRecallsResponse(
    @SerialName("results") val results: List<NhtsaRecallRecord> = emptyList(),
)

@Serializable
data class NhtsaRecallRecord(
    @SerialName("Campaign Number") val campaignId: String = "",
    @SerialName("Defect Summary") val defectSummary: String = "",
    @SerialName("Consequence Summary") val consequenceSummary: String = "",
    @SerialName("Manufacturer Name") val manufacturerName: String = "",
    @SerialName("Model Year") val modelYear: Int = 0,
)

class RecallMatcher(private val http: OkHttpClient, private val timeoutMs: Long = 5_000L) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun match(vin: String, make: String, model: String, modelYear: Int, dtcs: List<ScrapedDtc>): List<RecallMatch> =
        withContext(Dispatchers.IO) {
            if (dtcs.isEmpty()) return@withContext emptyList()
            val recalls = fetchRecalls(make, model, modelYear)
            val codes = dtcs.map { it.code.uppercase() }.toSet()
            recalls.mapNotNull { r ->
                val related = findDtcsInText(r.defectSummary + " " + r.consequenceSummary, codes)
                if (related.isEmpty()) null else RecallMatch(
                    campaignId = r.campaignId, summary = r.defectSummary,
                    consequence = r.consequenceSummary, relatedDtcs = related,
                    isOpenForVin = true, manufacturer = r.manufacturerName,
                    modelYear = r.modelYear, make = make, model = model,
                )
            }.sortedByDescending { it.relatedDtcs.size }
        }

    private fun fetchRecalls(make: String, model: String, modelYear: Int): List<NhtsaRecallRecord> {
        val url = "https://api.nhtsa.gov/recalls/recallsByVehicle?make=${enc(make)}&model=${enc(model)}&modelYear=$modelYear"
        return try {
            http.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
                .newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    json.decodeFromString(NhtsaRecallsResponse.serializer(), body).results
                }
        } catch (e: Exception) {
            Log.w("RecallMatcher", "fetch failed", e); emptyList()
        }
    }

    private fun findDtcsInText(text: String, codes: Set<String>): List<String> =
        Regex("""[PBCU]\d{4}""").findAll(text.uppercase()).map { it.value }.filter { it in codes }.distinct()

    private fun enc(s: String) = s.replace(" ", "%20")
}
