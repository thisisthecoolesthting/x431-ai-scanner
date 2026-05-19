package com.caseforge.scanner.ui.main

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecallsVinValidationTest {

    private val validVin = "1HGCM82633A004352"

    @Test
    fun emptyInputHasNoError() {
        assertNull(vinValidationError(""))
        assertNull(vinValidationError("   "))
    }

    @Test
    fun validVinHasNoError() {
        assertNull(vinValidationError(validVin))
    }

    @Test
    fun shortVinReportsLength() {
        assertNotNull(vinValidationError("1HGCM82633A00435"))
    }

    @Test
    fun invalidLettersReported() {
        assertNotNull(vinValidationError("1HGCM82633I004352"))
    }

    @Test
    fun badCheckDigitReported() {
        assertNotNull(vinValidationError("1HGCM82633A004353"))
    }
}
