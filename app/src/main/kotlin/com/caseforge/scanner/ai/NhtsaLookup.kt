package com.caseforge.scanner.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Free public NHTSA lookups: VIN decode + recalls for that vehicle. No API key needed.
 *
 * docs:
 *   https://vpic.nhtsa.dot.gov/api/  (DecodeVinValues)
 *   https://api.nhtsa.gov/recalls/recallsByVehicle
 */
class NhtsaLookup {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun decodeVehicle(vin: String): Vehicle? = runCatching { decode(vin) }.getOrNull()

    /** Returns a compact JSON-string summary the agent can read back. */
    fun decodeAndRecalls(vin: String): String {
        val vehicle = runCatching { decode(vin) }.getOrNull()
            ?: return "VIN decode failed for $vin."
        val recalls = runCatching { recalls(vehicle.year, vehicle.make, vehicle.model) }
            .getOrDefault(emptyList())
        return buildString {
            appendLine("VIN: $vin")
            appendLine("Year: ${vehicle.year}")
            appendLine("Make: ${vehicle.make}")
            appendLine("Model: ${vehicle.model}")
            if (vehicle.engine.isNotBlank()) appendLine("Engine: ${vehicle.engine}")
            if (vehicle.transmission.isNotBlank()) appendLine("Transmission: ${vehicle.transmission}")
            if (vehicle.trim.isNotBlank()) appendLine("Trim: ${vehicle.trim}")
            appendLine()
            if (recalls.isEmpty()) {
                appendLine("Recalls: none reported for this year/make/model.")
            } else {
                appendLine("Recalls (${recalls.size}):")
                recalls.take(10).forEach { r ->
                    appendLine("  • [${r.campaign}] ${r.component}: ${r.summary.take(180)}")
                }
                if (recalls.size > 10) appendLine("  ...and ${recalls.size - 10} more.")
            }
        }
    }

    data class Vehicle(
        val year: String,
        val make: String,
        val model: String,
        val engine: String,
        val transmission: String,
        val trim: String,
    )

    data class Recall(val campaign: String, val component: String, val summary: String)

    private fun decode(vin: String): Vehicle {
        val url = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/$vin?format=json"
        val body = get(url)
        val results = JSONObject(body).optJSONArray("Results") ?: error("no Results")
        val o = results.optJSONObject(0) ?: error("empty Results")
        return Vehicle(
            year = o.optString("ModelYear", ""),
            make = o.optString("Make", ""),
            model = o.optString("Model", ""),
            engine = listOf(
                o.optString("DisplacementL", ""),
                o.optString("FuelTypePrimary", ""),
                o.optString("EngineCylinders", ""),
            ).filter { it.isNotBlank() }.joinToString(" "),
            transmission = o.optString("TransmissionStyle", ""),
            trim = o.optString("Trim", ""),
        )
    }

    private fun recalls(year: String, make: String, model: String): List<Recall> {
        if (year.isBlank() || make.isBlank() || model.isBlank()) return emptyList()
        val url = "https://api.nhtsa.gov/recalls/recallsByVehicle" +
            "?make=${enc(make)}&model=${enc(model)}&modelYear=${enc(year)}"
        val body = get(url)
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            Recall(
                campaign = r.optString("NHTSACampaignNumber", "?"),
                component = r.optString("Component", "Unknown"),
                summary = r.optString("Summary", ""),
            )
        }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        http.newCall(req).execute().use { resp ->
            val t = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${t.take(120)}")
            return t
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
