package com.akjarvis.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI-compatible chat completion DTOs, used with Z.ai (api.z.ai) and any
// compatible provider. Field names follow the wire format.

@Serializable
data class ApiMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val temperature: Double = 0.6,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ApiChoice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
data class ApiChoice(
    val index: Int = 0,
    val message: ApiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val code: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError? = null,
    val message: String? = null,
)
