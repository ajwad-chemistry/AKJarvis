package com.akjarvis.app.domain

import com.akjarvis.app.domain.model.LanguageMode
import java.util.Calendar
import java.util.Locale

/** Everything the user can say that maps to a concrete device action. */
sealed interface ParsedIntent {
    data class OpenApp(val target: String) : ParsedIntent
    data class OpenSettings(val target: String) : ParsedIntent
    data class WebSearch(val query: String) : ParsedIntent
    data class Dial(val number: String?) : ParsedIntent
    data class ComposeSms(val number: String?, val text: String?) : ParsedIntent
    data class AddNote(val text: String) : ParsedIntent
    data class AddReminder(val message: String, val triggerAt: Long?) : ParsedIntent
    data object ShowTime : ParsedIntent
    data object ShowDate : ParsedIntent
    data object ReadNotifications : ParsedIntent
    data object OpenDownloads : ParsedIntent
    data object PickFile : ParsedIntent
    data object Help : ParsedIntent
    data object None : ParsedIntent
}

/**
 * Lightweight on-device command parser for English, Roman Urdu and Urdu.
 * Runs locally, so device commands (open app, reminders, notes…) work even
 * in Offline Mode without any AI backend.
 */
class IntentRouter {

    fun parse(rawInput: String): ParsedIntent {
        val wakePattern = Regex("^\\s*(jarvis|hey jarvis|ok jarvis|اے کے جاروس|جاروس)\\b[,:]?", RegexOption.IGNORE_CASE)
        val input = rawInput.replace(wakePattern, "").trim()
        if (input.isEmpty()) return ParsedIntent.None
        val text = input.lowercase(Locale.ROOT)

        parseReminder(input)?.let { return it }
        parseNote(text)?.let { return it }
        parseSearch(text)?.let { return it }
        parseSms(text)?.let { return it }
        parseDial(text)?.let { return it }
        parseOpen(text)?.let { return it }
        parseSimple(text)?.let { return it }
        return ParsedIntent.None
    }

    // ---- Individual command families -------------------------------------

