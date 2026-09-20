package com.ecosystem.research.core.model

import java.util.UUID

enum class Discipline {
    MEDICAL,
    CS,
    BIOINFORMATICS,
    SOCIAL_SCIENCE,
    ENGINEERING
}

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

data class PeoFramework(
    val population: String = "",
    val exposure: String = "",
    val outcome: String = ""
) {
    fun toQueryTerms(): List<String> {
        return listOf(population, exposure, outcome).filter { it.isNotBlank() }
    }
}

data class CsBenchmarkFramework(
    val benchmarkDataset: String = "",
    val proposedAlgorithm: String = "",
    val baseline: String = "",
    val targetMetric: String = ""
) {
    fun toQueryTerms(): List<String> {
        return listOf(benchmarkDataset, proposedAlgorithm, baseline, targetMetric).filter { it.isNotBlank() }
    }
}

data class BioinformaticsFramework(
    val variant: String = "",
    val gene: String = "",
    val phenotype: String = "",
    val associationMethod: String = ""
) {
    fun toQueryTerms(): List<String> {
        return listOf(variant, gene, phenotype, associationMethod).filter { it.isNotBlank() }
    }
}

data class SocialScienceFramework(
    val targetGroup: String = "",
    val phenomenon: String = "",
    val context: String = "",
    val methodology: String = ""
) {
    fun toQueryTerms(): List<String> {
        return listOf(targetGroup, phenomenon, context, methodology).filter { it.isNotBlank() }
    }
}

data class ResearchQuestion(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val rawIdea: String,
    val discipline: Discipline = Discipline.MEDICAL,
    val pico: PicoFramework? = null,
    val peo: PeoFramework? = null,
    val csBenchmark: CsBenchmarkFramework? = null,
    val bioinformatics: BioinformaticsFramework? = null,
    val socialScience: SocialScienceFramework? = null,
    val formulatedQuestion: String,
    val searchStrategy: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

