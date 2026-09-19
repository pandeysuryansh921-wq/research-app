package com.ecosystem.research.core.ecosystem

import com.ecosystem.research.core.model.Claim
import com.ecosystem.research.core.model.ClaimStatus
import com.ecosystem.research.core.model.Evidence
import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.ResearchProject
import com.ecosystem.research.core.model.Source
import com.ecosystem.research.core.model.VerificationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates publication-ready academic manuscripts formatted specifically for Likhoji
 * (the ecosystem's deep writing and publishing engine).
 *
 * Adheres to strict provenance: every assertion is tied to empirical study coordinates
 * with academic footnotes and formatted BibTeX bibliographies.
 */
object ManuscriptExportEngine {

    data class ExportOptions(
        val includeYamlFrontmatter: Boolean = true,
        val includeLiteratureReview: Boolean = true,
        val includeEvidenceMatrix: Boolean = true,
        val includeContradictionsSection: Boolean = true,
        val includeBibTeXReferences: Boolean = true,
        val includeFootnotes: Boolean = true
    )

    fun generateManuscript(
        project: ResearchProject,
        claims: List<Claim>,
        claimEvidenceMap: Map<String, List<Pair<Evidence, Source?>>>,
        sources: List<Source>,
        synthesisReport: String? = null,
        options: ExportOptions = ExportOptions()
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        // 1. YAML Frontmatter for Likhoji composition engine
        if (options.includeYamlFrontmatter) {
            sb.appendLine("---")
            sb.appendLine("title: \"${escapeYaml(project.title)}\"")
            sb.appendLine("project_id: \"${project.id}\"")
            sb.appendLine("date: \"$currentDate\"")
            sb.appendLine("generated_by: \"Research App (Ecosystem Synthesis Engine)\"")
            sb.appendLine("target_editor: \"Likhoji\"")
            sb.appendLine("claims_count: ${claims.size}")
            sb.appendLine("sources_count: ${sources.size}")
            sb.appendLine("total_evidence_excerpts: ${claimEvidenceMap.values.sumOf { it.size }}")
            sb.appendLine("---")
            sb.appendLine()
        }

        // 2. Title & Project Header
        sb.appendLine("# ${project.title}")
        sb.appendLine()
        val desc = project.description
        if (!desc.isNullOrBlank()) {
            sb.appendLine("> **Project Abstract / Research Question:**")
            sb.appendLine("> ${desc.replace("\n", "\n> ")}")
            sb.appendLine()
        }

        // 3. Grounded Synthesis Review (if generated)
        if (options.includeLiteratureReview && !synthesisReport.isNullOrBlank()) {
            sb.appendLine("## 1. Grounded Literature Synthesis")
            sb.appendLine()
            sb.appendLine(synthesisReport.trim())
            sb.appendLine()
        }

        // 4. Claims Ledger & Empirical Evidence Trace
        if (options.includeEvidenceMatrix && claims.isNotEmpty()) {
            sb.appendLine("## 2. Empirical Claims & Evidence Graph")
            sb.appendLine()
            sb.appendLine("This section outlines verified project claims and their supporting/contradicting empirical sources.")
            sb.appendLine()

            var footnoteIndex = 1
            val footnotes = mutableListOf<String>()

            claims.forEachIndexed { idx, claim ->
                val claimNumber = idx + 1
                val statusBadge = claim.status.name
                val evidenceList = claimEvidenceMap[claim.id] ?: emptyList()
                val confidencePct = when (claim.provenance.verificationState) {
                    VerificationState.CONFIRMED -> 100
                    VerificationState.USER_REVIEWED -> 95
                    VerificationState.FLAGGED_CONTRADICTORY -> 40
                    VerificationState.UNVERIFIED -> if (claim.status == ClaimStatus.SUPPORTED) 90 else 65
                }

                sb.appendLine("### 2.$claimNumber Claim: ${claim.proposition}")
                sb.appendLine("**Verification Status:** `$statusBadge` | **Confidence:** $confidencePct%")
                sb.appendLine()

                if (evidenceList.isEmpty()) {
                    sb.appendLine("*No empirical evidence linked to this claim yet.*")
                } else {
                    sb.appendLine("| Source | Relationship | Evidence Excerpt | Coordinates |")
                    sb.appendLine("|---|:---:|---|:---:|")

                    evidenceList.forEach { (evidence, source) ->
                        val sourceTitle = source?.title ?: "Unknown Source"
                        val authorShort = source?.authors?.firstOrNull() ?: "Unknown"
                        val year = source?.year?.toString() ?: "n.d."
                        val pageCoord = evidence.location.pageNumber.let { "Page $it" }
                        val rel = evidence.relationshipType.name

                        val cleanExcerpt = evidence.excerptText.replace("|", "\\|").replace("\n", " ")
                        val shortExcerpt = if (cleanExcerpt.length > 120) cleanExcerpt.take(117) + "..." else cleanExcerpt

                        if (options.includeFootnotes) {
                            val fnNum = footnoteIndex++
                            footnotes.add("[^$fnNum]: **$authorShort et al. ($year)**, *$sourceTitle*. Location: $pageCoord. Excerpt: \"${cleanExcerpt}\"")
                            sb.appendLine("| $authorShort ($year)[^$fnNum] | `$rel` | \"$shortExcerpt\" | $pageCoord |")
                        } else {
                            sb.appendLine("| $authorShort ($year) | `$rel` | \"$shortExcerpt\" | $pageCoord |")
                        }
                    }
                }
                sb.appendLine()
            }

            // Append Academic Footnotes
            if (options.includeFootnotes && footnotes.isNotEmpty()) {
                sb.appendLine("### Evidence Footnotes & Verbatim Quotes")
                sb.appendLine()
                footnotes.forEach { fn ->
                    sb.appendLine(fn)
                }
                sb.appendLine()
            }
        }

        // 5. Contradictions & Scholarly Controversies Section
        if (options.includeContradictionsSection) {
            val claimsWithContradictions = claims.filter { claim ->
                val ev = claimEvidenceMap[claim.id] ?: emptyList()
                val hasSupport = ev.any { it.first.relationshipType == EvidenceRelationship.SUPPORTS }
                val hasContradict = ev.any { it.first.relationshipType == EvidenceRelationship.CONTRADICTS }
                hasSupport && hasContradict
            }

            if (claimsWithContradictions.isNotEmpty()) {
                sb.appendLine("## 3. Scholarly Controversies & Contradictory Evidence")
                sb.appendLine()
                sb.appendLine("The following claims have conflicting empirical evidence in the literature:")
                sb.appendLine()

                claimsWithContradictions.forEach { claim ->
                    val ev = claimEvidenceMap[claim.id] ?: emptyList()
                    val supporting = ev.filter { it.first.relationshipType == EvidenceRelationship.SUPPORTS }
                    val contradicting = ev.filter { it.first.relationshipType == EvidenceRelationship.CONTRADICTS }

                    sb.appendLine("#### Controversy: \"${claim.proposition}\"")
                    sb.appendLine("- **Supporting Studies (${supporting.size}):** " +
                            supporting.joinToString(", ") { "${it.second?.authors?.firstOrNull() ?: "Author"} (${it.second?.year ?: "n.d."})" })
                    sb.appendLine("- **Contradicting Studies (${contradicting.size}):** " +
                            contradicting.joinToString(", ") { "${it.second?.authors?.firstOrNull() ?: "Author"} (${it.second?.year ?: "n.d."})" })
                    sb.appendLine()
                }
            }
        }

        // 6. Comprehensive BibTeX Bibliography for Likhoji
        if (options.includeBibTeXReferences && sources.isNotEmpty()) {
            sb.appendLine("## 4. Complete Bibliography (BibTeX)")
            sb.appendLine()
            sb.appendLine("```bibtex")
            sources.forEach { source ->
                sb.appendLine(CitationExportEngine.generateBibTeX(source))
                sb.appendLine()
            }
            sb.appendLine("```")
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun escapeYaml(input: String): String {
        return input.replace("\"", "\\\"").replace("\n", " ")
    }
}
