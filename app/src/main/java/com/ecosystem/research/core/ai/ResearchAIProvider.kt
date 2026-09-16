package com.ecosystem.research.core.ai

import com.ecosystem.research.core.model.*

data class CandidateExtraction(
    val studyType: StudyType? = null,
    val population: String? = null,
    val sampleSize: String? = null,
    val methods: String? = null,
    val keyFindings: String? = null,
    val limitations: String? = null
)

data class ContradictionCandidate(
    val claimId: String,
    val supportingEvidenceId: String,
    val contradictingEvidenceId: String,
    val contradictionReason: String,
    val confidence: Float
)

interface ResearchAIProvider {
    suspend fun extractStructuredFields(text: String): Result<CandidateExtraction>
    suspend fun detectContradictions(claim: Claim, evidenceList: List<Evidence>): Result<List<ContradictionCandidate>>
    suspend fun synthesizeGroundedReview(question: String, claims: List<Claim>, evidenceMap: Map<String, List<Evidence>>): Result<String>
}

class HeuristicOfflineAIProvider : ResearchAIProvider {
    override suspend fun extractStructuredFields(text: String): Result<CandidateExtraction> {
        val lower = text.lowercase()
        val studyType = when {
            "randomized controlled" in lower || "rct" in lower -> StudyType.RCT
            "cohort" in lower -> StudyType.COHORT
            "case-control" in lower -> StudyType.CASE_CONTROL
            "systematic review" in lower -> StudyType.SYSTEMATIC_REVIEW
            "meta-analysis" in lower -> StudyType.META_ANALYSIS
            else -> StudyType.OTHER
        }

        val sampleSizeMatch = Regex("""(n\s*=\s*\d+[\d,]*)""", RegexOption.IGNORE_CASE).find(text)
        val sampleSize = sampleSizeMatch?.value

        return Result.success(
            CandidateExtraction(
                studyType = studyType,
                sampleSize = sampleSize,
                methods = if ("methods" in lower) "Extracted from methods section" else null,
                limitations = if ("limitation" in lower) "Mentions study limitations" else null
            )
        )
    }

    override suspend fun detectContradictions(claim: Claim, evidenceList: List<Evidence>): Result<List<ContradictionCandidate>> {
        val supporting = evidenceList.filter { it.relationshipType == EvidenceRelationship.SUPPORTS }
        val contradicting = evidenceList.filter { it.relationshipType == EvidenceRelationship.CONTRADICTS }

        val candidates = mutableListOf<ContradictionCandidate>()
        for (sup in supporting) {
            for (contra in contradicting) {
                candidates.add(
                    ContradictionCandidate(
                        claimId = claim.id,
                        supportingEvidenceId = sup.id,
                        contradictingEvidenceId = contra.id,
                        contradictionReason = "Source [${sup.sourceId}] supports this claim, whereas source [${contra.sourceId}] directly contradicts it.",
                        confidence = 0.90f
                    )
                )
            }
        }
        return Result.success(candidates)
    }

    override suspend fun synthesizeGroundedReview(
        question: String,
        claims: List<Claim>,
        evidenceMap: Map<String, List<Evidence>>
    ): Result<String> {
        val sb = StringBuilder()
        sb.append("# Synthesis for: $question\n\n")
        sb.append("## Key Claims & Evidence Overview\n\n")

        for (claim in claims) {
            sb.append("### Claim: ${claim.proposition} (${claim.status})\n")
            val evList = evidenceMap[claim.id] ?: emptyList()
            if (evList.isEmpty()) {
                sb.append("*No evidence currently attached to this proposition.*\n\n")
            } else {
                for (ev in evList) {
                    val relBadge = when (ev.relationshipType) {
                        EvidenceRelationship.SUPPORTS -> "[SUPPORTS]"
                        EvidenceRelationship.CONTRADICTS -> "[CONTRADICTS]"
                        EvidenceRelationship.MIXED -> "[MIXED]"
                        else -> "[BACKGROUND]"
                    }
                    sb.append("- $relBadge Page ${ev.location.pageNumber}: \"${ev.excerptText}\"\n")
                    if (!ev.userInterpretation.isNullOrBlank()) {
                        sb.append("  *Interpretation*: ${ev.userInterpretation}\n")
                    }
                }
                sb.append("\n")
            }
        }
        return Result.success(sb.toString())
    }
}
