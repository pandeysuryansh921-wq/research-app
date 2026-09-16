package com.ecosystem.research

import com.ecosystem.research.core.model.ResearchContractV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchContractV1Test {

    @Test
    fun testBuildUris() {
        val projectUri = ResearchContractV1.buildProjectUri("p-123")
        assertEquals("research://project/p-123", projectUri)

        val sourceUri = ResearchContractV1.buildSourceUri("s-456", 3)
        assertEquals("research://source/s-456?page=3", sourceUri)

        val claimUri = ResearchContractV1.buildClaimUri("c-789")
        assertEquals("research://claim/c-789", claimUri)

        val evidenceUri = ResearchContractV1.buildEvidenceUri("e-101")
        assertEquals("research://evidence/e-101", evidenceUri)
    }

    @Test
    fun testParseUriMock() {
        val projectUri = ResearchContractV1.buildProjectUri("p-123")
        assertTrue(projectUri.startsWith("research://project/"))
    }
}
