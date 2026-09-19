package com.ecosystem.research.core.analytics

import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.repository.ResearchRepository

enum class RecommendationPriority {
    CRITICAL,
    WARNING,
    OPPORTUNITY
}

data class EpistemicRecommendation(
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val targetClaimId: String? = null,
    val targetSourceId: String? = null
)

data class ContestedClaimSummary(
    val claim: Claim,
    val supportCount: Int,
    val contradictionCount: Int,
    val conflictRatio: Float // contradiction / total
)

data class SingleSourceClaimSummary(
    val claim: Claim,
    val sourceId: String,
    val sourceTitle: String
)

data class EpistemicAuditReport(
    val projectId: String,
    val projectTitle: String,
    val healthScore: Int, // 0 - 100
    val totalClaims: Int,
    val supportedClaimsCount: Int,
    val unsupportedClaims: List<Claim>,
    val contestedClaims: List<ContestedClaimSummary>,
    val singleSourceClaims: List<SingleSourceClaimSummary>,
    val unreadKeyPapers: List<Source>,
    val recommendations: List<EpistemicRecommendation>
)

object ResearchGapEngine {

    fun auditProject(
        project: ResearchProject,
        claims: List<Claim>,
        sources: List<Source>,
        evidenceList: List<Evidence>,
        edges: List<ResearchRepository.GraphEdge>
    ): EpistemicAuditReport {
        val projectClaims = claims.filter { it.projectId == project.id }
        val projectSources = sources.filter { it.projectId == project.id }
        val projectEvidence = evidenceList.filter { it.projectId == project.id }

        // Group edges by claimId
        val edgesByClaim = edges.groupBy { it.claimId }

        // 1. Unsupported claims (0 evidence edges)
        val unsupportedClaims = projectClaims.filter { claim ->
            val linkedEdges = edgesByClaim[claim.id] ?: emptyList()
            linkedEdges.isEmpty()
        }

        // 2. Contested claims (both SUPPORTS and CONTRADICTS edges)
        val contestedClaims = mutableListOf<ContestedClaimSummary>()
        for (claim in projectClaims) {
            val linked = edgesByClaim[claim.id] ?: emptyList()
            val supports = linked.count { it.relationshipType == EvidenceRelationship.SUPPORTS }
            val contradicts = linked.count { it.relationshipType == EvidenceRelationship.CONTRADICTS }

            if (supports > 0 && contradicts > 0) {
                val ratio = contradicts.toFloat() / (supports + contradicts).toFloat()
                contestedClaims.add(
                    ContestedClaimSummary(
                        claim = claim,
                        supportCount = supports,
                        contradictionCount = contradicts,
                        conflictRatio = ratio
                    )
                )
            }
        }

        // 3. Single-Source Claims (backed by only 1 distinct source)
        val singleSourceClaims = mutableListOf<SingleSourceClaimSummary>()
        for (claim in projectClaims) {
            val linked = edgesByClaim[claim.id] ?: emptyList()
            val distinctSources = linked.map { it.sourceId }.distinct()
            if (distinctSources.size == 1) {
                val sId = distinctSources.first()
                val sTitle = projectSources.find { it.id == sId }?.title ?: "Unknown Source"
                singleSourceClaims.add(
                    SingleSourceClaimSummary(
                        claim = claim,
                        sourceId = sId,
                        sourceTitle = sTitle
                    )
                )
            }
        }

        // 4. Unread Key Literature (marked KEY_PAPER or TO_READ with 0 evidence extracted)
        val sourceEvidenceCounts = projectEvidence.groupingBy { it.sourceId }.eachCount()
        val unreadKeyPapers = projectSources.filter { src ->
            (src.readingStatus == ReadingStatus.KEY_PAPER || src.readingStatus == ReadingStatus.TO_READ) &&
                    (sourceEvidenceCounts[src.id] ?: 0) == 0
        }

        // 5. Calculate Quantitative Epistemic Health Score (0 - 100)
        var score = 100

        if (projectClaims.isNotEmpty()) {
            val supportedCount = projectClaims.size - unsupportedClaims.size
            val supportRatio = supportedCount.toFloat() / projectClaims.size.toFloat()
            // Claims coverage accounts for 40 points
            val claimCoveragePenalty = ((1.0f - supportRatio) * 40).toInt()
            score -= claimCoveragePenalty

            // Single source vulnerability penalty (up to 20 points)
            if (supportedCount > 0) {
                val singleRatio = singleSourceClaims.size.toFloat() / supportedCount.toFloat()
                val singlePenalty = (singleRatio * 20).toInt()
                score -= singlePenalty
            }

            // Contested claims penalty (up to 15 points if conflicting evidence is unresolved)
            if (contestedClaims.isNotEmpty()) {
                val contestedPenalty = (contestedClaims.size * 5).coerceAtMost(15)
                score -= contestedPenalty
            }
        } else {
            // No claims formulated yet
            score -= 30
        }

        // Unread key papers penalty (up to 15 points)
        if (unreadKeyPapers.isNotEmpty()) {
            val unreadPenalty = (unreadKeyPapers.size * 3).coerceAtMost(15)
            score -= unreadPenalty
        }

        val healthScore = score.coerceIn(0, 100)

        // 6. Actionable Epistemic Recommendations
        val recommendations = mutableListOf<EpistemicRecommendation>()

        for (unsupported in unsupportedClaims) {
            recommendations.add(
                EpistemicRecommendation(
                    priority = RecommendationPriority.CRITICAL,
                    title = "Ground Unsupported Hypothesis",
                    description = "Claim \"${unsupported.proposition.take(60)}...\" has 0 empirical evidence linked. Extract excerpts or cite primary literature.",
                    targetClaimId = unsupported.id
                )
            )
        }

        for (contested in contestedClaims) {
            recommendations.add(
                EpistemicRecommendation(
                    priority = RecommendationPriority.WARNING,
                    title = "Resolve Scholarly Controversy",
                    description = "Claim \"${contested.claim.proposition.take(50)}...\" has ${contested.supportCount} supporting vs ${contested.contradictionCount} contradicting findings. Synthesize methodology differences.",
                    targetClaimId = contested.claim.id
                )
            )
        }

        for (single in singleSourceClaims) {
            recommendations.add(
                EpistemicRecommendation(
                    priority = RecommendationPriority.OPPORTUNITY,
                    title = "Corroborate Single-Source Claim",
                    description = "Claim \"${single.claim.proposition.take(50)}...\" relies entirely on \"${single.sourceTitle.take(40)}\". Search for independent replication.",
                    targetClaimId = single.claim.id,
                    targetSourceId = single.sourceId
                )
            )
        }

        for (paper in unreadKeyPapers) {
            recommendations.add(
                EpistemicRecommendation(
                    priority = RecommendationPriority.OPPORTUNITY,
                    title = "Extract Key Paper Evidence",
                    description = "Key paper \"${paper.title.take(50)}\" is in library but has no evidence quotes extracted yet.",
                    targetSourceId = paper.id
                )
            )
        }

        return EpistemicAuditReport(
            projectId = project.id,
            projectTitle = project.title,
            healthScore = healthScore,
            totalClaims = projectClaims.size,
            supportedClaimsCount = projectClaims.size - unsupportedClaims.size,
            unsupportedClaims = unsupportedClaims,
            contestedClaims = contestedClaims,
            singleSourceClaims = singleSourceClaims,
            unreadKeyPapers = unreadKeyPapers,
            recommendations = recommendations.sortedBy { it.priority }
        )
    }
}
