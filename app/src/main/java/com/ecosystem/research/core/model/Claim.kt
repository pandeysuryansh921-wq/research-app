package com.ecosystem.research.core.model

import java.util.UUID

enum class ClaimStatus {
    PROPOSED,
    SUPPORTED,
    CONTESTED,
    REFUTED,
    SYNTHESIZED
}

data class Claim(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val proposition: String,
    val status: ClaimStatus = ClaimStatus.PROPOSED,
    val notes: String? = null,
    val provenance: ProvenanceRecord = ProvenanceRecord(
        originType = OriginType.USER_WRITTEN,
        verificationState = VerificationState.UNVERIFIED
    ),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
