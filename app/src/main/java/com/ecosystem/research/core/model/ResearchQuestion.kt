package com.ecosystem.research.core.model

import java.util.UUID

data class PicoFramework(
    val population: String = "",
    val intervention: String = "",
    val comparator: String = "",
    val outcome: String = "",
    val studyDesign: String = ""
) {
    fun toQueryTerms(): List<String> {
        return listOf(population, intervention, comparator, outcome)
            .filter { it.isNotBlank() }
    }
}

data class ResearchQuestion(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val rawIdea: String,
    val pico: PicoFramework? = null,
    val formulatedQuestion: String,
    val searchStrategy: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
