package com.ecosystem.research

import com.ecosystem.research.core.analytics.RecommendationPriority
import com.ecosystem.research.core.analytics.ResearchGapEngine
import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.repository.ResearchRepository
import org.junit.Assert.*
import org.junit.Test

class ResearchGapEngineTest {

    @Test
    fun testAuditProjectDetectsUnsupportedClaims() {
        val project = ResearchProject(id = "p1", title = "AI Scalability")
        val supportedClaim = Claim(id = "c1", projectId = "p1", proposition = "Scaling laws hold for parameter count.")
        val unsupportedClaim = Claim(id = "c2", projectId = "p1", proposition = "Scaling laws hold for infinite context length.")

        val source = Source(id = "s1", projectId = "p1", title = "Scaling Laws for Neural Language Models")
        val evidence = Evidence(
            id = "e1",
            projectId = "p1",
            sourceId = "s1",
            excerptText = "Performance scales as a power-law.",
            location = SourceLocation(pageNumber = 1)
        )
        val edges = listOf(
            ResearchRepository.GraphEdge(
                claimId = "c1",
                evidenceId = "e1",
                sourceId = "s1",
                relationshipType = EvidenceRelationship.SUPPORTS,
                excerptText = evidence.excerptText
            )
        )

        val report = ResearchGapEngine.auditProject(
            project = project,
            claims = listOf(supportedClaim, unsupportedClaim),
            sources = listOf(source),
            evidenceList = listOf(evidence),
            edges = edges
        )

        assertEquals(2, report.totalClaims)
        assertEquals(1, report.supportedClaimsCount)
        assertEquals(1, report.unsupportedClaims.size)
        assertEquals("c2", report.unsupportedClaims.first().id)
        assertTrue(report.recommendations.any { it.priority == RecommendationPriority.CRITICAL && it.targetClaimId == "c2" })
    }

    @Test
    fun testAuditProjectDetectsContestedClaimsAndConflictRatio() {
        val project = ResearchProject(id = "p1", title = "Quantum Supremacy")
        val contestedClaim = Claim(id = "c1", projectId = "p1", proposition = "Quantum advantage achieved on random circuit sampling.")

        val source1 = Source(id = "s1", projectId = "p1", title = "Quantum Supremacy Using a Programmable Superconducting Processor")
        val source2 = Source(id = "s2", projectId = "p1", title = "Classical Simulation of Random Circuit Sampling in Seconds")

        val edges = listOf(
            ResearchRepository.GraphEdge(
                claimId = "c1",
                evidenceId = "e1",
                sourceId = "s1",
                relationshipType = EvidenceRelationship.SUPPORTS,
                excerptText = "Our Sycamore processor takes 200 seconds."
            ),
            ResearchRepository.GraphEdge(
                claimId = "c1",
                evidenceId = "e2",
                sourceId = "s2",
                relationshipType = EvidenceRelationship.CONTRADICTS,
                excerptText = "Classical algorithms can simulate this in 14 seconds."
            )
        )

        val report = ResearchGapEngine.auditProject(
            project = project,
            claims = listOf(contestedClaim),
            sources = listOf(source1, source2),
            evidenceList = emptyList(),
            edges = edges
        )

        assertEquals(1, report.contestedClaims.size)
        val summary = report.contestedClaims.first()
        assertEquals(1, summary.supportCount)
        assertEquals(1, summary.contradictionCount)
        assertEquals(0.5f, summary.conflictRatio, 0.01f)
        assertTrue(report.recommendations.any { it.priority == RecommendationPriority.WARNING && it.targetClaimId == "c1" })
    }

    @Test
    fun testAuditProjectDetectsSingleSourceFragility() {
        val project = ResearchProject(id = "p1", title = "Gene Editing")
        val singleSourceClaim = Claim(id = "c1", projectId = "p1", proposition = "Prime editing achieves targeted insertions without double strand breaks.")

        val source = Source(id = "s1", projectId = "p1", title = "Search-and-replace genome editing without double-strand breaks")

        val edges = listOf(
            ResearchRepository.GraphEdge(
                claimId = "c1",
                evidenceId = "e1",
                sourceId = "s1",
                relationshipType = EvidenceRelationship.SUPPORTS,
                excerptText = "We describe prime editing..."
            ),
            ResearchRepository.GraphEdge(
                claimId = "c1",
                evidenceId = "e2",
                sourceId = "s1",
                relationshipType = EvidenceRelationship.SUPPORTS,
                excerptText = "Point mutations were made with high efficiency."
            )
        )

        val report = ResearchGapEngine.auditProject(
            project = project,
            claims = listOf(singleSourceClaim),
            sources = listOf(source),
            evidenceList = emptyList(),
            edges = edges
        )

        assertEquals(1, report.singleSourceClaims.size)
        assertEquals("s1", report.singleSourceClaims.first().sourceId)
        assertTrue(report.recommendations.any { it.priority == RecommendationPriority.OPPORTUNITY && it.targetSourceId == "s1" })
    }
}
