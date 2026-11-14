package com.akjarvis.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akjarvis.app.ai.AiProvider
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.LanguageMode
import com.akjarvis.app.domain.model.PersonalityStyle
import com.akjarvis.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ak_jarvis_settings")

/** Immutable snapshot of every user-configurable option. */
data class AppSettings(
    val aiMode: AiMode = AiMode.CLOUD,
    val provider: AiProvider = AiProvider.Z_AI,
    val apiKey: String = "",
    val apiBaseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: LanguageMode = LanguageMode.ENGLISH,
    val personalityStyle: PersonalityStyle = PersonalityStyle.FRIENDLY,
    val assistantName: String = "Jarvis",
    val customInstructions: String = "",
    val speakReplies: Boolean = true,
    val handsFree: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val wakePhrase: String = "jarvis",
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.z.ai/api/paas/v4/"
        const val DEFAULT_MODEL = "glm-4.5-flash"
    }
}

/**
 * DataStore-backed settings store. The API key lives only in this app's
 * private storage and is excluded from Android backups (see backup_rules.xml).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val AI_MODE = stringPreferencesKey("ai_mode")
        val PROVIDER = stringPreferencesKey("provider")
        val API_KEY = stringPreferencesKey("api_key")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val MODEL = stringPreferencesKey("model")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val PERSONALITY = stringPreferencesKey("personality_style")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            aiMode = p[Keys.AI_MODE]?.let { runCatching { AiMode.valueOf(it) }.getOrNull() } ?: AiMode.CLOUD,
        provider = p[Keys.PROVIDER]?.let { name ->
            runCatching { AiProvider.valueOf(name) }.getOrNull()
        } ?: AiProvider.Z_AI,
            apiKey = p[Keys.API_KEY] ?: "",
            apiBaseUrl = (p[Keys.API_BASE_URL]?.takeIf { it.isNotBlank() }) ?: AppSettings.DEFAULT_BASE_URL,
            model = (p[Keys.MODEL]?.takeIf { it.isNotBlank() }) ?: AppSettings.DEFAULT_MODEL,
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = p[Keys.LANGUAGE]?.let { runCatching { LanguageMode.valueOf(it) }.getOrNull() } ?: LanguageMode.ENGLISH,
            personalityStyle = p[Keys.PERSONALITY]?.let { runCatching { PersonalityStyle.valueOf(it) }.getOrNull() }
                ?: PersonalityStyle.FRIENDLY,
            assistantName = p[Keys.ASSISTANT_NAME]?.takeIf { it.isNotBlank() } ?: "Jarvis",
            customInstructions = p[Keys.CUSTOM_INSTRUCTIONS] ?: "",
            speakReplies = p[Keys.SPEAK_REPLIES] ?: true,
            handsFree = p[Keys.HANDS_FREE] ?: true,
            wakeWordEnabled = p[Keys.WAKE_WORD_ENABLED] ?: false,
            wakePhrase = p[Keys.WAKE_PHRASE]?.takeIf { it.isNotBlank() } ?: "jarvis",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setAiMode(mode: AiMode) = edit { it[Keys.AI_MODE] = mode.name }

    /** Selects a provider and pre-fills its default base URL and model. */
    suspend fun setProvider(provider: AiProvider) = edit {
        it[Keys.PROVIDER] = provider.name
        if (provider != AiProvider.CUSTOM) {
            it[Keys.API_BASE_URL] = provider.defaultBaseUrl
            it[Keys.MODEL] = provider.defaultModel
        }
    }
    suspend fun setApiKey(key: String) = edit { it[Keys.API_KEY] = key.trim() }
    suspend fun setApiBaseUrl(url: String) = edit { it[Keys.API_BASE_URL] = url.trim() }
    suspend fun setModel(model: String) = edit { it[Keys.MODEL] = model.trim() }
    suspend fun setTheme(theme: ThemeMode) = edit { it[Keys.THEME] = theme.name }
    suspend fun setLanguage(language: LanguageMode) = edit { it[Keys.LANGUAGE] = language.name }
    suspend fun setPersonalityStyle(style: PersonalityStyle) = edit { it[Keys.PERSONALITY] = style.name }
    suspend fun setAssistantName(name: String) = edit { it[Keys.ASSISTANT_NAME] = name.trim() }
    suspend fun setCustomInstructions(text: String) = edit { it[Keys.CUSTOM_INSTRUCTIONS] = text }
    suspend fun setSpeakReplies(enabled: Boolean) = edit { it[Keys.SPEAK_REPLIES] = enabled }
    suspend fun setHandsFree(enabled: Boolean) = edit { it[Keys.HANDS_FREE] = enabled }
    suspend fun setWakeWordEnabled(enabled: Boolean) = edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    suspend fun setWakePhrase(phrase: String) = edit { it[Keys.WAKE_PHRASE] = phrase.trim().lowercase() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
