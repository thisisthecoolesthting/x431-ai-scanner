package com.caseforge.scanner.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDtcLookupTest {

  private val sampleDtcJson =
      """
      {
        "version": 1,
        "entries": [
          {
            "code": "P0300",
            "title": "Random/Multiple Cylinder Misfire Detected",
            "summary": "Misfire on multiple cylinders.",
            "likelyCauses": ["Spark plugs"],
            "firstChecks": ["Swap coils"],
            "tags": ["misfire"]
          },
          {
            "code": "P0171",
            "title": "System Too Lean (Bank 1)",
            "summary": "Excess oxygen on bank 1.",
            "tags": ["lean", "fuel trim"]
          }
        ]
      }
      """
          .trimIndent()

  private val sampleTestsJson =
      """
      {
        "version": 1,
        "tests": [
          {
            "id": "maf_sanity_idle",
            "title": "MAF sensor sanity at idle",
            "summary": "Check MAF grams per second.",
            "relatedCodes": ["P0101", "P0171"],
            "steps": ["Warm engine", "Log MAF at idle"]
          }
        ]
      }
      """
          .trimIndent()

  private val lookup: OfflineDtcLookup by lazy {
    OfflineDtcLookup.fromBundle(OfflineBundle.fromJson(sampleDtcJson, sampleTestsJson))
  }

  @Test
  fun normalizeCode_acceptsCommonForms() {
    assertEquals("P0300", OfflineDtcLookup.normalizeCode("p0300"))
    assertEquals("P0300", OfflineDtcLookup.normalizeCode("0300"))
    assertEquals("P0300", OfflineDtcLookup.normalizeCode(" P-0300 "))
  }

  @Test
  fun lookup_returnsEntryForNormalizedCode() {
    assertNotNull(lookup.lookup("0300"))
    assertEquals("P0300", lookup.lookup("P0300")?.code)
    assertNull(lookup.lookup("P9999"))
  }

  @Test
  fun search_matchesTitleAndTags() {
    val lean = lookup.search("lean")
    assertTrue(lean.any { it.code == "P0171" })

    val misfire = lookup.search("misfire")
    assertTrue(misfire.any { it.code == "P0300" })
  }

  @Test
  fun guidedTestsForCode_linksRelatedSnippets() {
    val tests = lookup.guidedTestsForCode("P0171")
    assertEquals(1, tests.size)
    assertEquals("maf_sanity_idle", tests.first().id)
  }
}
