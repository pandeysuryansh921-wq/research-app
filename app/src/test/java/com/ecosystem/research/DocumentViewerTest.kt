package com.ecosystem.research

import com.ecosystem.research.core.document.parseInlineMarkdown
import com.ecosystem.research.core.model.Evidence
import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.SourceLocation
import com.ecosystem.research.ui.screens.DocumentFormat
import org.junit.Assert.*
import org.junit.Test

class DocumentViewerTest {

    @Test
    fun `test document format detection`() {
        fun detect(path: String): DocumentFormat {
            val lower = path.lowercase()
            return when {
                lower.endsWith(".pdf") || path.contains("pdf", ignoreCase = true) -> DocumentFormat.PDF
                lower.endsWith(".md") || lower.endsWith(".markdown") -> DocumentFormat.MARKDOWN
                else -> DocumentFormat.TEXT
            }
        }

        assertEquals(DocumentFormat.PDF, detect("/sdcard/Download/nature_paper_2026.pdf"))
        assertEquals(DocumentFormat.PDF, detect("content://com.android.providers.downloads/document/123_pdf"))
        assertEquals(DocumentFormat.MARKDOWN, detect("/sdcard/Notes/summary.md"))
        assertEquals(DocumentFormat.MARKDOWN, detect("README.markdown"))
        assertEquals(DocumentFormat.TEXT, detect("citations.bib"))
        assertEquals(DocumentFormat.TEXT, detect("data.json"))
        assertEquals(DocumentFormat.TEXT, detect("results.csv"))
    }

    @Test
    fun `test markdown inline parsing retains clean text`() {
        val input = "This study demonstrates **significant efficacy** with `p < 0.001` in *cohort A*."
        val annotated = parseInlineMarkdown(input)
        
        assertEquals(
            "This study demonstrates significant efficacy with p < 0.001 in cohort A.",
            annotated.text
        )
    }

    @Test
    fun `test evidence extraction preserves page coordinate`() {
        val evidence = Evidence(
            projectId = "proj-123",
            sourceId = "src-456",
            location = SourceLocation(pageNumber = 14),
            excerptText = "The proposed algorithm achieves 94.2% sensitivity on external validation.",
            userInterpretation = "Validates generalizability across multi-site benchmarks.",
            relationshipType = EvidenceRelationship.SUPPORTS
        )

        assertEquals("proj-123", evidence.projectId)
        assertEquals("src-456", evidence.sourceId)
        assertEquals(14, evidence.location.pageNumber)
        assertEquals(EvidenceRelationship.SUPPORTS, evidence.relationshipType)
        assertNotNull(evidence.excerptText)
    }
}
