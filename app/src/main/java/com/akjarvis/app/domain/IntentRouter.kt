package com.akjarvis.app.domain

import com.akjarvis.app.domain.model.LanguageMode
import java.util.Calendar
import java.util.Locale

/** Everything the user can say that maps to a concrete device action. */
sealed interface ParsedIntent {
    data class OpenApp(val target: String) : ParsedIntent
    data class OpenSettings(val target: String) : ParsedIntent
    data class WebSearch(val query: String) : ParsedIntent
    data class Dial(val number: String?, val name: String? = null) : ParsedIntent
    data class ComposeSms(val number: String?, val text: String?) : ParsedIntent
    data class AddNote(val text: String) : ParsedIntent
    data class AddReminder(val message: String, val triggerAt: Long?) : ParsedIntent
    data object ShowTime : ParsedIntent
    data object ShowDate : ParsedIntent
    data object ReadNotifications : ParsedIntent
    data object OpenDownloads : ParsedIntent
    data object PickFile : ParsedIntent
    data class Flashlight(val on: Boolean?) : ParsedIntent
    data object BatteryStatus : ParsedIntent
    data class Volume(val direction: VolumeDirection) : ParsedIntent
    data class ShareText(val text: String) : ParsedIntent
    data class CopyText(val text: String) : ParsedIntent
    data class Remember(val fact: String) : ParsedIntent
    data object RecallMemory : ParsedIntent
    /** A free web API lookup; kind is one of [com.akjarvis.app.data.web.WebTools] kinds. */
    data class WebQuery(val kind: String, val query: String) : ParsedIntent
    // Advanced mobile control (requires the user to enable Accessibility access)
    data object GoBack : ParsedIntent
    data object GoHome : ParsedIntent
    data object OpenRecents : ParsedIntent
    data object OpenNotificationShade : ParsedIntent
    data object ReadScreen : ParsedIntent
    data class TypeText(val text: String) : ParsedIntent
    data class Scroll(val up: Boolean) : ParsedIntent
    data class TapOn(val target: String) : ParsedIntent
    data class MediaControl(val action: MediaCommand) : ParsedIntent
    data class TermuxCommand(val args: List<String>) : ParsedIntent
    data object Help : ParsedIntent
    data object None : ParsedIntent
}

/** Direction for volume commands parsed from speech. */
enum class VolumeDirection { UP, DOWN, MUTE, UNMUTE }

