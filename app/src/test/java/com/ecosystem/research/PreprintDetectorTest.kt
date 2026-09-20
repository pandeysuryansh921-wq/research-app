package com.ecosystem.research

import com.ecosystem.research.core.analytics.PreprintDetector
import com.ecosystem.research.core.model.ExternalIdentifiers
import com.ecosystem.research.core.model.Source
import org.junit.Assert.*
import org.junit.Test

class PreprintDetectorTest {

    @Test
    fun testBioRxivDoiDetection() {
        val result = PreprintDetector.detect(doi = "10.1101/2023.01.15.524098")
        assertTrue(result.isPreprint)
        assertEquals("bioRxiv/medRxiv", result.sourceName)
        assertEquals(0.5f, result.confidencePenalty, 0.001f)
        assertFalse(result.canRecommendClinically)
        assertNotNull(result.warningMessage)
    }

    @Test
    fun testPreprintsOrgDoiDetection() {
        val result = PreprintDetector.detect(doi = "10.20944/preprints202210.0123.v1")
        assertTrue(result.isPreprint)
        assertEquals("Preprints.org", result.sourceName)
        assertEquals(0.5f, result.confidencePenalty, 0.001f)
        assertFalse(result.canRecommendClinically)
    }

    @Test
    fun testArxivIdDetection() {
        val source = Source(
            title = "Attention Is All You Need",
            externalIds = ExternalIdentifiers(arxivId = "1706.03762")
        )
        val result = PreprintDetector.detect(source)
        assertTrue(result.isPreprint)
        assertEquals("arXiv", result.sourceName)
        assertEquals(0.5f, result.confidencePenalty, 0.001f)
    }

    @Test
    fun testPeerReviewedPaperIsNotPreprint() {
        val source = Source(
            title = "Dexamethasone in Hospitalized Patients with Covid-19",
            journal = "New England Journal of Medicine",
            externalIds = ExternalIdentifiers(
                doi = "10.1056/NEJMoa2021436",
                pmid = "32678270"
            )
        )
        val result = PreprintDetector.detect(source)
        assertFalse(result.isPreprint)
        assertNull(result.sourceName)
        assertEquals(1.0f, result.confidencePenalty, 0.001f)
        assertTrue(result.canRecommendClinically)
    }

    @Test
    fun testPreprintUrlDetection() {
        val result = PreprintDetector.detect(url = "https://www.biorxiv.org/content/10.1101/2021.01.01.123456v1")
        assertTrue(result.isPreprint)
        assertEquals("bioRxiv", result.sourceName)
    }
}
