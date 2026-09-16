package com.ecosystem.research.core.model

import android.net.Uri

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
            val uri = Uri.parse(uriString)
            if (uri.scheme != SCHEME) return DeepLinkTarget.Unknown(uriString)

            val host = uri.host ?: return DeepLinkTarget.Unknown(uriString)
            val pathSegments = uri.pathSegments

            when (host) {
                HOST_PROJECT -> {
                    val id = pathSegments.firstOrNull() ?: uri.lastPathSegment
                    if (id != null) DeepLinkTarget.Project(id) else DeepLinkTarget.Unknown(uriString)
                }
                HOST_SOURCE -> {
                    val id = pathSegments.firstOrNull() ?: uri.lastPathSegment
                    val page = uri.getQueryParameter("page")?.toIntOrNull()
                    if (id != null) DeepLinkTarget.Source(id, page) else DeepLinkTarget.Unknown(uriString)
                }
                HOST_CLAIM -> {
                    val id = pathSegments.firstOrNull() ?: uri.lastPathSegment
                    if (id != null) DeepLinkTarget.Claim(id) else DeepLinkTarget.Unknown(uriString)
                }
                HOST_EVIDENCE -> {
                    val id = pathSegments.firstOrNull() ?: uri.lastPathSegment
                    if (id != null) DeepLinkTarget.Evidence(id) else DeepLinkTarget.Unknown(uriString)
                }
                else -> DeepLinkTarget.Unknown(uriString)
            }
        } catch (e: Exception) {
            DeepLinkTarget.Unknown(uriString)
        }
    }
}
