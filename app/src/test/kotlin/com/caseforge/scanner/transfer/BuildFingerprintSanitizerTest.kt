package com.caseforge.scanner.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildFingerprintSanitizerTest {

    @Test
    fun sanitize_replacesLongHexAndTruncates() {
        val raw = "vendor/foo/" + "a".repeat(24) + "/release-keys/9876543210"
        val s = BuildFingerprintSanitizer.sanitize(raw)
        assertFalse(s.contains("a".repeat(12)))
        assertTrue(s.contains("…"))
        assertTrue(s.length <= 241)
    }

    @Test
    fun sanitize_blankReturnsEmpty() {
        assertEquals("", BuildFingerprintSanitizer.sanitize(null))
        assertEquals("", BuildFingerprintSanitizer.sanitize("   "))
    }
}
