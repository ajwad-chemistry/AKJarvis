package com.akjarvis.app.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akjarvis.app.data.db.NoteEntity
import com.akjarvis.app.data.db.ReminderEntity
import com.akjarvis.app.data.repo.NotesRepository
import com.akjarvis.app.data.repo.RemindersRepository
import com.akjarvis.app.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val notesRepository: NotesRepository = ServiceLocator.notesRepository
    private val remindersRepository: RemindersRepository = ServiceLocator.remindersRepository

    val notes: StateFlow<List<NoteEntity>> =
        notesRepository.observeNotes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> =
        remindersRepository.observePending()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch { notesRepository.add(title.ifBlank { body.take(40) }, body.ifBlank { title }) }
    }

    fun deleteNote(note: NoteEntity) = viewModelScope.launch { notesRepository.delete(note) }

    fun addReminder(message: String, triggerAt: Long) {
        if (message.isBlank() || triggerAt <= System.currentTimeMillis()) return
        viewModelScope.launch { remindersRepository.add(message, triggerAt) }
    }

    fun deleteReminder(id: Long) = viewModelScope.launch { remindersRepository.delete(id) }
}
