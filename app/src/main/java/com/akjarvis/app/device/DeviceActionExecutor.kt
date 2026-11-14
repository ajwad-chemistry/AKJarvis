package com.akjarvis.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.akjarvis.app.data.repo.NotesRepository
import com.akjarvis.app.data.repo.RemindersRepository
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import com.akjarvis.app.domain.model.InstalledApp
import com.akjarvis.app.domain.model.LanguageMode

/**
 * Executes device commands parsed by [com.akjarvis.app.domain.IntentRouter] and
 * produces a spoken/typed confirmation. Safety rules enforced here:
 *  - Dial: only opens the dialer, never places the call.
 *  - SMS: only opens the messaging app with text pre-filled, never sends.
 *  - Files: only user-driven pickers or public folders; no private data access.
 */
class DeviceActionExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val remindersRepository: RemindersRepository,
    private val notesRepository: NotesRepository,
) {
    /** Hook the UI sets to open the system file picker on demand. */
    var onPickFileRequested: (() -> Unit)? = null

    /** Executes the intent; returns a confirmation reply, or null if not a device command. */
    suspend fun execute(intent: ParsedIntent, language: LanguageMode): String? = when (intent) {
        is ParsedIntent.OpenApp -> intent.target.let { target ->
            val app = appLauncher.launchBestMatch(target)
            if (app != null) PhraseBook.openingApp(app.label, language)
            else PhraseBook.appNotFound(target, language)
        }
        is ParsedIntent.OpenSettings -> {
            val settingsIntent = SettingsShortcuts.intentFor(intent.target)
            val started = settingsIntent != null && runCatching {
                context.startActivity(settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (started) PhraseBook.openingSettings(language) else null
        }
        is ParsedIntent.WebSearch -> {
            WebSearchHelper.search(context, intent.query)
            PhraseBook.searching(intent.query, language)
        }
        is ParsedIntent.Dial -> {
            openDialer(intent.number)
            PhraseBook.dialerOpened(language)
        }
        is ParsedIntent.ComposeSms -> {
            openSms(intent.number, intent.text)
            PhraseBook.smsOpened(language)
        }
        is ParsedIntent.AddNote -> {
            notesRepository.add(title = intent.text.take(40), body = intent.text)
            PhraseBook.noteSaved(language)
        }
        is ParsedIntent.AddReminder -> intent.triggerAt?.let { at ->
            remindersRepository.add(intent.message, at)
            PhraseBook.reminderSet(TimeFormat.time(at), language)
        } ?: PhraseBook.reminderNeedsTime(language)
        ParsedIntent.ShowTime -> PhraseBook.currentTime(language)
        ParsedIntent.ShowDate -> PhraseBook.currentDate(language)
        ParsedIntent.ReadNotifications -> notificationsSummary(language)
        ParsedIntent.OpenDownloads -> {
            runCatching {
                context.startActivity(
                    Intent(DownloadManagerAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PhraseBook.downloadsOpened(language)
        }
        ParsedIntent.PickFile -> {
            onPickFileRequested?.invoke()
            PhraseBook.pickFile(language)
        }
        ParsedIntent.Help -> PhraseBook.help(language)
        ParsedIntent.None -> null
    }

    private fun openDialer(number: String?) {
        val uri = if (number.isNullOrBlank()) Uri.parse("tel:") else Uri.parse("tel:$number")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openSms(number: String?, text: String?) {
        val uri = Uri.parse(if (number.isNullOrBlank()) "smsto:" else "smsto:$number")
        val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!text.isNullOrBlank()) intent.putExtra("sms_body", text)
        runCatching { context.startActivity(intent) }
    }

    private fun notificationsSummary(language: LanguageMode): String {
        if (!NotificationListener.hasAccess(context)) return PhraseBook.noNotificationAccess(language)
        val items = NotificationListener.recent().take(5)
        if (items.isEmpty()) return PhraseBook.noNotifications(language)
        return PhraseBook.notificationsList(items, language)
    }

    companion object {
        const val DownloadManagerAction = "android.intent.action.VIEW_DOWNLOADS"
    }
}

/** Multilingual confirmation strings for device actions. */
object PhraseBook {
    fun openingApp(label: String, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "$label کھول رہا ہوں"
        LanguageMode.ROMAN_URDU -> "$label khol raha hoon"
        LanguageMode.ENGLISH -> "Opening $label"
    }

    fun appNotFound(name: String, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "میں \"$name\" نام کی کوئی ایپ نہیں تلاش سکا۔"
        LanguageMode.ROMAN_URDU -> "Muje \"$name\" naam ki koi app nahi mili."
        LanguageMode.ENGLISH -> "I couldn't find an app named \"$name\"."
    }

    fun openingSettings(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "سیٹنگز کھول رہا ہوں"
        LanguageMode.ROMAN_URDU -> "Settings khol raha hoon"
        LanguageMode.ENGLISH -> "Opening settings"
    }

    fun searching(query: String, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "«$query» کی تلاش کر رہا ہوں"
        LanguageMode.ROMAN_URDU -> "\"$query\" search kar raha hoon"
        LanguageMode.ENGLISH -> "Searching the web for \"$query\""
    }

    fun dialerOpened(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ڈائلر کھول دیا۔ کال آپ خود کنفرم کریں گے۔"
        LanguageMode.ROMAN_URDU -> "Dialer khol diya. Call aap khud confirm karein ge."
        LanguageMode.ENGLISH -> "I opened the dialer. You confirm the call yourself."
    }

    fun smsOpened(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "میسیجنگ ایپ کھول دی۔ بھیجنا آپ کے کنفرمیشن پر ہو گا۔"
        LanguageMode.ROMAN_URDU -> "Messaging app khol di. Bhejna aap ke confirmation par hoga."
        LanguageMode.ENGLISH -> "I opened messaging. Nothing is sent until you press send."
    }

    fun noteSaved(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "نوٹ محفوظ کر لیا۔"
        LanguageMode.ROMAN_URDU -> "Note save kar liya."
        LanguageMode.ENGLISH -> "Note saved."
    }

    fun reminderSet(at: String, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "یاد دہانی مقرر: $at"
        LanguageMode.ROMAN_URDU -> "Reminder set: $at"
        LanguageMode.ENGLISH -> "Reminder set for $at"
    }

    fun reminderNeedsTime(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "براہ کرم وقت بتائیں، مثلاً: 10 منٹ بعد یاد دلاؤ۔"
        LanguageMode.ROMAN_URDU -> "Time bata dein, misal: 10 minute baad yaad dilao."
        LanguageMode.ENGLISH -> "Please include a time, for example: remind me in 10 minutes."
    }

    fun currentTime(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ابھی وقت ہے ${TimeFormat.time(System.currentTimeMillis())}"
        LanguageMode.ROMAN_URDU -> "Abhi waqt hai ${TimeFormat.time(System.currentTimeMillis())}"
        LanguageMode.ENGLISH -> "It is ${TimeFormat.time(System.currentTimeMillis())}"
    }

    fun currentDate(lang: LanguageMode) = currentTime(lang)

    fun noNotificationAccess(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "نوٹیفکیشن رسائی اجازت نہیں دی گئی۔ آپ اسے سیٹنگز میں فعال کر سکتے ہیں۔"
        LanguageMode.ROMAN_URDU -> "Notification access ki ijazat nahi hai. Aap Settings mein on kar sakte hain."
        LanguageMode.ENGLISH -> "I don't have notification access. You can grant it in Settings → Permissions."
    }

    fun noNotifications(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ابھی کوئی نوٹیفکیشن موصول نہیں ہوا۔"
        LanguageMode.ROMAN_URDU -> "Abhi koi notification nahi aayi."
        LanguageMode.ENGLISH -> "No notifications captured yet."
    }

    fun notificationsList(items: List<NotifInfo>, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> items.joinToString("\n") { "• ${it.title}: ${it.text}" }
        LanguageMode.ROMAN_URDU -> items.joinToString("\n") { "• ${it.title}: ${it.text}" }
        LanguageMode.ENGLISH -> items.joinToString("\n") { "• ${it.title}: ${it.text}" }
    }

    fun downloadsOpened(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ڈاؤن لوڈز کھول رہا ہوں"
        LanguageMode.ROMAN_URDU -> "Downloads khol raha hoon"
        LanguageMode.ENGLISH -> "Opening Downloads"
    }

    fun pickFile(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "فائل منتخب کریں"
        LanguageMode.ROMAN_URDU -> "File chunein"
        LanguageMode.ENGLISH -> "Choose a file"
    }

    fun help(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "میں ایپ کھول سکتا ہوں، سیٹنگز، یاد دہانیاں، نوٹس، ویب سرچ اور نوٹیفکیشن پڑھ سکتا ہوں۔"
        LanguageMode.ROMAN_URDU -> "Main app khol sakta hoon, settings, reminders, notes, web search aur notifications parh sakta hoon."
        LanguageMode.ENGLISH -> "I can open apps, change settings, set reminders, save notes, search the web and read your notifications."
    }
}
