package com.ecosystem.research.core.model

import java.util.UUID

data class MatrixColumn(
    val key: String,
    val label: String,
    val type: String = "text"
)

data class MatrixCell(
    val matrixId: String,
    val sourceId: String,
    val columnKey: String,
    val cellValue: String,
    val evidenceId: String? = null,
    val verificationState: VerificationState = VerificationState.UNVERIFIED
)

data class EvidenceMatrix(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val columns: List<MatrixColumn> = defaultColumns(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun defaultColumns(): List<MatrixColumn> = listOf(
            MatrixColumn("population", "Population / N"),
            MatrixColumn("design", "Study Design"),
            MatrixColumn("intervention", "Intervention / Method"),
            MatrixColumn("comparator", "Comparator / Baseline"),
            MatrixColumn("outcome", "Primary Outcome"),
            MatrixColumn("result", "Key Results / Metric"),
            MatrixColumn("limitations", "Limitations")
        )
    }
}
