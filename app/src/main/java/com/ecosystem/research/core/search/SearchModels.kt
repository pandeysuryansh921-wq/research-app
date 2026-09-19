package com.ecosystem.research.core.search

import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.VerificationState

enum class SearchEntityType {
    ALL,
    EVIDENCE,
    SOURCE,
    CLAIM,
    INBOX
}

data class SearchFilter(
    val entityType: SearchEntityType = SearchEntityType.ALL,
    val projectId: String? = null,
    val relationshipType: EvidenceRelationship? = null
)

data class SearchResult(
    val id: String,
    val entityType: SearchEntityType,
    val title: String,
    val snippet: String,
    val matchedField: String,
    val projectId: String? = null,
    val projectName: String? = null,
    val sourceId: String? = null,
    val sourceTitle: String? = null,
    val pageNumber: Int? = null,
    val localFilePath: String? = null,
    val verificationState: VerificationState? = null,
    val relationshipType: EvidenceRelationship? = null,
    val timestamp: Long = System.currentTimeMillis()
)
