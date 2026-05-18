package com.caseforge.scanner.engine

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [CapabilityCatalogStore].
 *
 * Test matrix:
 *  1. [cacheHit_returnsCacheWithoutNetwork]         – fresh cache → no HTTP call, returns cache
 *  2. [cacheMiss_remoteSuccess_returnsMergedMap]     – stale cache + 200 → merged result, cache written
 *  3. [cacheMiss_remoteTimeout_returnsStaleCacheFallback] – stale cache + timeout → stale cache returned
 *  4. [cacheMiss_remote404_returnsStaleCacheFallback]     – stale cache + 404 → stale cache returned
 *  5. [cacheMiss_malformedRemoteJson_returnsStaleCacheFallback] – bad JSON → stale cache returned
 *  6. [baselineOnlyFallback_noCacheNoNetwork]        – no cache, network down → baseline only
 *  7. [refresh_forcesRemoteFetch_updatesCache]       – refresh ignores cache TTL, rewrites cache
 *  8. [refresh_failure_doesNotClearCache]            – refresh failure leaves old cache intact
 *  9. [merge_remoteWinsOnCollision]                  – id present in both → remote entry kept
 * 10. [merge_baselineFillsMissingIds]                – id only in baseline → baseline entry present
 */
class CapabilityCatalogStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    // Minimal baseline with two entries
    private val baselineEntryA = CapabilityEntry(
        id = "baseline_only",
        label = "Baseline Only",
        category = "Scan",
        path = listOf("baseline"),
        doneWhen = "done",
        timeoutSec = 30,
        oemScope = listOf("ford"),
        note = "from baseline",
    )
    private val baselineEntryB = CapabilityEntry(
        id = "shared_id",
        label = "Baseline Shared",
        category = "Codes",
        path = listOf("baseline", "shared"),
        doneWhen = "done",
        timeoutSec = 30,
        oemScope = listOf("ford"),
        note = "baseline version",
    )
    private val baselineMap = CapabilityCatalog(listOf(baselineEntryA, baselineEntryB))

    // Remote has one new entry and an override for "shared_id"
    private val remoteEntryNew = CapabilityEntry(
        id = "remote_only",
        label = "Remote Only",
        category = "LiveData",
        path = listOf("remote"),
        doneWhen = "done",
        timeoutSec = 60,
        oemScope = listOf("gm"),
        note = "from remote",
    )
    private val remoteEntryShared = CapabilityEntry(
        id = "shared_id",
        label = "Remote Shared",  // should win over baseline version
        category = "Codes",
        path = listOf("remote", "shared"),
        doneWhen = "done",
        timeoutSec = 45,
        oemScope = listOf("gm"),
        note = "remote version",
    )
    private val remoteMap = CapabilityCatalog(listOf(remoteEntryNew, remoteEntryShared))

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        assetManager = mockk()
        every { assetManager.open("capabilities/capabilities.json") } answers {
            ByteArrayInputStream(
                json.encodeToString(CapabilityMap.serializer(), baselineMap).toByteArray()
            )
        }

        context = mockk()
        every { context.assets } returns assetManager
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ------------------------------------------------------------------
    // Helper: build a registry pointed at the mock server
    // ------------------------------------------------------------------

    private fun registry(
        cacheContents: CapabilityMap? = null,
        cacheAgeMs: Long = 0L,            // 0 = just written (fresh)
        ttlMs: Long = 24L * 60L * 60L * 1_000L,
        timeoutMs: Long = 5_000L,
        url: String = server.url("/capabilities.json").toString(),
    ): CapabilityCatalogStore {
        val cacheDir = tmpFolder.newFolder()

        if (cacheContents != null) {
            val cacheFile = java.io.File(cacheDir, "capabilities_cache.json")
            cacheFile.writeText(json.encodeToString(CapabilityMap.serializer(), cacheContents))
            // Backdate the file to simulate age
            if (cacheAgeMs > 0) {
                cacheFile.setLastModified(System.currentTimeMillis() - cacheAgeMs)
            }
        }

        val http = OkHttpClient.Builder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        return CapabilityCatalogStore(
            context = context,
            cacheDir = cacheDir,
            http = http,
            remoteUrl = url,
            timeoutMs = timeoutMs,
            cacheTtlMs = ttlMs,
        )
    }

    // ------------------------------------------------------------------
    // 1. Cache hit
    // ------------------------------------------------------------------

    @Test
    fun cacheHit_returnsCacheWithoutNetwork() = runTest {
        // Fresh cache (age 0 < TTL); server enqueues nothing → any HTTP call would fail
        val cachedMap = CapabilityCatalog(listOf(baselineEntryA))
        val reg = registry(cacheContents = cachedMap, cacheAgeMs = 0L)

        val result = reg.load()

        assertEquals(1, result.capabilities.size)
        assertEquals("baseline_only", result.capabilities.first().id)
        // MockWebServer received no requests
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------------
    // 2. Cache miss + remote success
    // ------------------------------------------------------------------

    @Test
    fun cacheMiss_remoteSuccess_returnsMergedMap() = runTest {
        val staleAge = 25L * 60L * 60L * 1_000L  // 25 h > 24 h TTL
        val cachedMap = CapabilityCatalog(listOf(baselineEntryB))  // stale cache
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(CapabilityMap.serializer(), remoteMap))
        )

        val reg = registry(cacheContents = cachedMap, cacheAgeMs = staleAge)
        val result = reg.load()

        // Should contain: remote_only, shared_id (remote wins), baseline_only (from asset baseline)
        val ids = result.capabilities.map { it.id }.toSet()
        assertTrue("remote_only should be present", "remote_only" in ids)
        assertTrue("baseline_only should be present", "baseline_only" in ids)
        assertTrue("shared_id should be present", "shared_id" in ids)

        // Remote version wins for shared_id
        val shared = result.find("shared_id")
        assertNotNull(shared)
        assertEquals("Remote Shared", shared!!.label)

        assertEquals(1, server.requestCount)
    }

    // ------------------------------------------------------------------
    // 3. Cache miss + remote timeout
    // ------------------------------------------------------------------

    @Test
    fun cacheMiss_remoteTimeout_returnsStaleCacheFallback() = runTest {
        val staleAge = 25L * 60L * 60L * 1_000L
        val cachedMap = CapabilityCatalog(listOf(baselineEntryA))
        // Delay the response longer than the 100 ms timeout we'll set
        server.enqueue(MockResponse().setBodyDelay(500, TimeUnit.MILLISECONDS).setBody("{}"))

        val reg = registry(
            cacheContents = cachedMap,
            cacheAgeMs = staleAge,
            timeoutMs = 100L,
        )
        val result = reg.load()

        // Should fall back to stale cache
        assertEquals(1, result.capabilities.size)
        assertEquals("baseline_only", result.capabilities.first().id)
    }

    // ------------------------------------------------------------------
    // 4. Cache miss + remote 404
    // ------------------------------------------------------------------

    @Test
    fun cacheMiss_remote404_returnsStaleCacheFallback() = runTest {
        val staleAge = 25L * 60L * 60L * 1_000L
        val cachedMap = CapabilityCatalog(listOf(baselineEntryA))
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val reg = registry(cacheContents = cachedMap, cacheAgeMs = staleAge)
        val result = reg.load()

        assertEquals(1, result.capabilities.size)
        assertEquals("baseline_only", result.capabilities.first().id)
    }

    // ------------------------------------------------------------------
    // 5. Cache miss + malformed remote JSON
    // ------------------------------------------------------------------

    @Test
    fun cacheMiss_malformedRemoteJson_returnsStaleCacheFallback() = runTest {
        val staleAge = 25L * 60L * 60L * 1_000L
        val cachedMap = CapabilityCatalog(listOf(baselineEntryA))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{ this is not valid json }")
        )

        val reg = registry(cacheContents = cachedMap, cacheAgeMs = staleAge)
        val result = reg.load()

        assertEquals(1, result.capabilities.size)
        assertEquals("baseline_only", result.capabilities.first().id)
    }

    // ------------------------------------------------------------------
    // 6. Baseline-only fallback (no cache, no network)
    // ------------------------------------------------------------------

    @Test
    fun baselineOnlyFallback_noCacheNoNetwork() = runTest {
        // Serve a non-successful response so remote fetch fails
        server.enqueue(MockResponse().setResponseCode(503))

        // No cacheContents → no cache file at all
        val reg = registry(cacheContents = null)
        val result = reg.load()

        // Should return baseline (2 entries: baselineEntryA + baselineEntryB)
        val ids = result.capabilities.map { it.id }.toSet()
        assertTrue("baseline_only in result", "baseline_only" in ids)
        assertTrue("shared_id in result", "shared_id" in ids)
        // Note: baseline label for shared_id (no remote to override)
        assertEquals("Baseline Shared", result.find("shared_id")!!.label)
    }

    // ------------------------------------------------------------------
    // 7. refresh() forces remote fetch and updates cache
    // ------------------------------------------------------------------

    @Test
    fun refresh_forcesRemoteFetch_updatesCache() = runTest {
        // Even a fresh cache should be bypassed by refresh()
        val freshCache = CapabilityCatalog(listOf(baselineEntryA))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(CapabilityMap.serializer(), remoteMap))
        )

        val reg = registry(cacheContents = freshCache, cacheAgeMs = 0L)
        val result = reg.refresh()

        assertTrue(result.isSuccess)
        val map = result.getOrThrow()
        val ids = map.capabilities.map { it.id }.toSet()
        assertTrue("remote_only after refresh", "remote_only" in ids)
        assertEquals(1, server.requestCount)
    }

    // ------------------------------------------------------------------
    // 8. refresh() failure does NOT clear cache
    // ------------------------------------------------------------------

    @Test
    fun refresh_failure_doesNotClearCache() = runTest {
        val cachedMap = CapabilityCatalog(listOf(baselineEntryA))
        server.enqueue(MockResponse().setResponseCode(500))

        val reg = registry(cacheContents = cachedMap, cacheAgeMs = 0L)
        val result = reg.refresh()

        assertTrue(result.isFailure)

        // Cache must still be readable and unchanged → subsequent load returns cache
        server.enqueue(MockResponse().setResponseCode(500))  // another bad response
        val loaded = reg.load()  // should hit cache (still fresh)
        assertEquals(1, loaded.capabilities.size)
        assertEquals("baseline_only", loaded.capabilities.first().id)
    }

    // ------------------------------------------------------------------
    // 9. Merge: remote wins on collision
    // ------------------------------------------------------------------

    @Test
    fun merge_remoteWinsOnCollision() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(CapabilityMap.serializer(), remoteMap))
        )
        // No cache → will fetch
        val reg = registry(cacheContents = null)
        val result = reg.load()

        val shared = result.find("shared_id")
        assertNotNull(shared)
        // remoteEntryShared.label should win
        assertEquals("Remote Shared", shared!!.label)
        assertEquals("remote version", shared.note)
    }

    // ------------------------------------------------------------------
    // 10. Merge: baseline fills missing ids
    // ------------------------------------------------------------------

    @Test
    fun merge_baselineFillsMissingIds() = runTest {
        // Remote has only remoteEntryNew (no "baseline_only" entry)
        val remoteWithoutBaseline = CapabilityCatalog(listOf(remoteEntryNew))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json.encodeToString(CapabilityMap.serializer(), remoteWithoutBaseline))
        )

        val reg = registry(cacheContents = null)
        val result = reg.load()

        // "baseline_only" must be filled in from baseline asset
        assertNotNull(result.find("baseline_only"))
        // "remote_only" present from remote
        assertNotNull(result.find("remote_only"))
    }
}
