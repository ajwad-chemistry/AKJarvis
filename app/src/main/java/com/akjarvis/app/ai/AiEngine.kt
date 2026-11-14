package com.akjarvis.app.ai

import com.akjarvis.app.domain.model.ChatTurn

/**
 * Common interface for every AI backend (cloud model, local model, offline engine).
 * Implementations must be safe to call from any coroutine and must throw on failure
 * so the router can degrade gracefully.
 */
interface AiEngine {
    /**
     * @param history recent conversation turns (oldest first), excluding the new user message
     * @param userText the new user message
     * @return the assistant reply text
     */
    suspend fun respond(history: List<ChatTurn>, userText: String): String
}