/** Media transport command parsed from speech. */
enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS }

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
        parseKeyedWeb(text)?.let { return it }
        parseSms(text)?.let { return it }
        parseDial(text)?.let { return it }
        parseAutomation(text)?.let { return it }
        parseOpen(text)?.let { return it }
        parseDeviceTools(text)?.let { return it }
        parseWeb(text)?.let { return it }
        parseSimple(text)?.let { return it }
        return ParsedIntent.None
    }

    /**
     * Multi-command support (inspired by the AbuZar Jarvis multi-task engine):
     * splits a compound request on "and / aur / phir / then / ," and parses each
     * chunk. Returns multiple intents only when every chunk parses cleanly,
     * otherwise falls back to a single parse so queries like "search for cats
     * and dogs" stay intact.
     */
    fun parseAll(rawInput: String): List<ParsedIntent> {
        val chunks = rawInput.split(Regex("\\s*(?:,|\\b(?:aur|and|phir|then)\\b)\\s*", RegexOption.IGNORE_CASE))
            .filter { it.isNotBlank() }
        if (chunks.size < 2) return listOf(parse(rawInput))
        val parsed = chunks.map { parse(it) }
        return if (parsed.none { it is ParsedIntent.None }) parsed else listOf(parse(rawInput))
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
        val m = Regex("\\b(?:search(?:\\s+for)?|google|youtube|dhoondo|dhundo|talash karo|talash)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val query = m.groupValues[1].trim()
        if (query.isBlank()) return null
        val engine = if (text.startsWith("youtube")) "youtube" else "web"
        return ParsedIntent.WebSearch(query).also { }
    }

    private fun parseDial(text: String): ParsedIntent? {
        // "mama ko call karo" — contact-name calling
        Regex("(.+?)\\s+ko\\s+(?:call|phone|dial)(?:\\s+karo)?", RegexOption.IGNORE_CASE).find(text)?.let { ko ->
            val name = ko.groupValues[1].trim()
            if (name.isNotBlank()) return ParsedIntent.Dial(number = null, name = name)
        }
        val m = Regex("\\b(?:call|dial|phone)\\s+(.+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val target = m.groupValues[1].trim().ifBlank { return ParsedIntent.Dial(null) }
        val digits = target.replace(Regex("[^+\\d]"), "")
        return ParsedIntent.Dial(
            number = digits.ifBlank { null },
            name = if (digits.isBlank()) target else null,
        )
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

    /**
     * Permission-free hardware tools: flashlight, battery, volume, share, copy.
     * Parsed before settings fallback so "battery status" reports the level
     * instead of opening battery settings.
     */
    private fun parseDeviceTools(text: String): ParsedIntent? {
        // Flashlight: "flashlight on", "torch band karo", "torch jalao"
        if (Regex("\\b(flashlight|torch|flash light)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val on = when {
                Regex("\\b(off|band|bujhao|band karo)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> false
                Regex("\\b(on|jalao|lagao|on karo|chalu)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> true
                else -> null
            }
            return ParsedIntent.Flashlight(on)
        }
        // Battery: "battery status" / "battery kitni hai" (not "battery settings")
        if (Regex("\\bbatte?ry\\b|\u0628\u06cc\u0679\u0631\u06cc", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !text.contains("settings") && !text.contains("setting") &&
            Regex("\\b(status|level|percent|kitni|kaisi|check|charge|bach)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            return ParsedIntent.BatteryStatus
        }
        // Volume: "volume up", "awaz kam karo", "mute"
        if (Regex("\\b(volume|awaz|aawaz|sound)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            Regex("\\b(mute|unmute)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            val direction = when {
                Regex("\\b(up|barhao|bara|zyada|max|full)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> VolumeDirection.UP
                Regex("\\b(down|kam|less|low)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> VolumeDirection.DOWN
                Regex("\\bunmute\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> VolumeDirection.UNMUTE
                Regex("\\b(mute|silent|khamosh)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> VolumeDirection.MUTE
                else -> null
            } ?: return null
            return ParsedIntent.Volume(direction)
        }
        // Share: "share <text>"
        Regex("\\b(?:share)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val body = m.groupValues[1].trim().trim('.', '!', '?')
            if (body.isNotBlank()) return ParsedIntent.ShareText(body)
        }
        // Copy: "copy <text>" (clipboard write; reading the clipboard is never done)
        Regex("\\b(?:copy)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val body = m.groupValues[1].trim().trim('.', '!', '?')
            if (body.isNotBlank()) return ParsedIntent.CopyText(body)
        }
        // Memory: "remember <fact>" / "yaad rakho <fact>"; bare "remember" asks for recall
        Regex("\\b(?:remember|yaad rakho|yaad rakhna|یاد رکھو)\\b[:,]?(.*)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val body = m.groupValues[1].trim().trim('.', '!', '?')
            return if (body.isBlank()) ParsedIntent.RecallMemory else ParsedIntent.Remember(body)
        }
        if (Regex("\\b(what do you remember|kya yaad hai|meri baatein)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.RecallMemory
        }
        return null
    }

    /**
     * Advanced mobile control commands. These need the (opt-in) accessibility
     * service; the executor replies with an enable-instructions message when
     * it is switched off, so nothing happens silently.
     */
    private fun parseAutomation(text: String): ParsedIntent? {
        // Media transport: "play music", "gaana chalao", "next song", "pause"
        if (Regex("\\b(?:play|pause|chalao|bajao|music|gaana|gana|song)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !text.contains("youtube") && !text.contains("search")
        ) {
            val action = when {
                Regex("\\b(?:next|agla|agli|skip|aage)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> MediaCommand.NEXT
                Regex("\\b(?:previous|pichla|pichay|peechay)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> MediaCommand.PREVIOUS
                else -> MediaCommand.PLAY_PAUSE
            }
            return ParsedIntent.MediaControl(action)
        }
        if (Regex("\\b(?:go back|back jao|back karo|wapas jao|wapis jao|peeche jao|peechay jao|press back)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.GoBack
        }
        if (Regex("\\b(?:go home|home screen|homescreen|home par|home pe)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.GoHome
        }
        if (Regex("\\b(?:recent apps|recents)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.OpenRecents
        }
        if (Regex("\\b(?:notification panel|notification shade|panel kholo|status bar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.OpenNotificationShade
        }
        if (Regex("\\b(?:read\\s+(?:the\\s+|my\\s+)?screen|screen parho|screen padho|screen padh|whats on my screen|what's on my screen|screen kya)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.ReadScreen
        }
        Regex("\\b(?:type|type karo|likho)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val body = m.groupValues[1].trim().trim('.', '!', '?')
            if (body.isNotBlank()) return ParsedIntent.TypeText(body)
        }
        if (Regex("\\bscroll\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val up = Regex("\\b(?:up|upar|above|top)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            return ParsedIntent.Scroll(up)
        }
        Regex("\\b(?:tap on|click on|tap|click|press)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val target = m.groupValues[1].trim().trim('.', '!', '?')
            if (target.isNotBlank()) return ParsedIntent.TapOn(target)
        }
        Regex("\\btermux\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val args = m.groupValues[1].trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return ParsedIntent.TermuxCommand(args)
        }
        return null
    }

    /**
     * Keyed web tools parsed early so words like "phone" inside a Tavily query
     * are never mistaken for a call command.
     */
    private fun parseKeyedWeb(text: String): ParsedIntent? {
        Regex("\\btavily\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("TAVILY", it.groupValues[1].trim())
        }
        Regex("\\bwindy\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("WINDY", it.groupValues[1].trim().ifBlank { text })
        }
        if (Regex("\\b(?:nearby|near me|near by|qareeb|qareebi)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("NEARBY", text)
        }
        return null
    }

    /**
     * Free web-API skills (weather, namaz, scores, currency, knowledge, fun…).
     * Keyword table maps English + Roman Urdu + Urdu phrases to WebTools kinds.
     */
    private fun parseWeb(text: String): ParsedIntent? {
        Regex("\\b(?:research|research karo|research on)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("RESEARCH", it.groupValues[1].trim())
        }
        Regex("\\b(?:forecast|weather forecast|agla mausam|mausam forecast)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("FORECAST", it.groupValues[1].trim())
        }
        Regex("\\b(?:air quality|aqi|smog)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("AIR", it.groupValues[1].trim())
        }
        Regex("\\b(?:sunrise|sunset|suraj (?:kab|nikalta))\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("SUN", it.groupValues[1].trim())
        }
        if (Regex("\\bnamaz|\\bprayer|\\bsehri|\\biftar|\\bazaan|\\bazan|\\bnamaaz|نماز", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("PRAYER", text)
        }
        if (Regex("\\bcricket\\b|live score|match score|score kya", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("CRICKET", text)
        }
        if (Regex("trending (?:crypto|coins)|crypto trending", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("CRYPTO_TREND", text)
        }
        if (Regex("\\b(?:bitcoin|btc|ethereum|eth|solana|dogecoin|doge|crypto|xrp|ripple|bnb)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("CRYPTO", text)
        }
        if (Regex("\\bcurrency\\b|exchange rate|dollar (?:rate|ka rate|bhav)|(?:rate|bhav)\\b.*\\b(?:dollar|euro|pound|riyal|dirham|rupee)\\b|\\b(?:usd|eur|gbp|sar|aed|inr)\\b\\s*(?:to|vs)\\b|to pkr", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("CURRENCY", text)
        }
        Regex("\\b(?:meaning of|dictionary)\\b\\s*(.+)|(.+?)\\s+ka matlab|\\bmatlab\\s+(.+)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val word = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3]).firstOrNull { it.isNotBlank() }
            return ParsedIntent.WebQuery("MEANING", word?.trim() ?: "")
        }
        Regex("\\b(?:translate|tarjuma)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("TRANSLATE", it.groupValues[1].trim())
        }
        Regex("\\b(?:wiki|wikipedia)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("WIKI", it.groupValues[1].trim())
        }
        if (Regex("hacker news|tech news", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("NEWS", "")
        }
        Regex("\\b(?:book|kitab)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("BOOK", it.groupValues[1].trim())
        }
        Regex("\\b(?:country|country info|about country)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("COUNTRY", it.groupValues[1].trim())
        }
        if (Regex("\\bjoke\\b|mazah|chutkula|latifa", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("JOKE", "")
        }
        if (Regex("\\bquote\\b|aqwal|quotation", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("QUOTE", "")
        }
        if (Regex("\\badvice\\b|mashwara|mashvera", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("ADVICE", "")
        }
        if (Regex("\\b(?:random fact|facts?)\\b|dilchasp baat", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("FACT", "")
        }
        if (Regex("\\b(?:cat|billi)\\b.*(?:photo|picture|pic|image|tasveer)|(?:photo|picture|pic|image|tasveer).*\\b(?:cat|billi)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("CAT", "")
        }
        if (Regex("\\b(?:dog|kutta|kutte)\\b.*(?:photo|picture|pic|image|tasveer)|(?:photo|picture|pic|image|tasveer).*\\b(?:dog|kutta|kutte)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("DOG", "")
        }
        if (Regex("\\bxkcd\\b|\\bcomic\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("XKCD", "")
        }
        Regex("\\btime\\s+(?:in|of)\\s+(.+)|(.+?)\\s+ka (?:waqt|time)", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val city = listOf(m.groupValues[1], m.groupValues[2]).firstOrNull { it.isNotBlank() }
            return ParsedIntent.WebQuery("TIMEZONE", city?.trim() ?: "")
        }
        if (Regex("\\bmy ip\\b|mera ip|ip address", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("MYIP", "")
        }
        Regex("\\b(?:shorten|short url|chota link|link chota)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("SHORTEN", it.groupValues[1].trim())
        }
        Regex("\\bwhere is\\s+(.+)|(.+?)\\s+kahan hai", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val place = listOf(m.groupValues[1], m.groupValues[2]).firstOrNull { it.isNotBlank() }
            return ParsedIntent.WebQuery("PLACE", place?.trim() ?: "")
        }
        Regex("\\b(?:distance|doori|kitni door)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("DISTANCE", it.groupValues[1].trim().ifBlank { text })
        }
        if (Regex("\\bholiday|chhutti|chutti|public holidays", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("HOLIDAY", text)
        }
        Regex("\\bgithub\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("GITHUB", it.groupValues[1].trim().ifBlank { text })
        }
        Regex("\\bstack\\s?overflow\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("STACK", it.groupValues[1].trim().ifBlank { text })
        }
        if (Regex("\\biss\\b|space station", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("ISS", "")
        }
        Regex("\\b(?:pokemon|pokémon)\\b\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("POKEMON", it.groupValues[1].trim())
        }
        if (Regex("\\bquran|quraan|\\bayat|\\bayah|\\bsurah|قرآن", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return ParsedIntent.WebQuery("QURAN", "")
        }
        Regex("\\b(?:weather|mausam|mosam)(?:\\s+(?:in|of|at|ka|me|mein))?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)?.let {
            return ParsedIntent.WebQuery("WEATHER", it.groupValues[1].trim())
        }
        return null
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
