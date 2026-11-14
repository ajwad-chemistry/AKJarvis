package com.akjarvis.app.domain.model

/** Which AI backend answers the user. */
enum class AiMode { CLOUD, FALLBACK }

enum class ThemeMode { SYSTEM, DARK, LIGHT, IRON }

/** Assistant conversation language. Roman Urdu is written in Latin script. */
enum class LanguageMode { ENGLISH, URDU, ROMAN_URDU }

enum class PersonalityStyle { PROFESSIONAL, FRIENDLY, FUNNY }

/** One chat bubble as shown in the UI / stored in the database. */
data class ChatMessage(
    val id: Long = 0,
    val isFromUser: Boolean,
    val text: String,
    val timestamp: Long,
    val mode: AiMode? = null,
)

/** A turn sent to the AI model. */
data class ChatTurn(val role: String, val content: String)

/** Visual state driving the animated orb and voice indicator. */
enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING }

/** A single installed launchable app (for "open <app>" commands). */
data class InstalledApp(val label: String, val packageName: String)
