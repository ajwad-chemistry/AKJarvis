package com.akjarvis.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akjarvis.app.ai.AiRouter
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.data.prefs.SettingsRepository
import com.akjarvis.app.data.repo.ChatRepository
import com.akjarvis.app.data.repo.MemoryRepository
import com.akjarvis.app.device.DeviceActionExecutor
import com.akjarvis.app.di.ServiceLocator
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.AssistantState
import com.akjarvis.app.domain.model.ChatTurn
import com.akjarvis.app.device.FileManager
import com.akjarvis.app.voice.SpeechInputManager
import com.akjarvis.app.voice.TtsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Orchestrates a chat turn: save the user message, run device commands locally,
 * or route the text to the AI (cloud with offline fallback), then save and
 * optionally speak the reply.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository
    private val chatRepository: ChatRepository = ServiceLocator.chatRepository
    private val aiRouter: AiRouter = ServiceLocator.aiRouter
    private val intentRouter: IntentRouter = ServiceLocator.intentRouter
    private val executor: DeviceActionExecutor = ServiceLocator.actionExecutor
    private val tts: TtsManager = ServiceLocator.ttsManager
    private val speech = SpeechInputManager(app)
    private val memoryRepository: MemoryRepository = ServiceLocator.memoryRepository

    /** True while the current turn was started by voice (enables the follow-up window). */
    private var turnWasVoice = false

    val messages: StateFlow<List<com.akjarvis.app.domain.model.ChatMessage>> =
        chatRepository.observeMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val voiceState: StateFlow<SpeechInputManager.VoiceUiState> = speech.state

    val activeMode: StateFlow<AiMode> =
        settingsRepository.settings.map { it.aiMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiMode.FALLBACK)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Fired when the user asks the assistant to pick a file; the UI opens the SAF picker. */
    val pickFileEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        executor.onPickFileRequested = { pickFileEvent.tryEmit(Unit) }
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch { handleUserText(text) }
    }

    fun startVoice() {
        val language = settings.value.language
        _assistantState.value = AssistantState.LISTENING
        speech.startListening(language) { recognized ->
            turnWasVoice = true
            viewModelScope.launch { handleUserText(recognized) }
        }
    }

    fun stopVoice() {
        speech.stopListening()
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun clearChat() {
        viewModelScope.launch { chatRepository.clear() }
    }

    /** Called by the UI after the SAF file picker returns a document. */
    fun onFilePicked(context: android.content.Context, uri: android.net.Uri) {
        val description = FileManager.describe(context, uri)
        viewModelScope.launch {
            chatRepository.add("📎 $description", isFromUser = true)
            chatRepository.add(
                "File selected: $description. Tell me what you want to do with it.",
                isFromUser = false,
                mode = AiMode.FALLBACK,
            )
        }
    }

    private suspend fun handleUserText(text: String) {
        _error.value = null
        chatRepository.add(text, isFromUser = true)
        _assistantState.value = AssistantState.THINKING
        val startedByVoice = turnWasVoice
        turnWasVoice = false
        try {
            val language = IntentRouter.detectLanguage(text)
            val parsed = intentRouter.parse(text)
            val deviceReply = if (parsed != ParsedIntent.None) executor.execute(parsed, language) else null
            val (reply, mode) = deviceReply?.let { it to null as AiMode? } ?: routeToAi(text)

            chatRepository.add(reply, isFromUser = false, mode = mode)

            val currentSettings = settingsRepository.current()
            if (currentSettings.speakReplies) {
                _assistantState.value = AssistantState.SPEAKING
                val speakLanguage = if (mode == AiMode.CLOUD) currentSettings.language else language
                tts.speak(reply, speakLanguage)
                if (startedByVoice && currentSettings.handsFree) {
                    openFollowUpWindow()
                }
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Something went wrong."
        } finally {
            _assistantState.value = AssistantState.IDLE
        }
    }

    /**
     * Hands-free follow-up window: after the assistant finishes speaking, keep
     * the microphone open briefly so the user can reply without repeating the
     * wake word (inspired by the AbuZar Jarvis continuous-listening design).
     * The mic is only open for this short window; nothing is recorded to disk.
     */
    private suspend fun openFollowUpWindow() {
        kotlinx.coroutines.delay(400)
        _assistantState.value = AssistantState.LISTENING
        val followUp = withTimeoutOrNull(7_500L) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                speech.startListening(settings.value.language) { recognized ->
                    turnWasVoice = true
                    cont.resume(recognized)
                }
            }
        }
        if (followUp.isNullOrBlank()) {
            speech.stopListening()
            _assistantState.value = AssistantState.IDLE
        } else {
            handleUserText(followUp)
        }
    }

    private suspend fun routeToAi(text: String): Pair<String, AiMode> {
        val history = chatRepository.recentForAi()
            .map { (isUser, messageText) -> ChatTurn(if (isUser) "user" else "assistant", messageText) }
        val reply = aiRouter.respond(history, text)
        return reply.text to reply.mode
    }
}
