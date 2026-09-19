package com.ecosystem.research

import com.ecosystem.research.core.document.BatchDocumentImporter
import com.ecosystem.research.core.document.DocumentImportItem
import com.ecosystem.research.core.model.ReadingStatus
import org.junit.Assert.*
import org.junit.Test

class BatchDocumentImporterTest {

    @Test
    fun testCleanTitleRemovesExtensionAndNormalizes() {
        val title1 = BatchDocumentImporter.cleanTitle("vaswani_2017_attention_is_all_you_need.pdf")
        assertEquals("Vaswani 2017 Attention Is All You Need", title1)

        val title2 = BatchDocumentImporter.cleanTitle("2305.12345v1_Direct_Preference_Optimization.md")
        assertEquals("Direct Preference Optimization", title2)

        val title3 = BatchDocumentImporter.cleanTitle("survey--of--multimodal--models.markdown")
        assertTrue(title3.contains("Survey") && title3.contains("Multimodal Models"))
    }

    @Test
    fun testExtractYearDetectsPublicationYear() {
        assertEquals(2017, BatchDocumentImporter.extractYear("Vaswani_2017_Attention.pdf"))
        assertEquals(2023, BatchDocumentImporter.extractYear("Rafailov_DPO_2023.md"))
        assertEquals(1998, BatchDocumentImporter.extractYear("LeCun_1998_Gradient_Based_Learning.pdf"))
        assertNull(BatchDocumentImporter.extractYear("paper_without_year.pdf"))
    }

    @Test
    fun testCreateSourcesFromBatchGeneratesValidSources() {
        val items = listOf(
            DocumentImportItem(
                uriString = "content://media/external/files/123",
                fileName = "Attention_Is_All_You_Need_2017.pdf",
                mimeType = "application/pdf"
            ),
            DocumentImportItem(
                uriString = "file:///storage/emulated/0/Documents/notes.md",
                fileName = "notes_on_transformers.md",
                mimeType = "text/markdown"
            )
        )

        val sources = BatchDocumentImporter.createSourcesFromBatch("proj-123", items)

        assertEquals(2, sources.size)

        val s1 = sources[0]
        assertEquals("proj-123", s1.projectId)
        assertEquals("Attention Is All You Need 2017", s1.title)
        assertEquals(2017, s1.year)
        assertEquals("content://media/external/files/123", s1.localPdfPath)
        assertEquals(ReadingStatus.TO_SCREEN, s1.readingStatus)

        val s2 = sources[1]
        assertEquals("proj-123", s2.projectId)
        assertEquals("Notes On Transformers", s2.title)
        assertEquals("file:///storage/emulated/0/Documents/notes.md", s2.localPdfPath)
    }
}
