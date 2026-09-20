package com.ecosystem.research.core.model

import java.util.UUID

enum class ScreeningVote {
    INCLUDE,
    EXCLUDE,
    UNSURE
}

enum class ScreeningSessionStatus {
    IN_PROGRESS,
    ARBITRATION_REQUIRED,
    COMPLETED
}

data class ScreeningSession(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val inclusionCriteria: String = "",
    val exclusionCriteria: String = "",
    val screener1Name: String = "Screener 1",
    val screener2Name: String = "Screener 2",
    val arbitratorName: String? = "Lead Investigator",
    val status: ScreeningSessionStatus = ScreeningSessionStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis()
)

data class ScreeningDecision(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val sourceId: String,
    val screenerId: String, // "screener1", "screener2", "arbitrator"
    val decision: ScreeningVote,
    val exclusionReason: String? = null,
    val notes: String? = null,
    val decidedAt: Long = System.currentTimeMillis()
)

data class ScreeningReliability(
    val sessionId: String,
    val totalScreened: Int,
    val agreedCount: Int,
    val conflictedCount: Int,
    val percentAgreement: Float,
    val cohensKappa: Float,
    val interpretation: String,
    val calculatedAt: Long = System.currentTimeMillis()
)

data class ScreeningConflict(
    val sourceId: String,
    val sourceTitle: String,
    val screener1Decision: ScreeningDecision?,
    val screener2Decision: ScreeningDecision?,
    val arbitrationDecision: ScreeningDecision? = null,
    val isResolved: Boolean = arbitrationDecision != null
)
