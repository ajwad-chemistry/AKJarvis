package com.akjarvis.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akjarvis.app.ai.AiProvider
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.data.web.UpdateChecker
import com.akjarvis.app.data.prefs.SettingsRepository
import com.akjarvis.app.data.repo.ChatRepository
import com.akjarvis.app.data.repo.MemoryRepository
import com.akjarvis.app.di.ServiceLocator
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.LanguageMode
import com.akjarvis.app.domain.model.PersonalityStyle
import com.akjarvis.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: SettingsRepository = ServiceLocator.settingsRepository
    private val chatRepository: ChatRepository = ServiceLocator.chatRepository
    private val memoryRepository: MemoryRepository = ServiceLocator.memoryRepository

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setAiMode(mode: AiMode) = launch { repository.setAiMode(mode) }
    fun setProvider(provider: AiProvider) = launch { repository.setProvider(provider) }
    fun setApiKey(key: String) = launch { repository.setApiKey(key) }
    fun setCricketApiKey(key: String) = launch { repository.setCricketApiKey(key) }
    fun setTavilyApiKey(key: String) = launch { repository.setTavilyApiKey(key) }
    fun setWindyApiKey(key: String) = launch { repository.setWindyApiKey(key) }
    fun setGeoapifyApiKey(key: String) = launch { repository.setGeoapifyApiKey(key) }
    fun setApiBaseUrl(url: String) = launch { repository.setApiBaseUrl(url) }
    fun setModel(model: String) = launch { repository.setModel(model) }
    fun setTheme(theme: ThemeMode) = launch { repository.setTheme(theme) }
    fun setLanguage(language: LanguageMode) = launch { repository.setLanguage(language) }
    fun setPersonalityStyle(style: PersonalityStyle) = launch { repository.setPersonalityStyle(style) }
    fun setAssistantName(name: String) = launch { repository.setAssistantName(name) }
    fun setCustomInstructions(text: String) = launch { repository.setCustomInstructions(text) }
    fun setSpeakReplies(enabled: Boolean) = launch { repository.setSpeakReplies(enabled) }
    fun setHandsFree(enabled: Boolean) = launch { repository.setHandsFree(enabled) }
    fun setWakeWordEnabled(enabled: Boolean) = launch { repository.setWakeWordEnabled(enabled) }
    fun setWakePhrase(phrase: String) = launch { repository.setWakePhrase(phrase) }

    fun clearChat() = launch { chatRepository.clear() }
    fun clearMemory() = launch { memoryRepository.clear() }

    private val _updateInfo = MutableStateFlow<UpdateChecker.UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateChecker.UpdateInfo?> = _updateInfo

    init {
        viewModelScope.launch { _updateInfo.value = UpdateChecker.check() }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
