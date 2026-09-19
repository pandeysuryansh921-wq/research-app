package com.ecosystem.research

import android.content.Context
import com.ecosystem.research.core.ai.AIServiceFactory
import com.ecosystem.research.core.ai.HeuristicOfflineAIProvider
import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.security.AIProviderType
import com.ecosystem.research.core.security.SecureKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AIPipelineTest {

    @Test
    fun `test offline heuristic synthesis produces grounded markdown review with page citations`() = runBlocking {
        val provider = HeuristicOfflineAIProvider()
        val question = "Does multimodal pre-training improve generalizability in clinical imaging?"

        val claim = Claim(
            id = "c1",
            projectId = "p1",
            proposition = "Multimodal feature alignment improves out-of-domain AUROC.",
            status = ClaimStatus.SUPPORTED
        )

        val ev1 = Evidence(
            id = "e1",
            projectId = "p1",
            sourceId = "s1",
            location = SourceLocation(pageNumber = 7),
            excerptText = "We observe an 8.4% increase in external validation AUROC compared to unimodal baselines.",
            userInterpretation = "Strong empirical support on multi-hospital cohort.",
            relationshipType = EvidenceRelationship.SUPPORTS
        )

        val ev2 = Evidence(
            id = "e2",
            projectId = "p1",
            sourceId = "s2",
            location = SourceLocation(pageNumber = 12),
            excerptText = "Performance degradation was significant on pediatric datasets lacking text annotations.",
            userInterpretation = "Pediatric generalization failure.",
            relationshipType = EvidenceRelationship.CONTRADICTS
        )

        val result = provider.synthesizeGroundedReview(
            question = question,
            claims = listOf(claim),
            evidenceMap = mapOf("c1" to listOf(ev1, ev2))
        )

        assertTrue(result.isSuccess)
        val markdown = result.getOrThrow()
        assertTrue("Markdown must contain the research question", markdown.contains(question))
        assertTrue("Markdown must cite Page 7", markdown.contains("Page 7"))
        assertTrue("Markdown must cite Page 12", markdown.contains("Page 12"))
        assertTrue("Markdown must highlight [SUPPORTS]", markdown.contains("[SUPPORTS]"))
        assertTrue("Markdown must highlight [CONTRADICTS]", markdown.contains("[CONTRADICTS]"))
    }

    @Test
    fun `test contradiction candidate detection across opposing evidence`() = runBlocking {
        val provider = HeuristicOfflineAIProvider()
        val claim = Claim(
            id = "c1",
            projectId = "p1",
            proposition = "Targeted therapy reduces 1-year progression."
        )

        val sup = Evidence(
            id = "e1",
            projectId = "p1",
            sourceId = "studyA",
            location = SourceLocation(pageNumber = 4),
            excerptText = "Progression was reduced by 34% (p < 0.01).",
            relationshipType = EvidenceRelationship.SUPPORTS
        )

        val contra = Evidence(
            id = "e2",
            projectId = "p1",
            sourceId = "studyB",
            location = SourceLocation(pageNumber = 9),
            excerptText = "No significant difference in 1-year progression was observed (p = 0.42).",
            relationshipType = EvidenceRelationship.CONTRADICTS
        )

        val candidates = provider.detectContradictions(claim, listOf(sup, contra)).getOrThrow()
        assertEquals(1, candidates.size)
        assertEquals("e1", candidates[0].supportingEvidenceId)
        assertEquals("e2", candidates[0].contradictingEvidenceId)
        assertTrue(candidates[0].contradictionReason.contains("contradicts"))
    }
}
