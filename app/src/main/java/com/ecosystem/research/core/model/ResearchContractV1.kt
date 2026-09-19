package com.ecosystem.research.core.model

import java.net.URI

sealed class DeepLinkTarget {
    data class Project(val projectId: String) : DeepLinkTarget()
    data class Source(val sourceId: String, val pageNumber: Int? = null) : DeepLinkTarget()
    data class Claim(val claimId: String) : DeepLinkTarget()
    data class Evidence(val evidenceId: String) : DeepLinkTarget()
    data class Unknown(val raw: String) : DeepLinkTarget()
}

object ResearchContractV1 {
    const val SCHEME = "research"
    const val HOST_PROJECT = "project"
    const val HOST_SOURCE = "source"
    const val HOST_CLAIM = "claim"
    const val HOST_EVIDENCE = "evidence"

    fun buildProjectUri(projectId: String): String = "$SCHEME://$HOST_PROJECT/$projectId"

    fun buildSourceUri(sourceId: String, pageNumber: Int? = null): String {
        return if (pageNumber != null) {
            "$SCHEME://$HOST_SOURCE/$sourceId?page=$pageNumber"
        } else {
            "$SCHEME://$HOST_SOURCE/$sourceId"
        }
    }

    fun buildClaimUri(claimId: String): String = "$SCHEME://$HOST_CLAIM/$claimId"

    fun buildEvidenceUri(evidenceId: String): String = "$SCHEME://$HOST_EVIDENCE/$evidenceId"

    fun parseUri(uriString: String): DeepLinkTarget {
        return try {
            val uri = URI(uriString)
            if (uri.scheme != SCHEME) return DeepLinkTarget.Unknown(uriString)

            val host = uri.host ?: return DeepLinkTarget.Unknown(uriString)
            val path = uri.path?.trimStart('/') ?: return DeepLinkTarget.Unknown(uriString)
            val id = path.split('/').firstOrNull { it.isNotBlank() } ?: return DeepLinkTarget.Unknown(uriString)

            val queryParams = uri.query?.split('&')?.associate {
                val parts = it.split('=', limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            } ?: emptyMap()

            when (host) {
                HOST_PROJECT -> DeepLinkTarget.Project(id)
                HOST_SOURCE -> {
                    val page = queryParams["page"]?.toIntOrNull()
                    DeepLinkTarget.Source(id, page)
                }
                HOST_CLAIM -> DeepLinkTarget.Claim(id)
                HOST_EVIDENCE -> DeepLinkTarget.Evidence(id)
                else -> DeepLinkTarget.Unknown(uriString)
            }
        } catch (e: Exception) {
            DeepLinkTarget.Unknown(uriString)
        }
    }
}
