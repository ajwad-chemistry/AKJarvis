package com.akjarvis.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class, NoteEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun build(context: Context): AssistantDatabase =
            Room.databaseBuilder(context, AssistantDatabase::class.java, "ak_jarvis.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
