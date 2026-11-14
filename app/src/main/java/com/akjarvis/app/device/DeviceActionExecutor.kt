package com.akjarvis.app.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.akjarvis.app.data.repo.MemoryRepository
import com.akjarvis.app.data.repo.NotesRepository
import com.akjarvis.app.data.repo.RemindersRepository
import com.akjarvis.app.data.web.WebTools
import kotlinx.coroutines.flow.first
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import com.akjarvis.app.domain.VolumeDirection
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
    private val memoryRepository: MemoryRepository? = null,
    private val cricketApiKeyProvider: suspend () -> String = { "" },
) {
    /** Hook the UI sets to open the system file picker on demand. */
    var onPickFileRequested: (() -> Unit)? = null

    /** Hook the UI sets to receive a short label of every executed action (tool-call log). */
    var onActionLabel: ((String) -> Unit)? = null

    /** Executes the intent; returns a confirmation reply, or null if not a device command. */
    suspend fun execute(intent: ParsedIntent, language: LanguageMode): String? {
        if (intent != ParsedIntent.None) onActionLabel?.invoke(labelOf(intent))
        return executeIntent(intent, language)
    }

    private fun labelOf(intent: ParsedIntent): String = when (intent) {
        is ParsedIntent.OpenApp -> "open ${intent.target}"
        is ParsedIntent.OpenSettings -> "settings: ${intent.target}"
        is ParsedIntent.WebSearch -> "web search"
        is ParsedIntent.Dial -> "call"
        is ParsedIntent.ComposeSms -> "sms"
        is ParsedIntent.AddNote -> "note"
        is ParsedIntent.AddReminder -> "reminder"
        ParsedIntent.ShowTime -> "time"
        ParsedIntent.ShowDate -> "date"
        ParsedIntent.ReadNotifications -> "notifications"
        ParsedIntent.OpenDownloads -> "downloads"
        ParsedIntent.PickFile -> "file picker"
        is ParsedIntent.Flashlight -> "torch"
        ParsedIntent.BatteryStatus -> "battery"
        is ParsedIntent.Volume -> "volume"
        is ParsedIntent.ShareText -> "share"
        is ParsedIntent.CopyText -> "clipboard"
        is ParsedIntent.Remember -> "memory"
        ParsedIntent.RecallMemory -> "memory"
        is ParsedIntent.WebQuery -> intent.kind.lowercase()
        ParsedIntent.Help -> "help"
        ParsedIntent.None -> ""
    }

    private suspend fun executeIntent(intent: ParsedIntent, language: LanguageMode): String? {
        return when (intent) {
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
            if (intent.number == null && intent.name != null && !DeviceTools.hasContactsAccess(context)) {
                return PhraseBook.contactPermissionNeeded(language)
            }
            val number = intent.number ?: intent.name?.let { name ->
                DeviceTools.findContactNumber(context, name)?.second
                    ?: return PhraseBook.contactNotFound(name, language)
            }
            openDialer(number)
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
        is ParsedIntent.Flashlight -> {
            val changed = DeviceTools.setTorch(context, intent.on)
            if (changed) PhraseBook.torchSet(intent.on, language) else PhraseBook.torchUnavailable(language)
        }
        ParsedIntent.BatteryStatus -> {
            val info = DeviceTools.readBattery(context)
            if (info != null) PhraseBook.batteryStatus(info.percent, info.charging, language)
            else PhraseBook.batteryUnavailable(language)
        }
        is ParsedIntent.Volume -> {
            DeviceTools.changeVolume(
                context,
                when (intent.direction) {
                    VolumeDirection.UP -> DeviceTools.VolumeAction.UP
                    VolumeDirection.DOWN -> DeviceTools.VolumeAction.DOWN
                    VolumeDirection.MUTE -> DeviceTools.VolumeAction.MUTE
                    VolumeDirection.UNMUTE -> DeviceTools.VolumeAction.UNMUTE
                },
            )
            val percent = DeviceTools.currentVolumePercent(context)
            PhraseBook.volumeSet(intent.direction, percent, language)
        }
        is ParsedIntent.ShareText -> {
            val ok = DeviceTools.shareText(context, intent.text)
            if (ok) PhraseBook.shareOpened(language) else null
        }
        is ParsedIntent.CopyText -> {
            val ok = DeviceTools.copyToClipboard(context, intent.text)
            if (ok) PhraseBook.copied(language) else null
        }
        is ParsedIntent.Remember -> {
            val repo = memoryRepository
            if (repo == null) null
            else {
                repo.add(intent.fact)
                PhraseBook.remembered(language)
            }
        }
        ParsedIntent.RecallMemory -> {
            val repo = memoryRepository
            if (repo == null) null
            else {
                val facts = repo.facts.first()
                if (facts.isEmpty()) PhraseBook.memoryEmpty(language)
                else PhraseBook.memoryList(facts, language)
            }
        }
        is ParsedIntent.WebQuery ->
            WebTools.dispatch(intent.kind, intent.query, cricketApiKeyProvider())
                ?: PhraseBook.webLookupFailed(language)
        ParsedIntent.Help -> PhraseBook.help(language)
        ParsedIntent.None -> null
        }
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
        LanguageMode.URDU -> "میں ایپ کھول سکتا ہوں، یاد دہانیاں، نوٹس، ٹرچ، والیوم، بیٹری، موسم، نماز کے اوقات، کرکٹ سکور، کرنسی ریٹ، ترجمہ، اور بہت کچھ کر سکتا ہوں۔"
        LanguageMode.ROMAN_URDU -> "Main apps, settings, reminders, notes, torch, volume, battery ke saath weather, namaz times, cricket score, currency rates, translation, wikipedia, jokes aur 30+ web skills bhi chala sakta hoon. Multi-commands bhi: 'torch on karo aur volume up'."
        LanguageMode.ENGLISH -> "I can open apps, set reminders, save notes, control torch/volume, check battery, and answer from 30+ free web APIs: weather, forecast, air quality, namaz times, cricket scores, currency, crypto, dictionary, translation, wikipedia, books, jokes and more. Try multi-commands like \"torch on and volume up\"."
    }

    fun torchSet(on: Boolean?, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> if (on != false) "ٹرچ آن کر دی۔" else "ٹرچ بند کر دی۔"
        LanguageMode.ROMAN_URDU -> if (on != false) "Torch on kar di." else "Torch band kar di."
        LanguageMode.ENGLISH -> if (on != false) "Torch is on." else "Torch is off."
    }

    fun torchUnavailable(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "اس ڈیوائس پر ٹرچ کنٹرول ممکن نہیں۔"
        LanguageMode.ROMAN_URDU -> "Is device par torch control mumkin nahi."
        LanguageMode.ENGLISH -> "This device has no controllable torch."
    }

    fun batteryStatus(percent: Int, charging: Boolean, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "بیٹری $percent فیصد ہے${if (charging) "، چارج ہو رہی ہے" else ""}۔"
        LanguageMode.ROMAN_URDU -> "Battery $percent% hai${if (charging) ", charging ho rahi hai" else ""}."
        LanguageMode.ENGLISH -> "Battery is at $percent%${if (charging) " and charging" else ""}."
    }

    fun batteryUnavailable(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "بیٹری کی حالت معلوم نہیں ہو سکی۔"
        LanguageMode.ROMAN_URDU -> "Battery ki halat maloom nahi ho saki."
        LanguageMode.ENGLISH -> "I couldn't read the battery state."
    }

    fun volumeSet(direction: VolumeDirection, percent: Int?, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> {
            val action = when (direction) {
                VolumeDirection.UP -> "والیوم بڑھا"
                VolumeDirection.DOWN -> "والیوم کم"
                VolumeDirection.MUTE -> "میوٹ"
                VolumeDirection.UNMUTE -> "ان میوٹ"
            }
            if (percent != null) "$action دیا۔ اب $percent فیصد ہے۔" else "$action دیا۔"
        }
        LanguageMode.ROMAN_URDU -> {
            val action = when (direction) {
                VolumeDirection.UP -> "Volume barha"
                VolumeDirection.DOWN -> "Volume kam"
                VolumeDirection.MUTE -> "Mute"
                VolumeDirection.UNMUTE -> "Unmute"
            }
            if (percent != null) "$action diya. Ab $percent% hai." else "$action diya."
        }
        LanguageMode.ENGLISH -> {
            val action = when (direction) {
                VolumeDirection.UP -> "Volume raised"
                VolumeDirection.DOWN -> "Volume lowered"
                VolumeDirection.MUTE -> "Muted"
                VolumeDirection.UNMUTE -> "Unmuted"
            }
            if (percent != null) "$action. Now at $percent%." else "$action."
        }
    }

    fun shareOpened(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "شیئر مینو کھول دیا۔"
        LanguageMode.ROMAN_URDU -> "Share menu khol diya."
        LanguageMode.ENGLISH -> "Share menu opened."
    }

    fun copied(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "کلپ بورڈ پر کاپی کر لیا۔"
        LanguageMode.ROMAN_URDU -> "Clipboard par copy kar liya."
        LanguageMode.ENGLISH -> "Copied to clipboard."
    }

    fun remembered(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "یاد رکھ لیا۔"
        LanguageMode.ROMAN_URDU -> "Yaad rakh liya."
        LanguageMode.ENGLISH -> "Noted — I'll remember that."
    }

    fun memoryEmpty(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ابھی مجھے کچھ یاد نہیں کرنا پڑا۔"
        LanguageMode.ROMAN_URDU -> "Abhi mujhe kuch yaad karwaya nahi gaya."
        LanguageMode.ENGLISH -> "I don't have anything in memory yet. Say \"remember…\" to store a fact."
    }

    fun memoryList(facts: List<String>, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> facts.joinToString("\n") { "• $it" }
        LanguageMode.ROMAN_URDU -> facts.joinToString("\n") { "• $it" }
        LanguageMode.ENGLISH -> facts.joinToString("\n") { "• $it" }
    }

    fun contactPermissionNeeded(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "کال کے لیے رابطوں کی اجازت درکار ہے۔ Permissions اسکرین سے دیں۔"
        LanguageMode.ROMAN_URDU -> "Call ke liye contacts ki ijazat chahiye. Permissions screen se dein."
        LanguageMode.ENGLISH -> "I need Contacts permission to call by name. Grant it in Settings → Permissions."
    }

    fun contactNotFound(name: String, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "\"$name\" نام کا کوئی رابطہ نہیں ملا۔"
        LanguageMode.ROMAN_URDU -> "\"$name\" naam ka koi contact nahi mila."
        LanguageMode.ENGLISH -> "No contact named \"$name\" found."
    }

    fun webLookupFailed(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "انٹرنیٹ سروس سے جواب حاصل نہیں ہو سکا۔"
        LanguageMode.ROMAN_URDU -> "Internet service se jawab hasil nahi ho saka."
        LanguageMode.ENGLISH -> "The web service didn't answer. Check your internet and try again."
    }
}
