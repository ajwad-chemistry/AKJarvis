package com.akjarvis.app.ai

import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.domain.model.ChatTurn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ChatApi {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest,
    ): ChatResponse
}

/**
 * Cloud AI engine for any OpenAI-compatible chat completion API.
 * Defaults are tuned for Z.ai (GLM): base URL https://api.z.ai/api/paas/v4/
 * and the free-tier model glm-4.5-flash, but everything is editable in Settings.
 * The API key is read from DataStore at call time and sent only as a request header.
 */
class CloudEngine(
    private val settingsProvider: suspend () -> AppSettings,
) : AiEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedApi: Pair<String, ChatApi>? = null

    private suspend fun apiFor(baseUrl: String): ChatApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        cachedApi?.let { (url, api) -> if (url == normalized) return api }
        val api = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChatApi::class.java)
        cachedApi = normalized to api
        return api
    }

    override suspend fun respond(history: List<ChatTurn>, userText: String): String {
        val settings = settingsProvider()
        require(settings.apiKey.isNotBlank()) { "No API key configured" }

        val messages = buildList {
            add(ApiMessage(role = "system", content = SystemPromptBuilder.build(settings)))
            history.forEach { add(ApiMessage(role = it.role, content = it.content)) }
            add(ApiMessage(role = "user", content = userText))
        }
        val request = ChatRequest(model = settings.model, messages = messages)

        val response = try {
            apiFor(settings.apiBaseUrl).chat("Bearer ${settings.apiKey}", request)
        } catch (e: HttpException) {
            val bodyText = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val apiMessage = runCatching {
                json.decodeFromString(ApiErrorResponse.serializer(), bodyText).error?.message
            }.getOrNull()
            throw CloudException(
                apiMessage ?: "Cloud API error (HTTP ${e.code()})",
                e.code(),
            )
        } catch (e: Exception) {
            if (e is CloudException) throw e
            throw CloudException("Cannot reach the cloud AI: ${e.message ?: e.javaClass.simpleName}", null)
        }

        response.error?.message?.let { throw CloudException(it, null) }
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw CloudException("The cloud AI returned an empty response", null)
        return stripThinking(content).ifBlank {
            throw CloudException("The cloud AI returned an empty response", null)
        }
    }

    /** Some GLM thinking modes wrap reasoning in <think> tags; show only the final answer. */
    private fun stripThinking(text: String): String =
        text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
}

class CloudException(message: String, val httpCode: Int?) : Exception(message)

object SystemPromptBuilder {
    /**
     * Builds the system prompt from the user's personality settings.
     * Keeps replies compact because they are also spoken aloud.
     */
    fun build(settings: AppSettings): String {
        val style = when (settings.personalityStyle) {
            com.akjarvis.app.domain.model.PersonalityStyle.PROFESSIONAL ->
                "Be precise, formal and efficient, like a professional assistant."
            com.akjarvis.app.domain.model.PersonalityStyle.FRIENDLY ->
                "Be warm, friendly and encouraging."
            com.akjarvis.app.domain.model.PersonalityStyle.FUNNY ->
                "Be playful and lightly humorous, but still helpful."
        }
        val language = when (settings.language) {
            com.akjarvis.app.domain.model.LanguageMode.ENGLISH ->
                "Reply in English."
            com.akjarvis.app.domain.model.LanguageMode.URDU ->
                "Reply in Urdu (Urdu script)."
            com.akjarvis.app.domain.model.LanguageMode.ROMAN_URDU ->
                "Reply in Roman Urdu (Urdu written in Latin letters, e.g. 'Main theek hoon, shukriya!')."
        }
        return buildString {
            append("You are ${settings.assistantName}, a personal AI assistant inside an Android app called AK Jarvis. ")
            append(style).append(' ')
            append(language).append(' ')
            append("Keep replies short and conversational (usually under 120 words) because they may be read aloud. ")
            append("You cannot send messages, make calls or perform purchases; if asked, explain the user must confirm in the app. ")
            append("Never ask for passwords, OTPs or other secrets.")
            if (settings.customInstructions.isNotBlank()) {
                append(" Additional user instructions: ").append(settings.customInstructions)
            }
        }
    }
}
