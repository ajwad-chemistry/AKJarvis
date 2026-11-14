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
import com.akjarvis.app.domain.MediaCommand
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
    private val tavilyApiKeyProvider: suspend () -> String = { "" },
    private val windyApiKeyProvider: suspend () -> String = { "" },
    private val geoapifyApiKeyProvider: suspend () -> String = { "" },
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
        ParsedIntent.GoBack -> "back"
        ParsedIntent.GoHome -> "home"
        ParsedIntent.OpenRecents -> "recents"
        ParsedIntent.OpenNotificationShade -> "notification shade"
        ParsedIntent.ReadScreen -> "read screen"
        is ParsedIntent.TypeText -> "type"
        is ParsedIntent.Scroll -> "scroll"
        is ParsedIntent.TapOn -> "tap ${intent.target}"
        is ParsedIntent.MediaControl -> "media ${intent.action.name.lowercase()}"
        is ParsedIntent.TermuxCommand -> "termux"
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
            WebTools.dispatch(
                intent.kind,
                intent.query,
                cricketApiKeyProvider(),
                tavilyApiKeyProvider(),
                windyApiKeyProvider(),
                geoapifyApiKeyProvider(),
            ) ?: PhraseBook.webLookupFailed(language)
        ParsedIntent.GoBack -> automationAction(language) { it.goBack() }
        ParsedIntent.GoHome -> automationAction(language) { it.goHome() }
        ParsedIntent.OpenRecents -> automationAction(language) { it.openRecents() }
        ParsedIntent.OpenNotificationShade -> automationAction(language) { it.openNotificationShade() }
        ParsedIntent.ReadScreen -> {
            val service = AutomationService.instance
            if (service == null || !AutomationService.hasAccess(context)) {
                PhraseBook.automationNeeded(language)
            } else {
                service.readScreenText()?.take(1200)?.let { screenText ->
                    PhraseBook.screenHeader(language) + "\n" + screenText
                } ?: PhraseBook.screenEmpty(language)
            }
        }
        is ParsedIntent.TypeText -> {
            val service = AutomationService.instance
            if (service == null || !AutomationService.hasAccess(context)) {
                PhraseBook.automationNeeded(language)
            } else {
                val ok = runCatching { service.typeText(intent.text) }.getOrDefault(false)
                PhraseBook.typed(ok, language)
            }
        }
        is ParsedIntent.Scroll -> {
            val service = AutomationService.instance
            if (service == null || !AutomationService.hasAccess(context)) {
                PhraseBook.automationNeeded(language)
            } else {
                val ok = runCatching { service.scroll(intent.up) }.getOrDefault(false)
                PhraseBook.scrolled(intent.up, ok, language)
            }
        }
        is ParsedIntent.TapOn -> {
            val service = AutomationService.instance
            if (service == null || !AutomationService.hasAccess(context)) {
                PhraseBook.automationNeeded(language)
            } else {
                val ok = runCatching { service.tapOn(intent.target) }.getOrDefault(false)
                PhraseBook.tapped(intent.target, ok, language)
            }
        }
        is ParsedIntent.MediaControl -> {
            val ok = DeviceTools.mediaControl(
                context,
                when (intent.action) {
                    MediaCommand.PLAY_PAUSE -> DeviceTools.MediaAction.PLAY_PAUSE
                    MediaCommand.NEXT -> DeviceTools.MediaAction.NEXT
                    MediaCommand.PREVIOUS -> DeviceTools.MediaAction.PREVIOUS
                },
            )
            if (ok) PhraseBook.mediaDone(intent.action, language) else null
        }
        is ParsedIntent.TermuxCommand -> when (TermuxBridge.run(context, intent.args)) {
            TermuxBridge.Result.Sent -> PhraseBook.termuxSent(language)
            TermuxBridge.Result.TermuxMissing -> PhraseBook.termuxMissing(language)
            TermuxBridge.Result.PermissionMissing -> PhraseBook.termuxPermission(language)
            TermuxBridge.Result.Blocked -> PhraseBook.termuxBlocked(language)
        }
        ParsedIntent.Help -> PhraseBook.help(language)
        ParsedIntent.None -> null
        }
    }

    /** Runs an accessibility action, or explains how to enable mobile control. */
    private fun automationAction(language: LanguageMode, action: (AutomationService) -> Boolean): String {
        val service = AutomationService.instance
        if (service == null || !AutomationService.hasAccess(context)) {
            return PhraseBook.automationNeeded(language)
        }
        val ok = runCatching { action(service) }.getOrDefault(false)
        return if (ok) PhraseBook.automationDone(language) else PhraseBook.automationFailed(language)
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

    fun automationNeeded(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "موبائل کنٹرول بند ہے۔ سیٹنگز ← پرمیشنز ← موبائل کنٹرول سے آن کریں۔"
        LanguageMode.ROMAN_URDU -> "Mobile control abhi off hai. Settings → Permissions → Mobile Control se enable karein."
        LanguageMode.ENGLISH -> "Mobile control is off. Enable it in Settings → Permissions → Mobile Control."
    }

    fun automationDone(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ہو گیا۔"
        LanguageMode.ROMAN_URDU -> "Ho gaya."
        LanguageMode.ENGLISH -> "Done."
    }

    fun automationFailed(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "یہ ایکشن اس اسکرین پر ممکن نہیں۔"
        LanguageMode.ROMAN_URDU -> "Ye action is screen par mumkin nahi."
        LanguageMode.ENGLISH -> "That action isn't possible on this screen."
    }

    fun screenHeader(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "اسکرین پر لکھا ہے:"
        LanguageMode.ROMAN_URDU -> "Screen par likha hai:"
        LanguageMode.ENGLISH -> "On your screen:"
    }

    fun screenEmpty(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "اسکرین پر پڑھنے کے قابل کچھ نہیں ملا۔"
        LanguageMode.ROMAN_URDU -> "Screen par parhne layak kuch nahi mila."
        LanguageMode.ENGLISH -> "I couldn't find readable text on this screen."
    }

    fun typed(ok: Boolean, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> if (ok) "ٹائپ کر دیا۔" else "کوئی ٹیکسٹ فیلڈ نہیں ملی۔"
        LanguageMode.ROMAN_URDU -> if (ok) "Type kar diya." else "Koi text field nahi mili jahan type karoon."
        LanguageMode.ENGLISH -> if (ok) "Typed it in." else "No text field found to type into."
    }

    fun tapped(target: String, ok: Boolean, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> if (ok) "\"$target\" پر ٹیپ کر دیا۔" else "\"$target\" اسکرین پر نہیں ملا۔"
        LanguageMode.ROMAN_URDU -> if (ok) "\"$target\" par tap kar diya." else "\"$target\" screen par nahi mila."
        LanguageMode.ENGLISH -> if (ok) "Tapped \"$target\"." else "Couldn't find \"$target\" on screen."
    }

    fun scrolled(up: Boolean, ok: Boolean, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> if (ok) (if (up) "اوپر اسکرول کیا۔" else "نیچے اسکرول کیا۔") else "اسکرول ممکن نہیں۔"
        LanguageMode.ROMAN_URDU -> if (ok) (if (up) "Scroll up kar diya." else "Scroll down kar diya.") else "Scroll mumkin nahi."
        LanguageMode.ENGLISH -> if (ok) (if (up) "Scrolled up." else "Scrolled down.") else "Scroll failed."
    }

    fun mediaDone(action: MediaCommand, lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> when (action) {
            MediaCommand.PLAY_PAUSE -> "میڈیا پلے/پاز کیا۔"
            MediaCommand.NEXT -> "اگلا ٹریک۔"
            MediaCommand.PREVIOUS -> "پچھلا ٹریک۔"
        }
        LanguageMode.ROMAN_URDU -> when (action) {
            MediaCommand.PLAY_PAUSE -> "Media play/pause kar diya."
            MediaCommand.NEXT -> "Agla track chala diya."
            MediaCommand.PREVIOUS -> "Pichla track chala diya."
        }
        LanguageMode.ENGLISH -> when (action) {
            MediaCommand.PLAY_PAUSE -> "Media play/pause toggled."
            MediaCommand.NEXT -> "Skipped to the next track."
            MediaCommand.PREVIOUS -> "Went to the previous track."
        }
    }

    fun termuxSent(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ٹرمکس کو کمانڈ بھیج دی۔"
        LanguageMode.ROMAN_URDU -> "Termux ko command bhej di."
        LanguageMode.ENGLISH -> "Command forwarded to Termux."
    }

    fun termuxMissing(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ٹرمکس ایپ انسٹال نہیں ہے۔ F-Droid سے Termux اور Termux:API انسٹال کریں۔"
        LanguageMode.ROMAN_URDU -> "Termux app install nahi hai. F-Droid se Termux aur Termux:API install karein."
        LanguageMode.ENGLISH -> "Termux isn't installed. Install Termux and Termux:API from F-Droid."
    }

    fun termuxPermission(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "ٹرمکس کی RUN_COMMAND اجازت نہیں ہے۔ سیٹنگز ← پرمیشنز میں دیں۔"
        LanguageMode.ROMAN_URDU -> "Termux ki RUN_COMMAND ijazat nahi hai. Settings → Permissions mein grant karein."
        LanguageMode.ENGLISH -> "The Termux RUN_COMMAND permission isn't granted. Enable it in Settings → Permissions."
    }

    fun termuxBlocked(lang: LanguageMode) = when (lang) {
        LanguageMode.URDU -> "یہ ٹرمکس کمانڈ اجازت شدہ نہیں۔ صرف محفوظ termux-api کمانڈز چلتی ہیں۔"
        LanguageMode.ROMAN_URDU -> "Ye Termux command allow nahi hai. Sirf safe termux-api commands chalti hain."
        LanguageMode.ENGLISH -> "That Termux command isn't on the safe allow-list. Only read-only termux-api tools run."
    }
}
