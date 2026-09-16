package com.ecosystem.research

import com.ecosystem.research.core.ai.HeuristicOfflineAIProvider
import com.ecosystem.research.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProvenanceAndAiTest {

    @Test
    fun testProvenanceFlags() {
        val aiRecord = ProvenanceRecord(
            originType = OriginType.AI_EXTRACTED,
            verificationState = VerificationState.UNVERIFIED
        )
        assertTrue(aiRecord.isAiDerived)
        assertFalse(aiRecord.isConfirmed)

        val confirmedUserRecord = ProvenanceRecord(
            originType = OriginType.DIRECT_EXCERPT,
            verificationState = VerificationState.CONFIRMED
        )
        assertFalse(confirmedUserRecord.isAiDerived)
        assertTrue(confirmedUserRecord.isConfirmed)
    }

    @Test
    fun testContradictionDetection() = runBlocking {
        val provider = HeuristicOfflineAIProvider()
        val claim = Claim(
            projectId = "p-1",
            proposition = "Multimodal foundation models generalize effectively to clinical screening."
        )

        val ev1 = Evidence(
            projectId = "p-1",
            sourceId = "s-1",
            location = SourceLocation(pageNumber = 4),
            excerptText = "Model achieved AUROC 0.94 on external hospital dataset.",
            relationshipType = EvidenceRelationship.SUPPORTS
        )

        val ev2 = Evidence(
            projectId = "p-1",
            sourceId = "s-2",
            location = SourceLocation(pageNumber = 12),
            excerptText = "Model suffered severe performance degradation on non-training demographics (AUROC 0.61).",
            relationshipType = EvidenceRelationship.CONTRADICTS
        )

        val result = provider.detectContradictions(claim, listOf(ev1, ev2))
        assertTrue(result.isSuccess)
        val candidates = result.getOrThrow()
        assertEquals(1, candidates.size)
        assertEquals(claim.id, candidates[0].claimId)
        assertEquals(ev1.id, candidates[0].supportingEvidenceId)
        assertEquals(ev2.id, candidates[0].contradictingEvidenceId)
    }

    @Test
    fun testStructuredFieldExtraction() = runBlocking {
        val provider = HeuristicOfflineAIProvider()
        val sampleText = "In this randomized controlled trial of n = 1,420 patients with diabetic retinopathy, we evaluated..."

        val result = provider.extractStructuredFields(sampleText)
        assertTrue(result.isSuccess)
        val extracted = result.getOrThrow()
        assertEquals(StudyType.RCT, extracted.studyType)
        assertNotNull(extracted.sampleSize)
        assertTrue(extracted.sampleSize!!.contains("1,420"))
    }
}
