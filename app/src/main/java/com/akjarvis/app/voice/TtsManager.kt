package com.akjarvis.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.akjarvis.app.domain.model.LanguageMode
import java.util.Locale

/**
 * Wraps Android's TextToSpeech. Initialized lazily on first use; the pending
 * utterance is spoken as soon as the engine finishes initializing.
 */
class TtsManager(private val context: Context) {

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready: Boolean = false

    private var pending: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    fun speak(text: String, language: LanguageMode, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        val engine = tts
        if (engine != null && ready) {
            speakNow(engine, text, language, onDone)
            return
        }
        pending = text
        if (engine != null) {
            pendingOnDone = onDone
            return
        }
        pendingOnDone = onDone
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending?.let {
                    speakNow(tts ?: return@TextToSpeech, it, language, pendingOnDone)
                    pending = null
                    pendingOnDone = null
                }
            }
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun speakNow(engine: TextToSpeech, text: String, language: LanguageMode, onDone: (() -> Unit)? = null) {
        val locale = when (language) {
            LanguageMode.URDU -> Locale("ur", "PK")
            LanguageMode.ROMAN_URDU -> Locale("en", "IN")
            LanguageMode.ENGLISH -> Locale.US
        }
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Urdu voices are missing on many devices; fall back to English.
            engine.setLanguage(Locale.US)
        }
        val utteranceId = "ak_jarvis_${System.currentTimeMillis()}"
        if (onDone != null) {
            engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) onDone()
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) onDone()
                }
            })
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
}
