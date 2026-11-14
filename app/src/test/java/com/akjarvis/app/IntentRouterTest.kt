package com.akjarvis.app

import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import com.akjarvis.app.domain.model.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    private val router = IntentRouter()

    @Test
    fun `open app command is detected`() {
        val parsed = router.parse("open whatsapp")
        assertTrue(parsed is ParsedIntent.OpenApp)
        assertEquals("whatsapp", (parsed as ParsedIntent.OpenApp).target)
    }

    @Test
    fun `roman urdu open command is detected`() {
        val parsed = router.parse("kholo youtube")
        assertTrue(parsed is ParsedIntent.OpenApp)
        assertEquals("youtube", (parsed as ParsedIntent.OpenApp).target)
    }

    @Test
    fun `wake phrase prefix is stripped`() {
        val parsed = router.parse("jarvis open settings")
        assertTrue(parsed is ParsedIntent.OpenSettings)
    }

    @Test
    fun `reminder with minutes is parsed`() {
        val parsed = router.parse("remind me to take medicine in 10 minutes")
        assertTrue(parsed is ParsedIntent.AddReminder)
        val reminder = parsed as ParsedIntent.AddReminder
        assertTrue(reminder.triggerAt != null)
        assertTrue(reminder.message.contains("medicine"))
    }

    @Test
    fun `reminder without time asks for time`() {
        val parsed = router.parse("remind me to call mom")
        assertTrue(parsed is ParsedIntent.AddReminder)
        assertEquals(null, (parsed as ParsedIntent.AddReminder).triggerAt)
    }

    @Test
    fun `note command is parsed`() {
        val parsed = router.parse("note buy milk tomorrow")
        assertTrue(parsed is ParsedIntent.AddNote)
        assertTrue((parsed as ParsedIntent.AddNote).text.contains("buy milk"))
    }

    @Test
    fun `search command is parsed`() {
        val parsed = router.parse("search for weather in Karachi")
        assertTrue(parsed is ParsedIntent.WebSearch)
        assertEquals("weather in karachi", (parsed as ParsedIntent.WebSearch).query)
    }

    @Test
    fun `call only opens dialer`() {
        val parsed = router.parse("call 03001234567")
        assertEquals("03001234567", (parsed as ParsedIntent.Dial).number)
    }

    @Test
    fun `plain question is not a device command`() {
        assertEquals(ParsedIntent.None, router.parse("What is the capital of France?"))
    }

    @Test
    fun `language detection works`() {
        assertEquals(LanguageMode.URDU, IntentRouter.detectLanguage("آپ کیسے ہیں"))
        assertEquals(LanguageMode.ROMAN_URDU, IntentRouter.detectLanguage("aap kaise hain"))
        assertEquals(LanguageMode.ENGLISH, IntentRouter.detectLanguage("How are you doing today friend"))
    }
}
