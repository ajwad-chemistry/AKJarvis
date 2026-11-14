package com.akjarvis.app.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.akjarvis.app.MainActivity
import com.akjarvis.app.R

import com.akjarvis.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional always-listening wake word detection.
 *
 * IMPORTANT LIMITATIONS (documented in docs/LIMITATIONS.md):
 *  - Uses Android's SpeechRecognizer in a foreground service with continuous
 *    restarts; Android does not guarantee always-on background recognition on
 *    every vendor ROM, and battery use is higher while enabled.
 *  - Runs only after the user explicitly enables it in Settings AND grants
 *    microphone permission. It can be stopped any time from Settings or by
 *    swiping the notification away.
 */
class WakeWordService : android.app.Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakePhrase: String = "jarvis"
    private var scope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = mainScope
        mainScope.launch {
            wakePhrase = ServiceLocator.settingsRepository.settings.first().wakePhrase
            startListening()
        }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.settings_wake_word),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.settings_wake_word))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!hasMicPermission() || !SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        recognizer?.destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        rec.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        rec.startListening(intent)
    }

    private fun restartWithDelay(millis: Long = 500L) {
        handler.postDelayed({ if (!stopped) startListening() }, millis)
    }

    private fun onWakeDetected() {
        TtsSingleton.speak(this, "Yes?")
        val launch = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        // Brief pause so the confirmation is not re-captured as a command.
        handler.postDelayed({ if (!stopped) startListening() }, 2500L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase()
                .orEmpty()
            if (partial.contains(wakePhrase)) onWakeDetected()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase()
                .orEmpty()
            if (text.contains(wakePhrase)) onWakeDetected() else restartWithDelay()
        }

        override fun onError(error: Int) {
            restartWithDelay(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 500L)
        }
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    @Volatile
    private var stopped: Boolean = false

    companion object {
        const val ACTION_STOP = "com.akjarvis.app.action.STOP_WAKE_WORD"
        const val CHANNEL_ID = "wake_word"
        const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
