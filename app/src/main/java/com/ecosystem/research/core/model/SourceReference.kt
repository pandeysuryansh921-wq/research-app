package com.ecosystem.research.core.model

data class ExternalIdentifiers(
    val doi: String? = null,
    val pmid: String? = null,
    val arxivId: String? = null,
    val url: String? = null
) {
    fun canonicalKey(): String? {
        return doi?.lowercase()?.trim()
            ?: pmid?.trim()
            ?: arxivId?.trim()
            ?: url?.trim()
    }
}

data class SourceReference(
    val sourceId: String,
    val title: String,
    val externalIds: ExternalIdentifiers = ExternalIdentifiers(),
    val location: SourceLocation
)
