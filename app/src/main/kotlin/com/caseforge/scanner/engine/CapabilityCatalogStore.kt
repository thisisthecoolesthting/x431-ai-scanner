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

/**
 * A single diagnostic capability entry as stored in capabilities.json.
 * Renamed from earlier B1 "CapabilityEntry" — same structure, namespace-safe.
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
 * Root wrapper that matches the `{ "capabilities": [...] }` structure used by
 * both the bundled baseline asset and the remote hot-patch.
 *
 * Named CapabilityCatalog (not CapabilityMap) to avoid collision with the
 * foundation singleton `object CapabilityMap` in CapabilityMap.kt.
 */
@Serializable
data class CapabilityCatalog(
    val capabilities: List<CapabilityEntry> = emptyList(),
) {
    private val index: Map<String, CapabilityEntry> by lazy {
        capabilities.associateBy { it.id }
    }

    fun find(id: String): CapabilityEntry? = index[id]

    companion object {
        val EMPTY = CapabilityCatalog(emptyList())
    }
}

/**
 * Typed registry that merges a bundled baseline (assets/capabilities/capabilities.json)
 * with a hot-patch fetched from [remoteUrl].
 *
 * NOTE: this is the IMPLEMENTATION class for the JSON catalog. The
 * `CapabilityRegistry` interface in Types.kt (used by EngineDriver) is a
 * separate, narrower abstraction that returns `CapabilityMap.Capability` items.
 * If you need the interface-shaped accessor on top of this store, call
 * [asRegistry] to get an adapter that maps catalog entries to the foundation
 * capability type by id.
 */
class CapabilityCatalogStore(
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

        private const val TAG = "CapabilityCatalogStore"
        private const val CACHE_FILENAME = "capabilities_cache.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val cacheFile: File get() = File(cacheDir, CACHE_FILENAME)

    suspend fun load(): CapabilityCatalog = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null && isCacheFresh()) {
            Log.d(TAG, "load: cache hit (" + cached.capabilities.size + " entries)")
            return@withContext cached
        }
        val remoteResult = fetchRemote()
        if (remoteResult.isSuccess) {
            val remote = remoteResult.getOrThrow()
            val baseline = loadBaseline()
            val merged = merge(remote, baseline)
            writeCache(merged)
            Log.d(TAG, "load: remote OK – merged " + merged.capabilities.size + " entries")
            return@withContext merged
        }
        if (cached != null) {
            Log.w(TAG, "load: remote failed, using stale cache (" + cached.capabilities.size + " entries)")
            return@withContext cached
        }
        val baseline = loadBaseline()
        Log.w(TAG, "load: remote failed and no cache – baseline only (" + baseline.capabilities.size + " entries)")
        baseline
    }

    suspend fun refresh(): Result<CapabilityCatalog> = withContext(Dispatchers.IO) {
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
        Log.d(TAG, "refresh: success – " + merged.capabilities.size + " entries")
        Result.success(merged)
    }

    private fun merge(remote: CapabilityCatalog, baseline: CapabilityCatalog): CapabilityCatalog {
        val merged: MutableMap<String, CapabilityEntry> = LinkedHashMap()
        for (entry in remote.capabilities) {
            merged[entry.id] = entry
        }
        for (entry in baseline.capabilities) {
            merged.putIfAbsent(entry.id, entry)
        }
        return CapabilityCatalog(merged.values.toList())
    }

    private fun loadBaseline(): CapabilityCatalog {
        return try {
            val text = context.assets.open("capabilities/capabilities.json")
                .bufferedReader()
                .use { it.readText() }
            json.decodeFromString(CapabilityCatalog.serializer(), text)
        } catch (e: IOException) {
            Log.w(TAG, "loadBaseline: asset open failed", e)
            CapabilityCatalog.EMPTY
        } catch (e: SerializationException) {
            Log.w(TAG, "loadBaseline: JSON parse failed", e)
            CapabilityCatalog.EMPTY
        }
    }

    private fun fetchRemote(): Result<CapabilityCatalog> {
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
                    val msg = "HTTP " + response.code + " from " + remoteUrl
                    Log.w(TAG, "fetchRemote: " + msg)
                    return Result.failure(IOException(msg))
                }
                val body = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body"))
                try {
                    Result.success(json.decodeFromString(CapabilityCatalog.serializer(), body))
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

    private fun readCache(): CapabilityCatalog? {
        if (!cacheFile.exists()) return null
        return try {
            val text = cacheFile.readText()
            json.decodeFromString(CapabilityCatalog.serializer(), text)
        } catch (e: IOException) {
            Log.w(TAG, "readCache: read failed", e)
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "readCache: parse failed – cache will be discarded", e)
            null
        }
    }

    private fun writeCache(map: CapabilityCatalog) {
        try {
            cacheDir.mkdirs()
            val text = json.encodeToString(CapabilityCatalog.serializer(), map)
            cacheFile.writeText(text)
        } catch (e: IOException) {
            Log.w(TAG, "writeCache: failed to write cache", e)
        }
    }

    private fun isCacheFresh(): Boolean {
        if (!cacheFile.exists()) return false
        val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
        return ageMs < cacheTtlMs
    }
}