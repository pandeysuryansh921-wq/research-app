package com.ecosystem.research.core.ecosystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ecosystem.research.core.model.ResearchContractV1

/**
 * Inter-App Ecosystem Bridge coordinating with:
 * - Likhoji (Deep Writing & Publishing)
 * - Stylus Notes (Handwriting & Sketching Canvas - strictly separate)
 * - Boloji (Voice Memos & Audio Transcriptions)
 * - Productivity (Task Execution & Schedule)
 * - DegreeTrack (Academic Curriculum & Thesis Tracker)
 */
object EcosystemBridge {
    const val ACTION_CAPTURE_INBOX = "com.ecosystem.research.ACTION_CAPTURE_INBOX"
    const val ACTION_VOICE_IDEA = "com.ecosystem.research.ACTION_VOICE_IDEA"
    const val ACTION_CREATE_TASK = "com.ecosystem.productivity.ACTION_CREATE_TASK"
    const val ACTION_OPEN_LIKHOJI = "com.ecosystem.likhoji.ACTION_IMPORT_MANUSCRIPT"

    data class ParsedVoiceMemo(
        val title: String,
        val transcript: String,
        val detectedTags: List<String>,
        val estimatedMinutes: Int
    )

    /**
     * Creates an Intent to open a document in Stylus Notes for freehand sketching.
     * Research App contains NO canvas drawing tools; drawing is handled entirely in Stylus Notes.
     */
    fun createOpenInStylusNotesIntent(sourceId: String, pageNumber: Int? = null): Intent {
        val uriBuilder = Uri.parse("stylus://open").buildUpon()
            .appendQueryParameter("sourceId", sourceId)
        if (pageNumber != null) {
            uriBuilder.appendQueryParameter("page", pageNumber.toString())
        }
        return Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to send a formatted research manuscript directly to Likhoji.
     */
    fun createLikhojiExportIntent(manuscriptMarkdown: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, manuscriptMarkdown)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates a task creation intent for the Productivity app.
     */
    fun createProductivityTaskIntent(
        title: String,
        notes: String,
        deepLink: String
    ): Intent {
        return Intent(ACTION_CREATE_TASK).apply {
            putExtra("task_title", title)
            putExtra("task_notes", notes)
            putExtra("deep_link", deepLink)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Pushes a literature reading queue item into the Productivity app with a deep link back
     * to the exact source and page in Research App.
     */
    fun createReadingTaskInProductivity(
        context: Context,
        sourceTitle: String,
        sourceId: String,
        pageNumber: Int? = null
    ): Boolean {
        val deepLink = ResearchContractV1.buildSourceUri(sourceId, pageNumber)
        val taskTitle = "Read Paper: $sourceTitle"
        val notes = "Academic reading task from Research App.${if (pageNumber != null) " Resume on Page $pageNumber." else ""}"
        return dispatchTaskToProductivity(context, taskTitle, notes, deepLink)
    }

    fun dispatchTaskToProductivity(context: Context, title: String, notes: String, deepLink: String): Boolean {
        return try {
            val intent = createProductivityTaskIntent(title, notes, deepLink)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parses an incoming voice transcript from Boloji, extracting timestamps, tags,
     * and paragraphs for ingestion as a primary research Source.
     */
    fun parseBolojiAudioTranscript(transcriptText: String, audioFileName: String? = null): ParsedVoiceMemo {
        val lines = transcriptText.trim().lines().filter { it.isNotBlank() }
        val title = audioFileName?.removeSuffix(".mp3")?.removeSuffix(".m4a")
            ?: lines.firstOrNull()?.take(50)
            ?: "Voice Memo Source"

        // Extract hashtag words (#methodology, #limitation, etc.)
        val hashtagRegex = Regex("#([a-zA-Z0-9_-]+)")
        val tags = hashtagRegex.findAll(transcriptText).map { it.groupValues[1] }.distinct().toList()

        val wordCount = transcriptText.split(Regex("\\s+")).count { it.isNotBlank() }
        val estimatedMinutes = maxOf(1, wordCount / 130) // ~130 words per minute speaking rate

        return ParsedVoiceMemo(
            title = title,
            transcript = transcriptText,
            detectedTags = tags,
            estimatedMinutes = estimatedMinutes
        )
    }
}
