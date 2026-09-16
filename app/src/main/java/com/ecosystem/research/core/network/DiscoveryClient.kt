package com.ecosystem.research.core.network

import com.ecosystem.research.core.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DiscoveryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun searchPubMed(query: String, maxResults: Int = 10): Result<List<Source>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term=$encodedQuery&retmode=json&retmax=$maxResults"

            val searchReq = Request.Builder().url(searchUrl).build()
            val searchResp = client.newCall(searchReq).execute()
            if (!searchResp.isSuccessful) {
                return@withContext Result.failure(Exception("PubMed search failed HTTP ${searchResp.code}"))
            }

            val searchJson = JsonParser.parseString(searchResp.body?.string() ?: "").asJsonObject
            val idListJson = searchJson.getAsJsonObject("esearchresult")?.getAsJsonArray("idlist")
            val pmids = idListJson?.map { it.asString } ?: emptyList()

            if (pmids.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Fetch summaries
            val pmidJoin = pmids.joinToString(",")
            val summaryUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=$pmidJoin&retmode=json"
            val summaryReq = Request.Builder().url(summaryUrl).build()
            val summaryResp = client.newCall(summaryReq).execute()
            if (!summaryResp.isSuccessful) {
                return@withContext Result.failure(Exception("PubMed summary failed HTTP ${summaryResp.code}"))
            }

            val summaryJson = JsonParser.parseString(summaryResp.body?.string() ?: "").asJsonObject
            val resultObj = summaryJson.getAsJsonObject("result") ?: return@withContext Result.success(emptyList())

            val sources = mutableListOf<Source>()
            for (pmid in pmids) {
                val doc = resultObj.getAsJsonObject(pmid) ?: continue
                val title = doc.get("title")?.asString?.trimEnd('.') ?: "Untitled"
                val journal = doc.get("source")?.asString
                val pubdate = doc.get("pubdate")?.asString
                val year = pubdate?.split(" ")?.firstOrNull()?.toIntOrNull()

                val authorsList = mutableListOf<String>()
                val authorsArray = doc.getAsJsonArray("authors")
                authorsArray?.forEach { a ->
                    val name = a.asJsonObject.get("name")?.asString
                    if (!name.isNullOrBlank()) authorsList.add(name)
                }

                var doi: String? = null
                val articleIds = doc.getAsJsonArray("articleids")
                articleIds?.forEach { idObj ->
                    val idType = idObj.asJsonObject.get("idtype")?.asString
                    val value = idObj.asJsonObject.get("value")?.asString
                    if (idType == "doi") doi = value
                }

                sources.add(
                    Source(
                        title = title,
                        authors = authorsList,
                        journal = journal,
                        year = year,
                        externalIds = ExternalIdentifiers(
                            doi = doi,
                            pmid = pmid,
                            url = if (doi != null) "https://doi.org/$doi" else "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
                        ),
                        readingStatus = ReadingStatus.TO_SCREEN,
                        provenance = ProvenanceRecord(
                            originType = OriginType.SOURCE_METADATA,
                            verificationState = VerificationState.CONFIRMED,
                            attribution = Attribution(createdBy = "pubmed_api")
                        )
                    )
                )
            }
            Result.success(sources)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchArxiv(query: String, maxResults: Int = 10): Result<List<Source>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://export.arxiv.org/api/query?search_query=all:$encodedQuery&start=0&max_results=$maxResults"

            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("arXiv search failed HTTP ${resp.code}"))
            }

            val xml = resp.body?.string() ?: ""
            val sources = parseArxivAtom(xml)
            Result.success(sources)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseArxivAtom(xml: String): List<Source> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val results = mutableListOf<Source>()
        var eventType = parser.eventType
        var inEntry = false
        var curTitle: String? = null
        var curSummary: String? = null
        var curId: String? = null
        var curPublished: String? = null
        val curAuthors = mutableListOf<String>()
        var inAuthor = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "entry") {
                        inEntry = true
                        curTitle = null
                        curSummary = null
                        curId = null
                        curPublished = null
                        curAuthors.clear()
                    } else if (inEntry) {
                        if (name == "title") {
                            curTitle = parser.nextText().replace("\n", " ").trim()
                        } else if (name == "summary") {
                            curSummary = parser.nextText().replace("\n", " ").trim()
                        } else if (name == "id") {
                            curId = parser.nextText().trim()
                        } else if (name == "published") {
                            curPublished = parser.nextText().trim()
                        } else if (name == "author") {
                            inAuthor = true
                        } else if (inAuthor && name == "name") {
                            curAuthors.add(parser.nextText().trim())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "author") {
                        inAuthor = false
                    } else if (name == "entry") {
                        inEntry = false
                        val arxivId = curId?.substringAfterLast("/abs/") ?: curId
                        val year = curPublished?.take(4)?.toIntOrNull()

                        if (!curTitle.isNullOrBlank()) {
                            results.add(
                                Source(
                                    title = curTitle ?: "Untitled",
                                    authors = curAuthors.toList(),
                                    journal = "arXiv preprint",
                                    year = year,
                                    externalIds = ExternalIdentifiers(
                                        arxivId = arxivId,
                                        url = curId
                                    ),
                                    abstractText = curSummary,
                                    readingStatus = ReadingStatus.TO_SCREEN,
                                    provenance = ProvenanceRecord(
                                        originType = OriginType.SOURCE_METADATA,
                                        verificationState = VerificationState.CONFIRMED,
                                        attribution = Attribution(createdBy = "arxiv_api")
                                    )
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }
}
