package com.akjarvis.app.di

import android.app.Application
import android.content.Context
import com.akjarvis.app.ai.AiRouter
import com.akjarvis.app.ai.CloudEngine
import com.akjarvis.app.ai.OfflineEngine
import com.akjarvis.app.data.db.AssistantDatabase
import com.akjarvis.app.data.prefs.SettingsRepository
import com.akjarvis.app.data.repo.ChatRepository
import com.akjarvis.app.data.repo.MemoryRepository
import com.akjarvis.app.data.repo.NotesRepository
import com.akjarvis.app.data.repo.RemindersRepository
import com.akjarvis.app.device.AppLauncher
import com.akjarvis.app.device.DeviceActionExecutor
import com.akjarvis.app.device.ReminderScheduler
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.voice.TtsManager
import kotlinx.coroutines.flow.first

/**
 * Lightweight manual dependency container. Chosen over Hilt/Dagger to keep the
 * project dependency-free and easy to build anywhere.
 */
object ServiceLocator {
    private lateinit var appContext: Context

    val database by lazy { AssistantDatabase.build(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val chatRepository by lazy { ChatRepository(database.chatDao()) }
    val notesRepository by lazy { NotesRepository(database) }
    val remindersRepository by lazy { RemindersRepository(database.reminderDao(), reminderScheduler) }
    val cloudEngine by lazy {
        CloudEngine(
            settingsProvider = { settingsRepository.settings.first() },
            memoryProvider = { memoryRepository.facts.first() },
        )
    }
    val offlineEngine by lazy { OfflineEngine(appContext) }
    val aiRouter by lazy { AiRouter(settingsRepository, cloudEngine, offlineEngine) }
    val intentRouter by lazy { IntentRouter() }
    val appLauncher by lazy { AppLauncher(appContext) }
    val reminderScheduler by lazy { ReminderScheduler(appContext) }
    val ttsManager by lazy { TtsManager(appContext) }
    val actionExecutor by lazy {
        DeviceActionExecutor(appContext, appLauncher, remindersRepository, notesRepository, memoryRepository)
    }
    val memoryRepository by lazy { MemoryRepository(appContext) }

    fun init(app: Application) {
        appContext = app.applicationContext
    }
}
