package com.akjarvis.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Process-wide TTS for services (wake word confirmation) that don't have a
 * configured LanguageMode. Simple English voice with graceful degradation.
 */
object TtsSingleton {
    @Volatile
    private var tts: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine != null) {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, "wake_${System.currentTimeMillis()}")
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching {
                    tts?.setLanguage(Locale.US)
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "wake_${System.currentTimeMillis()}")
                }
            }
        }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
    }
}
