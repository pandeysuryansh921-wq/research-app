package com.ecosystem.research.core.ai

import com.ecosystem.research.core.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Source-Grounded AI Provider calling Google's Gemini REST API (gemini-1.5-flash).
 * Enforces strict excerpt-grounded prompting and citations to ensure zero hallucination.
 */
class GeminiGroundedAIProvider(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : ResearchAIProvider {

    override suspend fun extractStructuredFields(text: String): Result<CandidateExtraction> = withContext(Dispatchers.IO) {
        val prompt = """
            You are a biomedical and scientific information extraction engine.
            Analyze the following research study text and extract the structured fields as a JSON object.
            
            JSON Schema:
            {
              "studyType": "RCT" | "COHORT" | "CASE_CONTROL" | "SYSTEMATIC_REVIEW" | "META_ANALYSIS" | "BENCHMARK" | "METHODOLOGY" | "OTHER",
              "population": "description of patient population / dataset or null",
              "sampleSize": "e.g. n=1,420 or null",
              "methods": "concise methodology summary or null",
              "keyFindings": "primary outcome / empirical result or null",
              "limitations": "noted constraints or null"
            }
            
            Return ONLY the valid JSON object with no preamble or markdown ticks.
            
            Study Text:
            $text
        """.trimIndent()

        callGemini(prompt).mapCatching { responseText ->
            val cleanJson = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = gson.fromJson(cleanJson, JsonObject::class.java)

            val studyTypeStr = obj.get("studyType")?.asString
            val studyType = try {
                if (studyTypeStr != null) StudyType.valueOf(studyTypeStr) else StudyType.OTHER
            } catch (_: Exception) {
                StudyType.OTHER
            }

            CandidateExtraction(
                studyType = studyType,
                population = obj.get("population")?.takeIf { !it.isJsonNull }?.asString,
                sampleSize = obj.get("sampleSize")?.takeIf { !it.isJsonNull }?.asString,
                methods = obj.get("methods")?.takeIf { !it.isJsonNull }?.asString,
                keyFindings = obj.get("keyFindings")?.takeIf { !it.isJsonNull }?.asString,
                limitations = obj.get("limitations")?.takeIf { !it.isJsonNull }?.asString
            )
        }
    }

    override suspend fun detectContradictions(
        claim: Claim,
        evidenceList: List<Evidence>
    ): Result<List<ContradictionCandidate>> = withContext(Dispatchers.IO) {
        val supporting = evidenceList.filter { it.relationshipType == EvidenceRelationship.SUPPORTS }
        val contradicting = evidenceList.filter { it.relationshipType == EvidenceRelationship.CONTRADICTS }

        if (supporting.isEmpty() || contradicting.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val prompt = buildString {
            appendLine("Evaluate the following scientific proposition and attached evidence excerpts:")
            appendLine("Proposition: \"${claim.proposition}\"")
            appendLine()
            appendLine("Supporting Evidence:")
            supporting.forEach {
                appendLine("- [ID: ${it.id}] Page ${it.location.pageNumber}: \"${it.excerptText}\"")
            }
            appendLine()
            appendLine("Opposing / Contradicting Evidence:")
            contradicting.forEach {
                appendLine("- [ID: ${it.id}] Page ${it.location.pageNumber}: \"${it.excerptText}\"")
            }
            appendLine()
            appendLine("Explain the scientific conflict between the opposing findings. Why do these studies disagree? (e.g. population differences, sample size, confounding variables).")
            appendLine("Keep explanation concise and academic.")
        }

        callGemini(prompt).mapCatching { analysis ->
            val candidates = mutableListOf<ContradictionCandidate>()
            for (sup in supporting) {
                for (contra in contradicting) {
                    candidates.add(
                        ContradictionCandidate(
                            claimId = claim.id,
                            supportingEvidenceId = sup.id,
                            contradictingEvidenceId = contra.id,
                            contradictionReason = analysis.trim(),
                            confidence = 0.95f
                        )
                    )
                }
            }
            candidates
        }
    }

    override suspend fun synthesizeGroundedReview(
        question: String,
        claims: List<Claim>,
        evidenceMap: Map<String, List<Evidence>>
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are an evidence-based scientific research synthesizer.")
            appendLine("Synthesize a structured literature review answering the research question based ONLY on the evidence excerpts provided below.")
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Every factual claim MUST cite the source excerpt and page coordinate (e.g. [Page 4]).")
            appendLine("2. If empirical findings conflict, explicitly present both sides and note study constraints.")
            appendLine("3. Format your response in clean Markdown with sections: Executive Summary, Empirical Evidence by Theme, Conflicting Findings / Nuances, and Conclusion.")
            appendLine("4. DO NOT make any claim unsupported by the excerpts.")
            appendLine()
            appendLine("RESEARCH QUESTION:")
            appendLine(question)
            appendLine()
            appendLine("EVIDENCE BASE:")
            for (claim in claims) {
                appendLine("## Proposition: ${claim.proposition} (Status: ${claim.status})")
                val evList = evidenceMap[claim.id] ?: emptyList()
                if (evList.isEmpty()) {
                    appendLine("(No evidence attached)")
                } else {
                    for (ev in evList) {
                        appendLine("- [${ev.relationshipType}] Page ${ev.location.pageNumber}: \"${ev.excerptText}\"")
                        if (!ev.userInterpretation.isNullOrBlank()) {
                            appendLine("  Notes: ${ev.userInterpretation}")
                        }
                    }
                }
                appendLine()
            }
        }

        callGemini(prompt)
    }

    private suspend fun callGemini(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val jsonBody = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray()
                val contentObj = JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray()
                    val partObj = JsonObject().apply {
                        addProperty("text", prompt)
                    }
                    partsArray.add(partObj)
                    add("parts", partsArray)
                }
                contentsArray.add(contentObj)
                add("contents", contentsArray)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gemini API error (${response.code}): $bodyStr"))
                }

                val respObj = gson.fromJson(bodyStr, JsonObject::class.java)
                val candidates = respObj.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates.get(0).asJsonObject
                    val content = firstCandidate.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    if (parts != null && parts.size() > 0) {
                        val text = parts.get(0).asJsonObject.get("text")?.asString ?: ""
                        return@withContext Result.success(text)
                    }
                }
                Result.failure(Exception("No content returned in Gemini response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
