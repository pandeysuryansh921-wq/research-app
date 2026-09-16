package com.ecosystem.research.core.model

import java.util.UUID

enum class ProjectStatus {
    ACTIVE,
    IN_REVIEW,
    SYNTHESIZED,
    ARCHIVED
}

data class ResearchProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val primaryQuestion: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