    private fun parseReminder(input: String): ParsedIntent? {
        val isReminder = Regex("(remind|reminder|yaad dila|yaad dilao|یاد دلائیں|یاد دلاؤ)", RegexOption.IGNORE_CASE)
            .containsMatchIn(input)
        if (!isReminder) return null

        val now = System.currentTimeMillis()
        var triggerAt: Long? = null

        Regex("(?:in\\s+|)(\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours|hr|ghanta|ghantay|din|day|days)\\s*(?:baad)?", RegexOption.IGNORE_CASE)
            .find(input)?.let { m ->
                val amount = m.groupValues[1].toLongOrNull() ?: 0L
                triggerAt = now + when (m.groupValues[2].lowercase(Locale.ROOT)) {
                    "second", "seconds", "sec" -> amount * 1000L
                    "minute", "minutes", "min" -> amount * 60_000L
                    "hour", "hours", "hr", "ghanta", "ghantay" -> amount * 3_600_000L
                    else -> amount * 86_400_000L
                }
            }

        if (triggerAt == null) {
            Regex("(?:at|by|\\b)\\s*(\\d{1,2})[:.](\\d{2})", RegexOption.IGNORE_CASE).find(input)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull() ?: return@let
                val minute = m.groupValues[2].toIntOrNull() ?: return@let
                if (hour in 0..23 && minute in 0..59) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= now) add(Calendar.DATE, 1)
                    }
                    triggerAt = cal.timeInMillis
                }
            }
        }

        val message = input
            .replace(Regex("(remind me to|remind me|remind|reminder|yaad dila|yaad dilao|yaad dilana|یاد دلاؤ|یاد دلائیں)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(in|at|baad|baje|pe|par)?\\s*\\d+\\s*(second|seconds|sec|minute|minutes|min|hour|hours|hr|ghanta|ghantay|din|day|days)?\\s*(baad|baje|pe|par)?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\d{1,2}[:.]\\d{2}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '!')
        return ParsedIntent.AddReminder(message.ifBlank { input.trim() }, triggerAt)
    }

    private fun parseNote(text: String): ParsedIntent? {
        val noteRegex = Regex("(?:make|write|save|banao|likho)?\\s*(?:a\\s+)?note(?:\\s+down)?[\\s:,-]*(.+)", RegexOption.IGNORE_CASE)
        if (!text.contains(Regex("\\bnote\\b|نوٹ"))) return null
        val body = noteRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return ParsedIntent.AddNote(body.ifBlank { text.trim() })
    }

    private fun parseSearch(text: String): ParsedIntent? {
        val m = Regex("(?:search(?:\\s+for)?|google|youtube|dhoondo|dhundo|talash karo|talash)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val query = m.groupValues[1].trim()
        if (query.isBlank()) return null
        val engine = if (text.startsWith("youtube")) "youtube" else "web"
        return ParsedIntent.WebSearch(query).also { }
    }

    private fun parseDial(text: String): ParsedIntent? {
        val m = Regex("\\b(?:call|dial|phone)\\s+(.+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val target = m.groupValues[1].trim().ifBlank { return ParsedIntent.Dial(null) }
        val digits = target.replace(Regex("[^+\\d]"), "")
        return ParsedIntent.Dial(digits.ifBlank { null })
    }

    private fun parseSms(text: String): ParsedIntent? {
        val m = Regex("\\b(?:sms|text|message)\\s+(?:to\\s+)?(.+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val target = m.groupValues[1].trim()
        val digits = target.replace(Regex("[^+\\d]"), "")
        return ParsedIntent.ComposeSms(digits.ifBlank { null }, null)
    }

    private fun parseOpen(text: String): ParsedIntent? {
        val m = Regex("\\b(?:open|launch|start|start karo|kholo|khol)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val target = m.groupValues[1].trim().trim('.', '!', '?')
        if (target.isBlank()) return null
        val settingsTarget = settingsKeyword(target) ?: return ParsedIntent.OpenApp(target)
        return ParsedIntent.OpenSettings(settingsTarget)
    }

    private fun parseSimple(text: String): ParsedIntent? {
        if (Regex("\\b(help|madad|what can you do|kya kar sakte)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.Help
        }
        if (Regex("\\b(time|kitne baje|kitna baja|waqt)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.ShowTime
        }
        if (Regex("\\b(date|tareekh|aaj)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.ShowDate
        }
        if (Regex("\\b(notifications|notices|itlaat)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.ReadNotifications
        }
        if (Regex("\\b(downloads|download folder)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.OpenDownloads
        }
        if (Regex("\\b(pick file|choose file|select file|file uthao)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.PickFile
        }
        settingsKeyword(text)?.let { return ParsedIntent.OpenSettings(it) }
        return null
    }

    /** Maps a spoken phrase to a settings screen key, or null if not a settings phrase. */
    private fun settingsKeyword(text: String): String? {
        val table = listOf(
            "wifi" to "wifi", "wi fi" to "wifi", "وائی فائی" to "wifi",
            "bluetooth" to "bluetooth", "بلوٹوتھ" to "bluetooth",
            "airplane" to "airplane", "flight mode" to "airplane",
            "brightness" to "display", "display" to "display", "screen" to "display",
            "volume" to "sound", "sound" to "sound", "awaz" to "sound",
            "battery" to "battery", "battry" to "battery", "بیٹری" to "battery",
            "storage" to "storage", "memory" to "storage",
            "location" to "location", "gps" to "location",
            "app info" to "apps", "applications" to "apps",
            "security" to "security", "date" to "datetime", "accessibility" to "accessibility",
            "notification access" to "notifaccess",
            "settings" to "main", "setting" to "main", "سیٹنگ" to "main",
        )
        for ((keyword, key) in table) {
            if (text.contains(keyword)) return key
        }
        return null
    }

    companion object {
        private val URDU_SCRIPT = Regex("[\\u0600-\\u06FF]")
        private val ROMAN_URDU_WORDS = setOf(
            "kaise", "kya", "hai", "hain", "nahi", "nahin", "theek", "thik", "shukriya",
            "acha", "achha", "acha", "mujhe", "mera", "aap", "tum", "karo", "kholo",
            "batao", "chahiye", "bhai", "yaar", "bilkul", "abhi", "kal", "aaj", "kitna",
            "kahan", "kyun", "kyu", "mein", "me", "ho", "hun", "wala", "wali",
        )

        /** Best-effort language detection for choosing reply language and speech engine. */
        fun detectLanguage(text: String): LanguageMode = when {
            URDU_SCRIPT.containsMatchIn(text) -> LanguageMode.URDU
            text.lowercase(Locale.ROOT).split(Regex("\\s+")).any { it.trim('.', ',', '!', '?') in ROMAN_URDU_WORDS } ->
                LanguageMode.ROMAN_URDU
            else -> LanguageMode.ENGLISH
        }
    }
}
