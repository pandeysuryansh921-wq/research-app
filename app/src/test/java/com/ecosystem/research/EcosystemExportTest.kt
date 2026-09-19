package com.ecosystem.research

import com.ecosystem.research.core.ecosystem.CitationExportEngine
import com.ecosystem.research.core.ecosystem.EcosystemBridge
import com.ecosystem.research.core.ecosystem.ManuscriptExportEngine
import com.ecosystem.research.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EcosystemExportTest {

    @Test
    fun testManuscriptExportWithFootnotesAndYaml() {
        val project = ResearchProject(
            id = "proj-101",
            title = "Transformer Attention Efficiency"
        )

        val claim = Claim(
            id = "claim-1",
            projectId = "proj-101",
            proposition = "FlashAttention decreases memory footprint by tiling matrix operations.",
            status = ClaimStatus.SUPPORTED
        )

        val source = Source(
            id = "src-1",
            projectId = "proj-101",
            title = "FlashAttention: Fast and Memory-Efficient Exact Attention",
            authors = listOf("Tri Dao", "Daniel Y. Fu"),
            year = 2022
        )

        val evidence = Evidence(
            id = "ev-1",
            projectId = "proj-101",
            sourceId = "src-1",
            excerptText = "We propose FlashAttention, an IO-aware exact attention algorithm that uses tiling to reduce memory reads/writes.",
            relationshipType = EvidenceRelationship.SUPPORTS,
            location = SourceLocation(pageNumber = 3)
        )

        val manuscript = ManuscriptExportEngine.generateManuscript(
            project = project,
            claims = listOf(claim),
            claimEvidenceMap = mapOf(claim.id to listOf(Pair(evidence, source))),
            sources = listOf(source),
            synthesisReport = "FlashAttention introduces exact tiling...",
            options = ManuscriptExportEngine.ExportOptions(
                includeYamlFrontmatter = true,
                includeLiteratureReview = true,
                includeEvidenceMatrix = true,
                includeContradictionsSection = true,
                includeBibTeXReferences = true,
                includeFootnotes = true
            )
        )

        // Verify YAML Frontmatter
        assertTrue(manuscript.contains("title: \"Transformer Attention Efficiency\""))
        assertTrue(manuscript.contains("target_editor: \"Likhoji\""))
        assertTrue(manuscript.contains("claims_count: 1"))

        // Verify Footnotes
        assertTrue(manuscript.contains("[^1]: **Tri Dao et al. (2022)**"))
        assertTrue(manuscript.contains("Location: Page 3"))

        // Verify BibTeX Reference List
        assertTrue(manuscript.contains("```bibtex"))
        assertTrue(manuscript.contains("@article{dao2022flashatt"))
    }

    @Test
    fun testBibTeXGeneration() {
        val source = Source(
            id = "src-2",
            projectId = "proj-1",
            title = "Attention Is All You Need",
            authors = listOf("Ashish Vaswani", "Noam Shazeer"),
            year = 2017,
            externalIds = ExternalIdentifiers(doi = "10.48550/arXiv.1706.03762")
        )

        val bibtex = CitationExportEngine.generateBibTeX(source)
        assertTrue(bibtex.startsWith("@article{vaswani2017attentio"))
        assertTrue(bibtex.contains("author    = {Ashish Vaswani and Noam Shazeer}"))
        assertTrue(bibtex.contains("year      = {2017}"))
        assertTrue(bibtex.contains("doi       = {10.48550/arXiv.1706.03762}"))
    }

    @Test
    fun testCsvMatrixExport() {
        val source = Source(
            id = "src-3",
            projectId = "proj-1",
            title = "Deep Residual Learning, Image Recognition",
            authors = listOf("Kaiming He"),
            year = 2016
        )

        val csv = CitationExportEngine.exportMatrixToCsv(
            columnTitles = listOf("Methodology", "Findings"),
            sources = listOf(source),
            cellValues = mapOf(Pair(source.id, "col-1") to "Residual connections bypass layers.")
        )

        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("Source Title,Year,Authors,Methodology,Findings"))
        assertTrue(lines[1].contains("\"Deep Residual Learning, Image Recognition\""))
        assertTrue(lines[1].contains("2016"))
    }

    @Test
    fun testBolojiAudioTranscriptParsing() {
        val transcript = """
            We discussed the methodology limitations in randomized controlled trials.
            The sample size of 50 patients is insufficient to detect subtle effects.
            #methodology #sample_size #limitation
        """.trimIndent()

        val parsed = EcosystemBridge.parseBolojiAudioTranscript(transcript, "meeting_recording.mp3")

        assertEquals("meeting_recording", parsed.title)
        assertTrue(parsed.detectedTags.contains("methodology"))
        assertTrue(parsed.detectedTags.contains("sample_size"))
        assertTrue(parsed.detectedTags.contains("limitation"))
        assertEquals(1, parsed.estimatedMinutes)
    }

    @Test
    fun testDeepLinkContract() {
        val uri = ResearchContractV1.buildSourceUri("src-99", 12)
        assertEquals("research://source/src-99?page=12", uri)

        val parsed = ResearchContractV1.parseUri(uri)
        assertTrue(parsed is DeepLinkTarget.Source)
        val sourceTarget = parsed as DeepLinkTarget.Source
        assertEquals("src-99", sourceTarget.sourceId)
        assertEquals(12, sourceTarget.pageNumber)
    }
}
