package com.ecosystem.research

import com.ecosystem.research.core.analytics.DualScreeningEngine
import com.ecosystem.research.core.model.ScreeningDecision
import com.ecosystem.research.core.model.ScreeningVote
import com.ecosystem.research.core.model.Source
import org.junit.Assert.*
import org.junit.Test

class DualScreeningEngineTest {

    @Test
    fun testPerfectAgreementKappaIsOne() {
        val decisions = listOf(
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener1", decision = ScreeningVote.INCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener2", decision = ScreeningVote.INCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener1", decision = ScreeningVote.EXCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener2", decision = ScreeningVote.EXCLUDE)
        )

        val reliability = DualScreeningEngine.calculateReliability("s1", decisions)
        assertEquals(2, reliability.totalScreened)
        assertEquals(2, reliability.agreedCount)
        assertEquals(0, reliability.conflictedCount)
        assertEquals(100f, reliability.percentAgreement, 0.01f)
        assertEquals(1.0f, reliability.cohensKappa, 0.01f)
        assertEquals("Almost Perfect Agreement", reliability.interpretation)
    }

    @Test
    fun testCompleteDisagreementKappaIsNegativeOrZero() {
        val decisions = listOf(
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener1", decision = ScreeningVote.INCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener2", decision = ScreeningVote.EXCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener1", decision = ScreeningVote.EXCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener2", decision = ScreeningVote.INCLUDE)
        )

        val reliability = DualScreeningEngine.calculateReliability("s1", decisions)
        assertEquals(2, reliability.totalScreened)
        assertEquals(0, reliability.agreedCount)
        assertEquals(2, reliability.conflictedCount)
        assertEquals(0f, reliability.percentAgreement, 0.01f)
        assertTrue(reliability.cohensKappa <= 0.0f)
        assertEquals("Poor (Disagreement)", reliability.interpretation)
    }

    @Test
    fun testConflictIdentificationAndArbitration() {
        val sources = listOf(
            Source(id = "src1", title = "Trial on Drug A"),
            Source(id = "src2", title = "Cohort on Drug B")
        )

        val decisions = listOf(
            // src1 is in conflict (Include vs Exclude)
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener1", decision = ScreeningVote.INCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src1", screenerId = "screener2", decision = ScreeningVote.EXCLUDE),

            // src2 is agreed (Include vs Include)
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener1", decision = ScreeningVote.INCLUDE),
            ScreeningDecision(sessionId = "s1", sourceId = "src2", screenerId = "screener2", decision = ScreeningVote.INCLUDE)
        )

        val conflicts = DualScreeningEngine.findConflicts(sources, decisions)
        assertEquals(1, conflicts.size)
        assertEquals("src1", conflicts[0].sourceId)
        assertFalse(conflicts[0].isResolved)

        // Now resolve conflict with arbitrator decision
        val arbitratedDecisions = decisions + ScreeningDecision(
            sessionId = "s1",
            sourceId = "src1",
            screenerId = "arbitrator",
            decision = ScreeningVote.INCLUDE,
            exclusionReason = "Arbitrator included after review of primary outcomes"
        )

        val resolvedConflicts = DualScreeningEngine.findConflicts(sources, arbitratedDecisions)
        assertEquals(1, resolvedConflicts.size)
        assertTrue(resolvedConflicts[0].isResolved)
        assertEquals(ScreeningVote.INCLUDE, resolvedConflicts[0].arbitrationDecision?.decision)
    }
}
