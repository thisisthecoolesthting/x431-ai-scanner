package com.caseforge.scanner.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdateCoordinatorTest {

    @Test
    fun updateChannel_parsesFromBundledJsonShape() {
        val raw = """
            {
              "schemaVersion": 1,
              "channelLabel": "test",
              "bundleManifestUrl": "https://example.com/manifest.json",
              "apk": {
                "releaseApiUrl": "https://api.github.com/repos/org/repo/releases/latest",
                "downloadUrl": "https://example.com/app.apk"
              }
            }
        """.trimIndent()
        val ch = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<UpdateChannel>(raw)
        assertEquals(1, ch.schemaVersion)
        assertTrue(ch.bundleManifestUrl.startsWith("https://"))
    }

    @Test
    fun remoteManifest_listsWindstarPath() {
        val raw = """
            {
              "schemaVersion": 1,
              "revision": "abc",
              "files": [
                { "path": "planb/vehicle-profiles/ford-windstar-2000.json" }
              ]
            }
        """.trimIndent()
        val m = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<RemoteUpdateManifest>(raw)
        assertEquals("ford-windstar-2000.json", m.files.single().path.substringAfterLast('/'))
    }
}
