package com.akjarvis.app.ai

import android.content.Context
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.model.ChatTurn
import com.akjarvis.app.domain.model.LanguageMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fully offline, rule-based engine. It keeps the assistant usable with no
 * internet, no API key, and no local model installed. It understands small
 * talk and time/date questions in English, Roman Urdu and Urdu, and honestly
 * explains its limits for anything bigger.
 */
class OfflineEngine(private val context: Context) : AiEngine {

    override suspend fun respond(history: List<ChatTurn>, userText: String): String {
        val lang = IntentRouter.detectLanguage(userText)
        val text = userText.lowercase(Locale.ROOT)

        return when {
            isGreeting(text) -> pick(
                lang,
                urdu = "السلام علیکم! میں آپ کی کیا مدد کر سکتا ہوں؟",
                roman = "Assalam-o-alaikum! Main aap ki kya madad kar sakta hoon?",
                english = "Hello! How can I help you?",
            )
            isThanks(text) -> pick(
                lang,
                urdu = "کوئی بات نہیں، ہمیشہ حاضر ہوں۔",
                roman = "Koi baat nahi, hamesha hazir hoon.",
                english = "You're welcome. Always here to help.",
            )
            isHowAreYou(text) -> pick(
                lang,
                urdu = "میں بالکل ٹھیک ہوں، شکریہ! آپ کیسے ہیں؟",
                roman = "Main bilkul theek hoon, shukriya! Aap kaise hain?",
                english = "I'm doing great, thanks! How are you?",
            )
            isNameQuestion(text) -> pick(
                lang,
                urdu = "میں AK Jarvis ہوں، آپ کا ذاتی اسسٹنٹ۔",
                roman = "Main AK Jarvis hoon, aap ka personal assistant.",
                english = "I'm AK Jarvis, your personal assistant.",
            )
            Regex("\\b(time|waqt|baje|وقت)\\b").containsMatchIn(text) ->
                "It is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}"
            Regex("\\b(date|tareekh|aaj|تاریخ)\\b").containsMatchIn(text) ->
                SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            else -> pick(
                lang,
                urdu = "میں آف لائن موڈ میں ہوں، اس لیے میری صلاحیتیں محدود ہیں۔ ایپ کھولنا، یاد دہانیاں، نوٹس اور ویب سرچ ابھی بھی کام کرتے ہیں۔ مکمل گفتگو کے لیے سیٹنگز میں کلاؤڈ اے آئی کی کلید شامل کریں۔",
                roman = "Main offline mode mein hoon, is liye meri salahiyatein mehdood hain. App kholna, reminders, notes aur web search abhi bhi kaam karte hain. Mukammal guftagu ke liye Settings mein cloud AI key add karein.",
                english = "I'm running in offline mode, so my abilities are limited. Opening apps, reminders, notes and web search still work. For full conversation, add a cloud AI key in Settings.",
            )
        }
    }

    private fun isGreeting(text: String) =
        Regex("\\b(hi|hello|hey|salam|assalam|asalam|salaam|السلام)\\b").containsMatchIn(text)

    private fun isThanks(text: String) =
        Regex("\\b(thanks|thank you|shukriya|jazak|شکریہ)\\b").containsMatchIn(text)

    private fun isHowAreYou(text: String) =
        Regex("(how are you|kaise ho|kya hal|کیسے ہو|کیا حال)").containsMatchIn(text)

    private fun isNameQuestion(text: String) =
        Regex("(your name|tumhara naam|apna naam|kaun ho|تمہارا نام|کون ہو)").containsMatchIn(text)

    private fun pick(lang: LanguageMode, urdu: String, roman: String, english: String) = when (lang) {
        LanguageMode.URDU -> urdu
        LanguageMode.ROMAN_URDU -> roman
        LanguageMode.ENGLISH -> english
    }
}
