package com.akjarvis.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.akjarvis.app.domain.model.LanguageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Push-to-talk microphone input built on Android's SpeechRecognizer.
 * The microphone is active only while a recognition session is running;
 * nothing is recorded or stored between sessions.
 */
class SpeechInputManager(private val context: Context) {

    data class VoiceUiState(
        val isListening: Boolean = false,
        val partialText: String = "",
        val error: String? = null,
    )

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var language: LanguageMode = LanguageMode.ENGLISH

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageMode: LanguageMode, result: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceUiState(error = "Speech recognition is not available on this device")
            return
        }
        stopListening()
        onResult = result
        language = languageMode

        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                when (languageMode) {
                    LanguageMode.URDU -> "ur-PK"
                    LanguageMode.ROMAN_URDU -> "en-IN"
                    LanguageMode.ENGLISH -> "en-US"
                },
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _state.value = VoiceUiState(isListening = true)
        rec.startListening(intent)
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = VoiceUiState()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceUiState(isListening = true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> " recognizer busy, try again."
                else -> "Voice input error ($error)."
            }
            _state.value = VoiceUiState(error = message.trim())
            recognizer = null
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = VoiceUiState()
            recognizer = null
            if (text.isNotBlank()) onResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = VoiceUiState(isListening = true, partialText = partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
