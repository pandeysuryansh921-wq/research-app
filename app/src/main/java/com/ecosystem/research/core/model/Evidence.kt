package com.ecosystem.research.core.model

import java.util.UUID

enum class EvidenceRelationship {
    SUPPORTS,
    CONTRADICTS,
    MIXED,
    BACKGROUND,
    RELATED,
    UNDETERMINED
}

data class Evidence(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val sourceId: String,
    val location: SourceLocation,
    val excerptText: String,
    val userInterpretation: String? = null,
    val relationshipType: EvidenceRelationship = EvidenceRelationship.UNDETERMINED,
    val provenance: ProvenanceRecord = ProvenanceRecord(
        originType = OriginType.DIRECT_EXCERPT,
        verificationState = VerificationState.UNVERIFIED,
        location = location
    ),
    val createdAt: Long = System.currentTimeMillis()
)
