package com.caseforge.scanner.vin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinNormalizerTest {

  /** NHTSA-style sample with known-good check digit. */
  private val validVin = "1HGCM82633A004352"

  @Test
  fun normalizeOcrText_stripsSeparatorsAndUppercases() {
    assertEquals("1HGCM82633A004352", VinNormalizer.normalizeOcrText("1hgcm 82633-a004352"))
  }

  @Test
  fun hasValidCharset_rejectsIOQ() {
    assertTrue(VinNormalizer.hasValidCharset(validVin))
    assertFalse(VinNormalizer.hasValidCharset("1HGCM82633A00435I"))
    assertFalse(VinNormalizer.hasValidCharset("1HGCM82633A00435O"))
    assertFalse(VinNormalizer.hasValidCharset("1HGCM82633A00435Q"))
    assertFalse(VinNormalizer.hasValidCharset("SHORT"))
  }

  @Test
  fun validateCheckDigit_acceptsKnownGoodVin() {
    assertTrue(VinNormalizer.validateCheckDigit(validVin))
    assertTrue(VinNormalizer.isValidVin(validVin))
  }

  @Test
  fun validateCheckDigit_rejectsTamperedCheckDigit() {
    val bad = validVin.replaceRange(8, 9, "0")
    assertFalse(VinNormalizer.validateCheckDigit(bad))
    assertFalse(VinNormalizer.isValidVin(bad))
  }

  @Test
  fun computeCheckDigit_matchesPositionNine() {
    assertEquals(validVin[8], VinNormalizer.computeCheckDigit(validVin))
  }

  @Test
  fun extractCandidates_picksSlidingWindowFromNoisyBlock() {
    val noisy = "LABEL 1HGCM82633A004352 END"
    val candidates = VinNormalizer.extractCandidates(noisy)
    assertTrue(candidates.isNotEmpty())
    assertEquals(validVin, candidates.first().normalizedVin)
    assertTrue(candidates.first().checkDigitValid)
  }

  @Test
  fun pickBest_prefersCheckDigitValidOverInvalid() {
    val good = VinOcrCandidate("g", validVin, checkDigitValid = true)
    val badVin = validVin.replaceRange(8, 9, "0")
    val bad = VinOcrCandidate("b", badVin, checkDigitValid = false)
    val best = VinNormalizer.pickBest(listOf(bad, good))
    assertNotNull(best)
    assertEquals(validVin, best!!.normalizedVin)
  }

  @Test
  fun ocrCorrection_mapsForbiddenLetters() {
    val withO = "1HGCM82633A00435O" // 16 chars + O at end — use full 17 with O at pos
    val fragment = "1HGCM82633A0043O2" // invalid; force O in middle
    val candidates = VinNormalizer.extractCandidates("VIN $fragment")
    val normalized = candidates.firstOrNull()?.normalizedVin
    assertNotNull(normalized)
    assertFalse(normalized!!.contains('O'))
    assertFalse(normalized.contains('I'))
    assertFalse(normalized.contains('Q'))
  }

  @Test
  fun normalizeToVin_returnsNullForGarbage() {
    assertNull(VinNormalizer.normalizeToVin("not a vin at all"))
  }
}
