package com.akjarvis.app.data.repo

import com.akjarvis.app.data.db.AssistantDatabase
import com.akjarvis.app.data.db.ChatDao
import com.akjarvis.app.data.db.ChatMessageEntity
import com.akjarvis.app.data.db.NoteEntity
import com.akjarvis.app.data.db.ReminderDao
import com.akjarvis.app.data.db.ReminderEntity
import com.akjarvis.app.device.ReminderScheduler
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val dao: ChatDao) {
    fun observeMessages(): Flow<List<ChatMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(text: String, isFromUser: Boolean, mode: AiMode? = null) {
        dao.insert(
            ChatMessageEntity(
                isFromUser = isFromUser,
                text = text,
                timestamp = System.currentTimeMillis(),
                mode = mode?.name,
            )
        )
    }

    suspend fun recentForAi(limit: Int = 12): List<Pair<Boolean, String>> =
        dao.recent(limit).map { it.isFromUser to it.text }

    suspend fun clear() = dao.clear()
}

private fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    isFromUser = isFromUser,
    text = text,
    timestamp = timestamp,
    mode = mode?.let { runCatching { AiMode.valueOf(it) }.getOrNull() },
)

class NotesRepository(private val database: AssistantDatabase) {
    fun observeNotes(): Flow<List<NoteEntity>> = database.noteDao().observeAll()

    suspend fun add(title: String, body: String) {
        val now = System.currentTimeMillis()
        database.noteDao().insert(NoteEntity(title = title, body = body, createdAt = now, updatedAt = now))
    }

    suspend fun update(note: NoteEntity) =
        database.noteDao().update(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(note: NoteEntity) = database.noteDao().delete(note)
}

class RemindersRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
) {
    fun observePending(): Flow<List<ReminderEntity>> = dao.observePending()

    /** Persists the reminder and arms the system alarm. Returns its database id. */
    suspend fun add(message: String, triggerAt: Long): Long {
        val id = dao.insert(
            ReminderEntity(
                message = message,
                triggerAt = triggerAt,
                createdAt = System.currentTimeMillis(),
            )
        )
        scheduler.schedule(id, triggerAt, message)
        return id
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.delete(id)
    }

    suspend fun markDone(id: Long) {
        dao.byId(id)?.let { scheduler.cancel(it.id) }
        dao.markDone(id)
    }

    /** Used by [com.akjarvis.app.device.BootReceiver] after a reboot. */
    suspend fun rescheduleAll() {
        dao.pendingAfter(System.currentTimeMillis()).forEach {
            scheduler.schedule(it.id, it.triggerAt, it.message)
        }
    }
}
