package com.caseforge.scanner.agent.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionDiagnosticVisionTest {

    @Test
    fun parseInsightsJson_acceptsRawObject() {
        val raw = """
            {
              "findings": [{"area":"engine_bay","observation":"Oil seep near valve cover","severity":"watch"}],
              "confidence": "medium",
              "suggestedNextSteps": ["Confirm leak with engine running"],
              "perPhoto": [{"role":"engine_bay","bullets":["Valve cover seep visible"]}]
            }
        """.trimIndent()
        val parsed = SessionDiagnosticVision.parseInsightsJson(raw)
        assertNotNull(parsed)
        assertEquals("medium", parsed?.confidence)
        assertEquals(1, parsed?.findings?.size)
        assertEquals("engine_bay", parsed?.perPhoto?.first()?.role)
    }

    @Test
    fun parseInsightsJson_stripsMarkdownFence() {
        val raw = """
            ```json
            {"findings":[],"confidence":"low","suggestedNextSteps":[],"perPhoto":[]}
            ```
        """.trimIndent()
        val parsed = SessionDiagnosticVision.parseInsightsJson(raw)
        assertEquals("low", parsed?.confidence)
    }
}
