package com.ecosystem.research.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AIProviderType {
    GEMINI,
    CLAUDE,
    OPENAI,
    OFFLINE_HEURISTIC
}

/**
 * Manages user-provided API keys using Android Keystore AES-256-GCM hardware encryption.
 * Safely falls back to private shared preferences if Keystore is unavailable (e.g. in test runners).
 */
class SecureKeyManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "research_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for headless test environments
        context.getSharedPreferences("research_secure_keys_fallback", Context.MODE_PRIVATE)
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun getGeminiApiKey(): String? {
        return prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
    }

    fun setClaudeApiKey(key: String) {
        prefs.edit().putString(KEY_CLAUDE, key.trim()).apply()
    }

    fun getClaudeApiKey(): String? {
        return prefs.getString(KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }
    }

    fun setOpenAiApiKey(key: String) {
        prefs.edit().putString(KEY_OPENAI, key.trim()).apply()
    }

    fun getOpenAiApiKey(): String? {
        return prefs.getString(KEY_OPENAI, null)?.takeIf { it.isNotBlank() }
    }

    fun setActiveProvider(provider: AIProviderType) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun getActiveProvider(): AIProviderType {
        val name = prefs.getString(KEY_ACTIVE_PROVIDER, AIProviderType.GEMINI.name)
        return try {
            AIProviderType.valueOf(name ?: AIProviderType.GEMINI.name)
        } catch (_: Exception) {
            AIProviderType.GEMINI
        }
    }

    fun clearAllKeys() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_GEMINI = "key_gemini"
        private const val KEY_CLAUDE = "key_claude"
        private const val KEY_OPENAI = "key_openai"
        private const val KEY_ACTIVE_PROVIDER = "key_active_provider"
    }
}
