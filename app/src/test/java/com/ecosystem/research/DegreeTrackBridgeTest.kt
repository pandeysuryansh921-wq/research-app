package com.ecosystem.research

import com.ecosystem.research.core.ecosystem.DegreeTrackBridge
import com.ecosystem.research.core.ecosystem.ThesisChapter
import com.ecosystem.research.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DegreeTrackBridgeTest {

    @Test
    fun testThesisChapterFromTag() {
        assertEquals(ThesisChapter.CH1_INTRODUCTION, ThesisChapter.fromTag("thesis:ch1"))
        assertEquals(ThesisChapter.CH2_LITERATURE_REVIEW, ThesisChapter.fromTag("chapter:ch2"))
        assertEquals(ThesisChapter.CH3_METHODOLOGY, ThesisChapter.fromTag("ch3"))
        assertEquals(ThesisChapter.CH4_RESULTS, ThesisChapter.fromTag("CH4_RESULTS"))
        assertEquals(ThesisChapter.CH5_DISCUSSION, ThesisChapter.fromTag("thesis:ch5"))
    }

    @Test
    fun testFilterClaimsByChapter() {
        val claim1 = Claim(id = "c1", projectId = "p1", proposition = "Claim 1", notes = "Mapped to thesis:ch2 literature review.")
        val claim2 = Claim(id = "c2", projectId = "p1", proposition = "Claim 2", notes = "Mapped to thesis:ch3 methodology.")
        val claim3 = Claim(id = "c3", projectId = "p1", proposition = "Claim 3", notes = "Unassigned claim")

        val ch2Claims = DegreeTrackBridge.filterClaimsByChapter(listOf(claim1, claim2, claim3), ThesisChapter.CH2_LITERATURE_REVIEW)
        assertEquals(1, ch2Claims.size)
        assertEquals("c1", ch2Claims.first().id)

        val ch3Claims = DegreeTrackBridge.filterClaimsByChapter(listOf(claim1, claim2, claim3), ThesisChapter.CH3_METHODOLOGY)
        assertEquals(1, ch3Claims.size)
        assertEquals("c2", ch3Claims.first().id)
    }

    @Test
    fun testGenerateChapterBriefFormatting() {
        val project = ResearchProject(id = "p1", title = "Neural Architecture Search", primaryQuestion = "How to optimize NAS latency?")
        val claim = Claim(id = "c1", projectId = "p1", proposition = "Weight-sharing decreases search time by 1000x.", status = ClaimStatus.SUPPORTED)

        val source = Source(
            id = "s1",
            projectId = "p1",
            title = "Efficient Neural Architecture Search via Parameter Sharing",
            authors = listOf("Hieu Pham", "Quoc V. Le"),
            year = 2018
        )
        val evidence = Evidence(id = "e1", projectId = "p1", sourceId = "s1", excerptText = "ENAS reduces GPU hours from 40,000 to 16.", location = SourceLocation(pageNumber = 2))

        val brief = DegreeTrackBridge.generateChapterBrief(
            project = project,
            chapter = ThesisChapter.CH2_LITERATURE_REVIEW,
            claims = listOf(claim),
            claimEvidenceMap = mapOf(claim.id to listOf(Pair(evidence, source)))
        )

        assertTrue(brief.contains("Neural Architecture Search"))
        assertTrue(brief.contains("Chapter 2: Literature Review"))
        assertTrue(brief.contains("Weight-sharing decreases search time by 1000x"))
        assertTrue(brief.contains("Hieu Pham et al. (2018), p. 2"))
        assertTrue(brief.contains("ENAS reduces GPU hours"))
    }
}
