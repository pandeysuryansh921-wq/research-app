package com.ecosystem.research.core.model

import java.util.UUID

enum class ReadingStatus {
    INBOX,
    TO_SCREEN,
    TO_READ,
    READING,
    READ,
    KEY_PAPER,
    ARCHIVED,
    EXCLUDED
}

enum class StudyType {
    RCT,
    COHORT,
    CASE_CONTROL,
    CROSS_SECTIONAL,
    SYSTEMATIC_REVIEW,
    META_ANALYSIS,
    BENCHMARK,
    METHODOLOGY,
    OTHER
}

data class Source(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val title: String,
    val authors: List<String> = emptyList(),
    val journal: String? = null,
    val year: Int? = null,
    val externalIds: ExternalIdentifiers = ExternalIdentifiers(),
    val abstractText: String? = null,
    val localPdfPath: String? = null,
    val readingStatus: ReadingStatus = ReadingStatus.INBOX,
    val studyType: StudyType = StudyType.OTHER,
    val priority: Int = 0,
    val rationale: String? = null,
    val provenance: ProvenanceRecord = ProvenanceRecord(
        originType = OriginType.SOURCE_METADATA,
        verificationState = VerificationState.UNVERIFIED
    ),
    val createdAt: Long = System.currentTimeMillis()
)
