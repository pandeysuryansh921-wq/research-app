package com.ecosystem.research.core.document

import com.ecosystem.research.core.model.*

data class DocumentImportItem(
    val uriString: String,
    val fileName: String,
    val mimeType: String? = null
)

object BatchDocumentImporter {

    private val YEAR_REGEX = Regex("""(?<!\d)(19[5-9]\d|20[0-4]\d)(?!\d)""")
    private val EXTENSION_REGEX = Regex("""\.(pdf|md|markdown|txt|json)$""", RegexOption.IGNORE_CASE)
    private val PREFIX_HASH_REGEX = Regex("""^[a-f0-9]{8,}_|^[0-9]{4,}\.[0-9]{4,}(v[0-9]+)?_?""", RegexOption.IGNORE_CASE)

    fun cleanTitle(fileName: String): String {
        // 1. Strip extension
        var name = fileName.replace(EXTENSION_REGEX, "")

        // 2. Strip common prefix hashes or arxiv identifiers
        name = name.replace(PREFIX_HASH_REGEX, "")

        // 3. Replace underscores, dashes, and extra spaces
        name = name.replace(Regex("""[_-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (name.isBlank()) return "Imported Document"

        // 4. Clean up capitalization if entire string is uppercase or lowercase
        if (name == name.uppercase() || name == name.lowercase()) {
            name = name.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        return name
    }

    fun extractYear(fileNameOrText: String): Int? {
        val match = YEAR_REGEX.find(fileNameOrText)
        return match?.value?.toIntOrNull()
    }

    fun createSourcesFromBatch(
        projectId: String,
        items: List<DocumentImportItem>
    ): List<Source> {
        val now = System.currentTimeMillis()
        return items.mapIndexed { index, item ->
            val title = cleanTitle(item.fileName)
            val year = extractYear(item.fileName)

            Source(
                projectId = projectId,
                title = title,
                authors = emptyList(),
                journal = null,
                year = year,
                externalIds = ExternalIdentifiers(),
                abstractText = null,
                localPdfPath = item.uriString,
                readingStatus = ReadingStatus.TO_SCREEN,
                studyType = StudyType.OTHER,
                priority = 2,
                rationale = "Batch imported from local file storage (${item.fileName})",
                provenance = ProvenanceRecord(
                    originType = OriginType.SOURCE_METADATA,
                    verificationState = VerificationState.UNVERIFIED,
                    attribution = Attribution(createdBy = "batch_importer", timestamp = now + index)
                ),
                createdAt = now + index
            )
        }
    }
}
