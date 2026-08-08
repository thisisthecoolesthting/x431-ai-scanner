package com.caseforge.scanner.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/**
 * A single diagnostic capability entry as stored in capabilities.json.
 *
 * All fields that appear in the OEM files are represented here. Unknown keys
 * in the JSON are silently ignored (coerceInputValues + ignoreUnknownKeys).
 */
@Serializable
data class CapabilityEntry(
    val id: String,
    val label: String,
    val category: String,
    val path: List<String> = emptyList(),
    @SerialName("done_when") val doneWhen: String = "",
    @SerialName("timeout_sec") val timeoutSec: Int = 60,
    @SerialName("oem_scope") val oemScope: List<String> = emptyList(),
    val note: String = "",
)

/**
 * Root wrapper that matches the `{ "capabilities": [...] }` structure used
 * by both the bundled baseline asset and the remote hot-patch.
 */
@Serializable
data class CapabilityMap(
    val capabilities: List<CapabilityEntry> = emptyList(),
) {
    /** Convenience: look up a capability by its [id]. O(1) after first call. */
    private val index: Map<String, CapabilityEntry> by lazy {
        capabilities.associateBy { it.id }
    }

    fun find(id: String): CapabilityEntry? = index[id]

    companion object {
        val EMPTY = CapabilityMap(emptyList())
    }
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * Typed registry that merges a bundled baseline
 * (`assets/capabilities/capabilities.json`) with a hot-patch fetched from
 * [remoteUrl].
 *
 * ### Load strategy (called by [load])
 * 1. Cache file exists **and** is younger than [cacheTtlMs] → return cache.
 * 2. Otherwise attempt remote fetch (timeout = [timeoutMs] ms).
 * 3. On remote success: merge remote + baseline (remote wins on id collision),
 *    persist cache, return merged map.
 * 4. On remote failure: return stale cache if one exists; else baseline only.
 *
 * ### Refresh ([refresh])
 * Forces a remote fetch regardless of cache age. On failure the existing
 * cache is left untouched and the error is returned as [Result.failure].
 *
 * All errors are logged via [Log.w]; this class never throws.
 */
class CapabilityRegistry(
    private val context: Context,
    private val cacheDir: File,
    private val http: OkHttpClient,
    private val remoteUrl: String = REMOTE_URL,
    private val timeoutMs: Long = 5_000L,
    private val cacheTtlMs: Long = 24L * 60L * 60L * 1_000L,
) {
    companion object {
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/thisisthecoolesthting/x431-ai-scanner/main/capabilities/capabilities.json"

        private const val TAG = "CapabilityRegistry"
        private const val CACHE_FILENAME = "capabilities_cache.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val cacheFile: File get() = File(cacheDir, CACHE_FILENAME)

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns the best available [CapabilityMap] following the four-step
     * strategy described in the class KDoc.
     *
     * Always returns a non-null map; worst case it is the bundled baseline.
     */
    suspend fun load(): CapabilityMap = withContext(Dispatchers.IO) {
        // 1. Cache hit
        val cached = readCache()
        if (cached != null && isCacheFresh()) {
            Log.d(TAG, "load: cache hit (${cached.capabilities.size} entries)")
            return@withContext cached
        }

        // 2-3. Attempt remote fetch + merge
        val remoteResult = fetchRemote()
        if (remoteResult.isSuccess) {
            val remote = remoteResult.getOrThrow()
            val baseline = loadBaseline()
            val merged = merge(remote, baseline)
            writeCache(merged)
            Log.d(TAG, "load: remote OK – merged ${merged.capabilities.size} entries")
            return@withContext merged
        }

        // 4. Remote failed → stale cache or baseline
        if (cached != null) {
            Log.w(TAG, "load: remote failed, using stale cache (${cached.capabilities.size} entries)")
            return@withContext cached
        }

        val baseline = loadBaseline()
        Log.w(TAG, "load: remote failed and no cache – baseline only (${baseline.capabilities.size} entries)")
        baseline
    }

    /**
     * Forces a remote fetch regardless of cache age.
     *
     * On success the cache is updated and the merged map is returned as
     * [Result.success]. On failure the cache is left untouched and a
     * [Result.failure] is returned with the underlying exception.
     */
    suspend fun refresh(): Result<CapabilityMap> = withContext(Dispatchers.IO) {
        val remoteResult = fetchRemote()
        if (remoteResult.isFailure) {
            val ex = remoteResult.exceptionOrNull()
            Log.w(TAG, "refresh: remote fetch failed – cache preserved", ex)
            return@withContext Result.failure(ex ?: IOException("Unknown fetch error"))
        }

        val remote = remoteResult.getOrThrow()
        val baseline = loadBaseline()
        val merged = merge(remote, baseline)
        writeCache(merged)
        Log.d(TAG, "refresh: success – ${merged.capabilities.size} entries")
        Result.success(merged)
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Merges [remote] and [baseline] so that remote entries win on id
     * collision and baseline fills any id absent from remote.
     */
    private fun merge(remote: CapabilityMap, baseline: CapabilityMap): CapabilityMap {
        // Start with a mutable map keyed by id; remote entries are inserted first
        // so they win on collision when baseline entries are added next.
        val merged: MutableMap<String, CapabilityEntry> = LinkedHashMap()
        for (entry in remote.capabilities) {
            merged[entry.id] = entry
        }
        for (entry in baseline.capabilities) {
            merged.putIfAbsent(entry.id, entry)
        }
        return CapabilityMap(merged.values.toList())
    }

    /**
     * Loads and parses the bundled asset at
     * `assets/capabilities/capabilities.json`.
     *
     * Returns [CapabilityMap.EMPTY] on any error (asset missing, malformed JSON).
     */
    private fun loadBaseline(): CapabilityMap {
        return try {
            val text = context.assets.open("capabilities/capabilities.json")
                .bufferedReader()
                .use { it.readText() }
            json.decodeFromString(CapabilityMap.serializer(), text)
        } catch (e: IOException) {
            Log.w(TAG, "loadBaseline: asset open failed", e)
            CapabilityMap.EMPTY
        } catch (e: SerializationException) {
            Log.w(TAG, "loadBaseline: JSON parse failed", e)
            CapabilityMap.EMPTY
        }
    }

    /**
     * Performs the HTTP GET against [remoteUrl] with a [timeoutMs] timeout.
     *
     * Returns [Result.success] with the parsed [CapabilityMap] or
     * [Result.failure] on network error, non-200 status, or malformed JSON.
     */
    private fun fetchRemote(): Result<CapabilityMap> {
        val client = http.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(remoteUrl)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code} from $remoteUrl"
                    Log.w(TAG, "fetchRemote: $msg")
                    return Result.failure(IOException(msg))
                }
                val body = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body"))
                try {
                    Result.success(json.decodeFromString(CapabilityMap.serializer(), body))
                } catch (e: SerializationException) {
                    Log.w(TAG, "fetchRemote: malformed JSON from remote", e)
                    Result.failure(e)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "fetchRemote: network error", e)
            Result.failure(e)
        }
    }

    /**
     * Reads and parses the on-disk cache file.
     *
     * Returns `null` if the file does not exist or cannot be parsed.
     */
    private fun readCache(): CapabilityMap? {
        if (!cacheFile.exists()) return null
        return try {
            val text = cacheFile.readText()
            json.decodeFromString(CapabilityMap.serializer(), text)
        } catch (e: IOException) {
            Log.w(TAG, "readCache: read failed", e)
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "readCache: parse failed – cache will be discarded", e)
            null
        }
    }

    /**
     * Writes [map] to the cache file, creating parent directories as needed.
     *
     * Silently logs and ignores any [IOException].
     */
    private fun writeCache(map: CapabilityMap) {
        try {
            cacheDir.mkdirs()
            val text = json.encodeToString(CapabilityMap.serializer(), map)
            cacheFile.writeText(text)
        } catch (e: IOException) {
            Log.w(TAG, "writeCache: failed to write cache", e)
        }
    }

    /**
     * Returns `true` if the cache file exists and its last-modified timestamp
     * is within the [cacheTtlMs] window.
     */
    private fun isCacheFresh(): Boolean {
        if (!cacheFile.exists()) return false
        val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
        return ageMs < cacheTtlMs
    }
}
