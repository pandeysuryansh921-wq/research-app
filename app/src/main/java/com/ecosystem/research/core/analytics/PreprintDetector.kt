package com.ecosystem.research.core.analytics

import com.ecosystem.research.core.model.Source

data class PreprintDetectionResult(
    val isPreprint: Boolean,
    val sourceName: String? = null,
    val confidencePenalty: Float = if (isPreprint) 0.5f else 1.0f,
    val warningMessage: String? = if (isPreprint) "⚠️ Preprint (not peer-reviewed) - 50% confidence penalty applied" else null,
    val canRecommendClinically: Boolean = !isPreprint
)

object PreprintDetector {

    // Known preprint DOI prefixes
    private val PREPRINT_DOI_PREFIXES = mapOf(
        "10.1101/" to "bioRxiv/medRxiv",
        "10.20944/" to "Preprints.org",
        "10.48550/" to "arXiv",
        "10.21203/" to "Research Square",
        "10.26434/" to "ChemRxiv",
        "10.31219/" to "OSF Preprints",
        "10.22541/" to "Authorea",
        "10.2139/" to "SSRN",
        "10.32942/" to "EcoEvoRxiv",
        "10.31222/" to "MetaArXiv"
    )

    // Known preprint repository domains
    private val PREPRINT_DOMAINS = mapOf(
        "biorxiv.org" to "bioRxiv",
        "medrxiv.org" to "medRxiv",
        "arxiv.org" to "arXiv",
        "preprints.org" to "Preprints.org",
        "researchsquare.com" to "Research Square",
        "ssrn.com" to "SSRN",
        "chemrxiv.org" to "ChemRxiv",
        "osf.io/preprints" to "OSF Preprints"
    )

    // Known preprint journal markers
    private val PREPRINT_JOURNAL_KEYWORDS = listOf(
        "biorxiv", "medrxiv", "arxiv", "preprints.org", "research square", "ssrn", "chemrxiv", "peerj preprints"
    )

    /**
     * Inspects a Source or raw metadata fields to determine whether it is an unreviewed preprint.
     */
    fun detect(
        doi: String? = null,
        arxivId: String? = null,
        journal: String? = null,
        url: String? = null
    ): PreprintDetectionResult {
        // 1. Check explicit arXiv identifier
        if (!arxivId.isNullOrBlank()) {
            return PreprintDetectionResult(
                isPreprint = true,
                sourceName = "arXiv"
            )
        }

        // 2. Check DOI prefix matching
        doi?.trim()?.let { trimmedDoi ->
            for ((prefix, name) in PREPRINT_DOI_PREFIXES) {
                if (trimmedDoi.startsWith(prefix, ignoreCase = true) ||
                    trimmedDoi.contains("doi.org/$prefix", ignoreCase = true)
                ) {
                    return PreprintDetectionResult(
                        isPreprint = true,
                        sourceName = name
                    )
                }
            }
        }

        // 3. Check repository URL domain
        url?.lowercase()?.let { lowerUrl ->
            for ((domain, name) in PREPRINT_DOMAINS) {
                if (lowerUrl.contains(domain)) {
                    return PreprintDetectionResult(
                        isPreprint = true,
                        sourceName = name
                    )
                }
            }
        }

        // 4. Check journal / publication venue string
        journal?.lowercase()?.let { lowerJournal ->
            for (keyword in PREPRINT_JOURNAL_KEYWORDS) {
                if (lowerJournal.contains(keyword)) {
                    return PreprintDetectionResult(
                        isPreprint = true,
                        sourceName = keyword.replaceFirstChar { it.uppercase() }
                    )
                }
            }
        }

        return PreprintDetectionResult(isPreprint = false)
    }

    /**
     * Convenience wrapper for checking a Source model instance.
     */
    fun detect(source: Source): PreprintDetectionResult {
        // If already flagged in source model
        if (source.isPreprint) {
            return PreprintDetectionResult(
                isPreprint = true,
                sourceName = source.preprintSource ?: "Preprint Server"
            )
        }

        return detect(
            doi = source.externalIds.doi,
            arxivId = source.externalIds.arxivId,
            journal = source.journal,
            url = source.externalIds.url
        )
    }
}
