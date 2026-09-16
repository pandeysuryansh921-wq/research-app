package com.ecosystem.research.core.ecosystem

import android.content.Context
import android.content.Intent
import android.net.Uri

object EcosystemBridge {
    const val ACTION_CAPTURE_INBOX = "com.ecosystem.research.ACTION_CAPTURE_INBOX"
    const val ACTION_VOICE_IDEA = "com.ecosystem.research.ACTION_VOICE_IDEA"
    const val ACTION_CREATE_TASK = "com.ecosystem.productivity.ACTION_CREATE_TASK"

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

    fun dispatchTaskToProductivity(context: Context, title: String, notes: String, deepLink: String): Boolean {
        return try {
            val intent = createProductivityTaskIntent(title, notes, deepLink)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
