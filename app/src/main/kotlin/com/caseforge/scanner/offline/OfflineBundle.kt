package com.caseforge.scanner.offline

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Bundled offline DTC dictionary and generic guided-test snippets.
 * Loaded from [ASSET_DTC] and [ASSET_GUIDED_TESTS] — no network.
 */
@Serializable
data class OfflineDtc(
    val code: String,
    val title: String,
    val summary: String,
    @SerialName("likelyCauses") val likelyCauses: List<String> = emptyList(),
    @SerialName("firstChecks") val firstChecks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class OfflineGuidedTest(
    val id: String,
    val title: String,
    val summary: String,
    @SerialName("relatedCodes") val relatedCodes: List<String> = emptyList(),
    val steps: List<String>,
    val tools: List<String> = emptyList(),
    val caution: String? = null,
)

@Serializable
private data class OfflineDtcFile(
    val version: Int = 1,
    val entries: List<OfflineDtc> = emptyList(),
)

@Serializable
private data class OfflineGuidedTestsFile(
    val version: Int = 1,
    val tests: List<OfflineGuidedTest> = emptyList(),
)

class OfflineBundle private constructor(
    val dtcs: List<OfflineDtc>,
    val guidedTests: List<OfflineGuidedTest>,
) {
    private val dtcByCode: Map<String, OfflineDtc> by lazy {
        dtcs.associateBy { OfflineDtcLookup.normalizeCode(it.code) }
    }

    fun dtc(code: String): OfflineDtc? = dtcByCode[OfflineDtcLookup.normalizeCode(code)]

    fun guidedTestsForCode(code: String): List<OfflineGuidedTest> {
        val normalized = OfflineDtcLookup.normalizeCode(code)
        if (normalized.isEmpty()) return emptyList()
        return guidedTests.filter { test ->
            test.relatedCodes.any { OfflineDtcLookup.normalizeCode(it) == normalized }
        }
    }

    companion object {
        private const val TAG = "OfflineBundle"
        const val ASSET_DTC = "offline/dtc_generic.json"
        const val ASSET_GUIDED_TESTS = "offline/guided_tests.json"

        val EMPTY = OfflineBundle(emptyList(), emptyList())

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun load(context: Context): OfflineBundle {
            return fromJson(
                readAsset(context, ASSET_DTC),
                readAsset(context, ASSET_GUIDED_TESTS),
            )
        }

        /** Parse bundled JSON — used by [load] and JVM unit tests. */
        fun fromJson(dtcJson: String, guidedTestsJson: String): OfflineBundle {
            val dtcs = parseDtcFile(dtcJson)
            val tests = parseGuidedTestsFile(guidedTestsJson)
            return OfflineBundle(dtcs, tests)
        }

        private fun readAsset(context: Context, path: String): String {
            return try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                Log.w(TAG, "readAsset failed: $path", e)
                ""
            }
        }

        private fun parseDtcFile(text: String): List<OfflineDtc> {
            if (text.isBlank()) return emptyList()
            return try {
                json.decodeFromString(OfflineDtcFile.serializer(), text).entries
            } catch (e: SerializationException) {
                Log.w(TAG, "parseDtcFile failed", e)
                emptyList()
            }
        }

        private fun parseGuidedTestsFile(text: String): List<OfflineGuidedTest> {
            if (text.isBlank()) return emptyList()
            return try {
                json.decodeFromString(OfflineGuidedTestsFile.serializer(), text).tests
            } catch (e: SerializationException) {
                Log.w(TAG, "parseGuidedTestsFile failed", e)
                emptyList()
            }
        }
    }
}
