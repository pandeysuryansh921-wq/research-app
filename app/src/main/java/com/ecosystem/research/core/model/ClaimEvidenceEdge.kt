package com.ecosystem.research.core.model

data class ClaimEvidenceEdge(
    val claimId: String,
    val evidenceId: String,
    val relationshipType: EvidenceRelationship,
    val reviewNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
