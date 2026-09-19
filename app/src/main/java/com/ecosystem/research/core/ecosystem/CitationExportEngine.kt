package com.ecosystem.research.core.ecosystem

import com.ecosystem.research.core.model.Claim
import com.ecosystem.research.core.model.ClaimStatus
import com.ecosystem.research.core.model.Source
import com.ecosystem.research.core.model.VerificationState
import java.util.Locale

/**
 * Standardized academic citation and data table export engine.
 * Generates standard BibTeX bibliographies and CSV/TSV matrices.
 */
object CitationExportEngine {

    fun generateBibTeX(source: Source): String {
        val firstAuthor = source.authors.firstOrNull()?.split(" ")?.lastOrNull()?.lowercase(Locale.ROOT) ?: "source"
        val year = source.year ?: 2026
        val titleClean = source.title.replace("[^a-zA-Z0-9]".toRegex(), "").take(8).lowercase(Locale.ROOT)
        val citeKey = "$firstAuthor$year$titleClean"

        val authorsString = if (source.authors.isNotEmpty()) {
            source.authors.joinToString(" and ")
        } else {
            "Unknown"
        }

        val doi = source.externalIds.doi
        val url = source.externalIds.url

        val sb = StringBuilder()
        sb.appendLine("@article{$citeKey,")
        sb.appendLine("  author    = {$authorsString},")
        sb.appendLine("  title     = {${source.title}},")
        sb.append("  year      = {$year}")
        if (!doi.isNullOrBlank()) {
            sb.appendLine(",")
            sb.append("  doi       = {$doi}")
        }
        if (!url.isNullOrBlank()) {
            sb.appendLine(",")
            sb.append("  url       = {$url}")
        }
        sb.appendLine()
        sb.append("}")
        return sb.toString()
    }

    fun exportBibTeX(sources: List<Source>): String {
        return sources.joinToString("\n\n") { generateBibTeX(it) }
    }

    fun exportMatrixToCsv(
        columnTitles: List<String>,
        sources: List<Source>,
        cellValues: Map<Pair<String, String>, String> // (sourceId, columnId) -> text
    ): String {
        val sb = StringBuilder()

        // Header: Source Title, Year, Authors, [ColumnTitles]
        val header = listOf("Source Title", "Year", "Authors") + columnTitles
        sb.appendLine(header.joinToString(",") { escapeCsv(it) })

        sources.forEach { source ->
            val row = mutableListOf<String>()
            row.add(escapeCsv(source.title))
            row.add(source.year?.toString() ?: "n.d.")
            row.add(escapeCsv(source.authors.joinToString("; ")))

            columnTitles.forEach { _ ->
                val value = cellValues.entries.find { it.key.first == source.id }?.value ?: ""
                row.add(escapeCsv(value))
            }
            sb.appendLine(row.joinToString(","))
        }

        return sb.toString()
    }

    fun exportClaimsToCsv(
        claims: List<Claim>,
        evidenceCountMap: Map<String, Int>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Claim ID,Proposition,Verification Status,Confidence,Linked Evidence Count")

        claims.forEach { claim ->
            val count = evidenceCountMap[claim.id] ?: 0
            val conf = when (claim.provenance.verificationState) {
                VerificationState.CONFIRMED -> "100%"
                VerificationState.USER_REVIEWED -> "95%"
                VerificationState.FLAGGED_CONTRADICTORY -> "40%"
                VerificationState.UNVERIFIED -> if (claim.status == ClaimStatus.SUPPORTED) "90%" else "60%"
            }
            val row = listOf(
                escapeCsv(claim.id),
                escapeCsv(claim.proposition),
                claim.status.name,
                conf,
                count.toString()
            )
            sb.appendLine(row.joinToString(","))
        }

        return sb.toString()
    }

    private fun escapeCsv(input: String): String {
        var clean = input.replace("\"", "\"\"")
        if (clean.contains(",") || clean.contains("\n") || clean.contains("\"")) {
            clean = "\"$clean\""
        }
        return clean
    }
}
