package com.ecosystem.research.core.model

import java.util.UUID

enum class IrbStatus {
    APPROVED,
    PENDING,
    EXPIRED,
    EXEMPT
}

data class IrbProtocol(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val protocolNumber: String,
    val institution: String,
    val title: String,
    val status: IrbStatus = IrbStatus.PENDING,
    val approvalDate: Long? = null,
    val expirationDate: Long? = null,
    val protocolVersion: String? = "1.0",
    val icfTemplatePath: String? = null,
    val decisionLetterPath: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if the protocol is active or expiring soon.
     * Returns: "EXPIRED", "EXPIRING_SOON" (within 30 days), "ACTIVE", or "PENDING".
     */
    fun getComplianceAlert(currentTime: Long = System.currentTimeMillis()): String {
        if (status == IrbStatus.EXEMPT) return "EXEMPT"
        if (status == IrbStatus.PENDING) return "PENDING_APPROVAL"
        if (expirationDate == null) return "ACTIVE_NO_EXPIRY"
        val remainingMs = expirationDate - currentTime
        val remainingDays = remainingMs / (1000 * 60 * 60 * 24)
        return when {
            remainingDays < 0 -> "EXPIRED"
            remainingDays <= 30 -> "EXPIRING_SOON ($remainingDays days)"
            else -> "ACTIVE ($remainingDays days remaining)"
        }
    }
}
