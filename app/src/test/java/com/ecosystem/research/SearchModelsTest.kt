package com.ecosystem.research

import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.VerificationState
import com.ecosystem.research.core.search.SearchEntityType
import com.ecosystem.research.core.search.SearchFilter
import com.ecosystem.research.core.search.SearchResult
import org.junit.Assert.*
import org.junit.Test

class SearchModelsTest {

    @Test
    fun testSearchResultStructureAndRanking() {
        val result1 = SearchResult(
            id = "ev-1",
            entityType = SearchEntityType.EVIDENCE,
            title = "Attention Is All You Need",
            snippet = "Transformer architecture uses multi-head attention.",
            matchedField = "Excerpt",
            pageNumber = 3,
            verificationState = VerificationState.CONFIRMED,
            relationshipType = EvidenceRelationship.SUPPORTS,
            timestamp = 1000L
        )

        val result2 = SearchResult(
            id = "src-1",
            entityType = SearchEntityType.SOURCE,
            title = "Transformer Scalability",
            snippet = "Survey on transformer variants.",
            matchedField = "Title",
            timestamp = 2000L
        )

        assertEquals("ev-1", result1.id)
        assertEquals(SearchEntityType.EVIDENCE, result1.entityType)
        assertEquals(3, result1.pageNumber)

        val filter = SearchFilter(
            entityType = SearchEntityType.EVIDENCE,
            projectId = "p1"
        )
        assertEquals(SearchEntityType.EVIDENCE, filter.entityType)
        assertEquals("p1", filter.projectId)

        // Verify sorting by title match then timestamp
        val query = "Transformer"
        val sorted = listOf(result1, result2).sortedWith(
            compareByDescending<SearchResult> { it.title.contains(query, ignoreCase = true) }
                .thenByDescending { it.timestamp }
        )
        assertEquals("src-1", sorted.first().id) // higher timestamp
    }
}
