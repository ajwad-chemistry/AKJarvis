package com.akjarvis.app.ai

/**
 * Supported AI providers. Everything except Anthropic uses the
 * OpenAI-compatible chat/completions format (including Gemini, which offers an
 * OpenAI-compatible endpoint). All defaults point to free-tier models where
 * available; base URL, model and key are always editable in Settings.
 */
enum class ProviderKind { OPENAI_COMPATIBLE, ANTHROPIC }

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val kind: ProviderKind,
    val freeTierNote: String,
) {
    Z_AI(
        "Z.ai (GLM)",
        "https://api.z.ai/api/paas/v4/",
        "glm-4.5-flash",
        ProviderKind.OPENAI_COMPATIBLE,
        "glm-4.5-flash is a free-tier model",
    ),
    GEMINI(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "gemini-2.0-flash",
        ProviderKind.OPENAI_COMPATIBLE,
        "Free tier via Google AI Studio key",
    ),
    GROQ(
        "Groq",
        "https://api.groq.com/openai/v1/",
        "llama-3.3-70b-versatile",
        ProviderKind.OPENAI_COMPATIBLE,
        "Generous free tier at console.groq.com",
    ),
    OPENROUTER(
        "OpenRouter",
        "https://openrouter.ai/api/v1/",
        "meta-llama/llama-3.3-70b-instruct:free",
        ProviderKind.OPENAI_COMPATIBLE,
        "Models ending in :free are free",
    ),
    ANTHROPIC(
        "Claude (Anthropic)",
        "https://api.anthropic.com/v1/",
        "claude-3-5-haiku-latest",
        ProviderKind.ANTHROPIC,
        "Paid API (no free tier) — use sparingly",
    ),
    CUSTOM(
        "Custom",
        "",
        "",
        ProviderKind.OPENAI_COMPATIBLE,
        "Any OpenAI-compatible endpoint",
    ),
}
