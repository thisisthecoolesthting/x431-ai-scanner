package com.caseforge.scanner.agent

import com.caseforge.scanner.ai.NhtsaLookup
import com.caseforge.scanner.ai.RecallMatch
import com.caseforge.scanner.ai.RecallMatcher
import com.caseforge.scanner.engine.ScrapedDtc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class RecallFlagger(
    private val nhtsa: NhtsaLookup = NhtsaLookup(),
    private val matcher: RecallMatcher = RecallMatcher(OkHttpClient()),
) {
    suspend fun flagRecalls(vin: String?, dtcs: List<ScrapedDtc>): List<RecallMatch> {
        if (vin.isNullOrBlank() || dtcs.isEmpty()) return emptyList()
        val vehicle = withContext(Dispatchers.IO) { nhtsa.decodeVehicle(vin) } ?: return emptyList()
        val year = vehicle.year.toIntOrNull() ?: return emptyList()
        if (vehicle.make.isBlank() || vehicle.model.isBlank()) return emptyList()
        return matcher.match(vin, vehicle.make, vehicle.model, year, dtcs)
    }
}
