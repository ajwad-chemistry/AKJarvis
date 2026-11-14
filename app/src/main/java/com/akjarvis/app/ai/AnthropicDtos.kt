package com.akjarvis.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Anthropic Messages API DTOs (Claude). Format differs from OpenAI's:
// system prompt is a top-level field and replies come back as content blocks.

@Serializable
data class AnthropicRequest(
    val model: String,
    val system: String? = null,
    val messages: List<ApiMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
data class AnthropicContentBlock(
    val type: String = "text",
    val text: String? = null,
)
