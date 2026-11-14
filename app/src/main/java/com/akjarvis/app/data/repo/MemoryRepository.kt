package com.akjarvis.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.memoryDataStore by preferencesDataStore(name = "ak_jarvis_memory")

/**
 * Long-term assistant memory, inspired by the Hermes tri-partite context
 * design: short facts the user explicitly asks the assistant to remember
 * ("remember that my wifi password is in the blue drawer"). Facts are stored
 * only on-device and injected into the AI system prompt for personalised
 * replies. Nothing is learned silently — every fact is user-initiated.
 */
class MemoryRepository(private val context: Context) {

    private object Keys {
        val FACTS = stringPreferencesKey("user_facts")
    }

    private val separator = "\u001F"

    val facts: Flow<List<String>> = context.memoryDataStore.data.map { p ->
        p[Keys.FACTS]?.split(separator)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun add(fact: String) {
        val clean = fact.trim().trim('.', '!', '?').take(200)
        if (clean.isBlank()) return
        context.memoryDataStore.edit { p ->
            val existing = p[Keys.FACTS]?.split(separator)?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(clean) + existing.filter { !it.equals(clean, ignoreCase = true) })
                .distinctBy { it.lowercase() }
                .take(MAX_FACTS)
            p[Keys.FACTS] = updated.joinToString(separator)
        }
    }

    suspend fun clear() {
        context.memoryDataStore.edit { it.remove(Keys.FACTS) }
    }

    companion object {
        const val MAX_FACTS = 30
    }
}
