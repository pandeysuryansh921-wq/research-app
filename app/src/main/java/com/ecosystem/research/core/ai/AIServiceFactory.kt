package com.ecosystem.research.core.ai

import android.content.Context
import com.ecosystem.research.core.security.AIProviderType
import com.ecosystem.research.core.security.SecureKeyManager

/**
 * Factory providing the active ResearchAIProvider instance based on saved credentials
 * and user preferences, automatically falling back to offline heuristics when appropriate.
 */
object AIServiceFactory {

    fun getProvider(context: Context): ResearchAIProvider {
        val keyManager = SecureKeyManager(context)
        val activeType = keyManager.getActiveProvider()

        return when (activeType) {
            AIProviderType.GEMINI -> {
                val key = keyManager.getGeminiApiKey()
                if (!key.isNullOrBlank()) {
                    GeminiGroundedAIProvider(apiKey = key)
                } else {
                    HeuristicOfflineAIProvider()
                }
            }
            AIProviderType.OFFLINE_HEURISTIC -> HeuristicOfflineAIProvider()
            else -> {
                // Default to Gemini if key exists, otherwise offline
                val geminiKey = keyManager.getGeminiApiKey()
                if (!geminiKey.isNullOrBlank()) {
                    GeminiGroundedAIProvider(apiKey = geminiKey)
                } else {
                    HeuristicOfflineAIProvider()
                }
            }
        }
    }
}
