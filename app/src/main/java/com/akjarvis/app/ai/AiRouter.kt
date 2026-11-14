package com.akjarvis.app.ai

import com.akjarvis.app.data.prefs.SettingsRepository
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.ChatTurn

/**
 * Chooses which engine answers a message:
 *  1. Cloud AI when the user selected it and configured a key.
 *  2. Automatic degradation to the offline engine on any cloud failure,
 *     so the assistant never leaves the user without an answer.
 */
class AiRouter(
    private val settingsRepository: SettingsRepository,
    private val cloudEngine: CloudEngine,
    private val offlineEngine: OfflineEngine,
) {
    data class Reply(val text: String, val mode: AiMode)

    suspend fun respond(history: List<ChatTurn>, userText: String): Reply {
        val settings = settingsRepository.current()
        if (settings.aiMode == AiMode.CLOUD && settings.apiKey.isNotBlank()) {
            try {
                val answer = cloudEngine.respond(history, userText)
                return Reply(answer, AiMode.CLOUD)
            } catch (e: CloudException) {
                val fallback = offlineEngine.respond(history, userText)
                val note = "⚠️ Cloud AI failed: ${e.message}\n\n$fallback"
                return Reply(note, AiMode.FALLBACK)
            }
        }
        return Reply(offlineEngine.respond(history, userText), AiMode.FALLBACK)
    }
}
