package com.ecosystem.research.core.model

enum class OriginType {
    SOURCE_METADATA,
    DIRECT_EXCERPT,
    USER_WRITTEN,
    AI_EXTRACTED,
    AI_INFERRED
}

enum class VerificationState {
    UNVERIFIED,
    USER_REVIEWED,
    CONFIRMED,
    FLAGGED_CONTRADICTORY
}

data class SourceLocation(
    val pageNumber: Int,
    val charOffsetStart: Int? = null,
    val charOffsetEnd: Int? = null,
    val section: String? = null,
    val boundingBox: String? = null
)

data class Attribution(
    val createdBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val promptFingerprint: String? = null
)

data class ProvenanceRecord(
    val originType: OriginType,
    val verificationState: VerificationState,
    val location: SourceLocation? = null,
    val attribution: Attribution = Attribution(createdBy = "user")
) {
    val isConfirmed: Boolean
        get() = verificationState == VerificationState.CONFIRMED

    val isAiDerived: Boolean
        get() = originType == OriginType.AI_EXTRACTED || originType == OriginType.AI_INFERRED
}
