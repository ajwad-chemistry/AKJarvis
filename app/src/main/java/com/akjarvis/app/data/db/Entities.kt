package com.akjarvis.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isFromUser: Boolean,
    val text: String,
    val timestamp: Long,
    /** "CLOUD" / "FALLBACK" for assistant messages, null for user messages. */
    val mode: String? = null,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val triggerAt: Long,
    val createdAt: Long,
    val isDone: Boolean = false,
)
