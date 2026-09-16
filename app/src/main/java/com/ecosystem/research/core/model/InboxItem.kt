package com.ecosystem.research.core.model

import java.util.UUID

enum class InboxItemType {
    DOI,
    PMID,
    URL,
    TEXT,
    SNIPPET,
    PDF_FILE,
    VOICE_IDEA
}

enum class InboxStatus {
    PENDING,
    PROCESSED,
    ARCHIVED
}

data class InboxItem(
    val id: String = UUID.randomUUID().toString(),
    val rawType: InboxItemType,
    val rawContent: String,
    val sourceApp: String, // e.g. "boloji", "likhoji", "stylus", "manual"
    val assignedProjectId: String? = null,
    val processedSourceId: String? = null,
    val status: InboxStatus = InboxStatus.PENDING,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
