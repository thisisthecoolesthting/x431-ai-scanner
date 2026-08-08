package com.caseforge.scanner.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.caseforge.scanner.agent.discovery.VehicleProfileLoader
import com.caseforge.scanner.data.SettingsRepo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionEventLoggerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanSessionLogDirs() {
        File(context.filesDir, "sessions").deleteRecursively()
        File(context.filesDir, "tcw-session-log").deleteRecursively()
    }

    @Test
    fun log_writesPerSessionFile_andAggregates_withZipEntry() {
        val sid = "test-session-1"
        SessionEventLogger.log(context, sid, "kind_a", "detail", mapOf("k" to "v"))

        val per = SessionEventLogger.sessionEventsFile(context, sid)
        assertTrue(per.isFile)
        val line = per.readText().trim()
        assertTrue(line.contains("kind_a"))
        assertTrue(line.contains("detail"))
        assertTrue(line.contains(sid))

        val agg = SessionEventLogger.aggregatedFile(context)
        assertTrue(agg.isFile)
        assertTrue(agg.readText().contains("kind_a"))

        val zipMap = SessionEventLogger.zipSidecarsIfPresent(context)
        assertEquals(SessionEventLogger.ZIP_ENTRY, zipMap.keys.single())
        assertTrue(zipMap[SessionEventLogger.ZIP_ENTRY]!!.isNotEmpty())
    }

    @Test
    fun refreshAggregatedCopy_deletesAggregateWhenNoEvents() {
        val sid = "empty-cleanup"
        val per = SessionEventLogger.sessionEventsFile(context, sid)
        per.parentFile?.mkdirs()
        per.writeText("")

        SessionEventLogger.refreshAggregatedCopy(context)
        val agg = SessionEventLogger.aggregatedFile(context)
        assertTrue(!agg.exists() || agg.length() == 0L)
    }

    @Test
    fun paths_matchContract() {
        val sid = "path-check"
        val filesDir: File = context.filesDir
        val expectedPer = File(filesDir, "sessions/$sid/${SessionEventLogger.EVENTS_FILE}")
        assertEquals(expectedPer.canonicalPath, SessionEventLogger.sessionEventsFile(context, sid).canonicalPath)

        val expectedAgg = File(filesDir, "tcw-session-log/${SessionEventLogger.EVENTS_FILE}")
        assertEquals(expectedAgg.canonicalPath, SessionEventLogger.aggregatedFile(context).canonicalPath)
        assertEquals("tcw-session-log/session_events.jsonl", SessionEventLogger.ZIP_ENTRY)
    }

    @Test
    fun harvestUploadCoordinator_merge_includesSessionSidecar_whenEventsPresent() {
        SessionEventLogger.log(context, "zip-merge-sid", "probe", "x")
        val settings = SettingsRepo(context)
        val batch =
            TabletDataHarvester.build(context, VehicleProfileLoader.DEFAULT_WINDSTAR_ID, settings = settings)
        val merged =
            batch.asZipSidecars() +
                GoldenCaptureStorage.zipSidecarsIfPresent(context) +
                SessionEventLogger.zipSidecarsIfPresent(context)
        assertTrue(merged.containsKey(HarvestBatchManifest.ZIP_ENTRY))
        assertTrue(merged.containsKey(SessionEventLogger.ZIP_ENTRY))
    }
}
