package com.ecosystem.research.core.analytics

import com.ecosystem.research.core.model.*
import kotlin.math.roundToInt

object DualScreeningEngine {

    /**
     * Computes Cohen's Kappa (κ) inter-rater reliability between Screener 1 and Screener 2 decisions.
     */
    fun calculateReliability(
        sessionId: String,
        decisions: List<ScreeningDecision>
    ): ScreeningReliability {
        // Group decisions by sourceId
        val decisionsBySource = decisions.groupBy { it.sourceId }

        var bothScreenedCount = 0
        var agreedCount = 0
        var conflictedCount = 0

        // Counts for marginal probabilities
        val votes = ScreeningVote.values()
        val matrix = mutableMapOf<Pair<ScreeningVote, ScreeningVote>, Int>()
        for (v1 in votes) {
            for (v2 in votes) {
                matrix[Pair(v1, v2)] = 0
            }
        }

        for ((_, sourceDecisions) in decisionsBySource) {
            val s1 = sourceDecisions.find { it.screenerId == "screener1" }
            val s2 = sourceDecisions.find { it.screenerId == "screener2" }

            if (s1 != null && s2 != null) {
                bothScreenedCount++
                val pair = Pair(s1.decision, s2.decision)
                matrix[pair] = (matrix[pair] ?: 0) + 1

                if (s1.decision == s2.decision) {
                    agreedCount++
                } else {
                    conflictedCount++
                }
            }
        }

        if (bothScreenedCount == 0) {
            return ScreeningReliability(
                sessionId = sessionId,
                totalScreened = 0,
                agreedCount = 0,
                conflictedCount = 0,
                percentAgreement = 100f,
                cohensKappa = 1.0f,
                interpretation = "No dual-screened records yet"
            )
        }

        val n = bothScreenedCount.toDouble()
        val po = agreedCount.toDouble() / n

        // Expected chance agreement: sum_k (P(S1=k) * P(S2=k))
        var pe = 0.0
        for (v in votes) {
            val s1Count = votes.sumOf { other -> matrix[Pair(v, other)] ?: 0 }
            val s2Count = votes.sumOf { other -> matrix[Pair(other, v)] ?: 0 }
            val p1 = s1Count / n
            val p2 = s2Count / n
            pe += (p1 * p2)
        }

        val kappa = if (pe >= 0.99999) {
            1.0
        } else {
            (po - pe) / (1.0 - pe)
        }

        val clampedKappa = kappa.coerceIn(-1.0, 1.0).toFloat()
        val percentAgreement = ((po * 1000.0).roundToInt() / 10.0).toFloat()

        val interpretation = interpretKappa(clampedKappa)

        return ScreeningReliability(
            sessionId = sessionId,
            totalScreened = bothScreenedCount,
            agreedCount = agreedCount,
            conflictedCount = conflictedCount,
            percentAgreement = percentAgreement,
            cohensKappa = ((clampedKappa * 1000.0).roundToInt() / 1000.0).toFloat(),
            interpretation = interpretation
        )
    }

    /**
     * Standard Landis & Koch (1977) interpretation guidelines for Cohen's Kappa.
     */
    fun interpretKappa(kappa: Float): String {
        return when {
            kappa < 0.00f -> "Poor (Disagreement)"
            kappa <= 0.20f -> "Slight Agreement"
            kappa <= 0.40f -> "Fair Agreement"
            kappa <= 0.60f -> "Moderate Agreement"
            kappa <= 0.80f -> "Substantial Agreement"
            else -> "Almost Perfect Agreement"
        }
    }

    /**
     * Identifies all sources where screener1 and screener2 disagreed, and attaches arbitrator's decision if present.
     */
    fun findConflicts(
        sources: List<Source>,
        decisions: List<ScreeningDecision>
    ): List<ScreeningConflict> {
        val decisionsBySource = decisions.groupBy { it.sourceId }
        val sourcesById = sources.associateBy { it.id }
        val conflicts = mutableListOf<ScreeningConflict>()

        for ((sourceId, sourceDecisions) in decisionsBySource) {
            val s1 = sourceDecisions.find { it.screenerId == "screener1" }
            val s2 = sourceDecisions.find { it.screenerId == "screener2" }
            val arb = sourceDecisions.find { it.screenerId == "arbitrator" }

            if (s1 != null && s2 != null && s1.decision != s2.decision) {
                val source = sourcesById[sourceId]
                conflicts.add(
                    ScreeningConflict(
                        sourceId = sourceId,
                        sourceTitle = source?.title ?: "Unknown Source ($sourceId)",
                        screener1Decision = s1,
                        screener2Decision = s2,
                        arbitrationDecision = arb,
                        isResolved = arb != null
                    )
                )
            }
        }

        return conflicts
    }
}
