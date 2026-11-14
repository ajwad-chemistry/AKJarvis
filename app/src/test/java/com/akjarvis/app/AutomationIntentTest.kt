package com.akjarvis.app

import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.MediaCommand
import com.akjarvis.app.domain.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationIntentTest {

    private val router = IntentRouter()

    @Test
    fun `go back in english`() {
        assertEquals(ParsedIntent.GoBack, router.parse("go back"))
    }

    @Test
    fun `wapas jao in roman urdu`() {
        assertEquals(ParsedIntent.GoBack, router.parse("wapas jao"))
    }

    @Test
    fun `go home`() {
        assertEquals(ParsedIntent.GoHome, router.parse("go home"))
    }

    @Test
    fun `home screen`() {
        assertEquals(ParsedIntent.GoHome, router.parse("home screen par jao"))
    }

    @Test
    fun `recent apps`() {
        assertEquals(ParsedIntent.OpenRecents, router.parse("recent apps dikhao"))
    }

    @Test
    fun `notification panel`() {
        assertEquals(ParsedIntent.OpenNotificationShade, router.parse("notification panel kholo"))
    }

    @Test
    fun `read screen`() {
        assertEquals(ParsedIntent.ReadScreen, router.parse("read my screen"))
    }

    @Test
    fun `type text`() {
        val parsed = router.parse("type hello world")
        assertTrue(parsed is ParsedIntent.TypeText)
        assertEquals("hello world", (parsed as ParsedIntent.TypeText).text)
    }

    @Test
    fun `likho roman urdu types text`() {
        val parsed = router.parse("likho salam bhai")
        assertTrue(parsed is ParsedIntent.TypeText)
        assertEquals("salam bhai", (parsed as ParsedIntent.TypeText).text)
    }

    @Test
    fun `scroll down default`() {
        val parsed = router.parse("scroll karo")
        assertTrue(parsed is ParsedIntent.Scroll)
        assertEquals(false, (parsed as ParsedIntent.Scroll).up)
    }

    @Test
    fun `scroll up`() {
        val parsed = router.parse("scroll upar karo")
        assertTrue(parsed is ParsedIntent.Scroll)
        assertEquals(true, (parsed as ParsedIntent.Scroll).up)
    }

    @Test
    fun `tap on target`() {
        val parsed = router.parse("tap on settings button")
        assertTrue(parsed is ParsedIntent.TapOn)
        assertEquals("settings button", (parsed as ParsedIntent.TapOn).target)
    }

    @Test
    fun `play music toggles media`() {
        assertEquals(ParsedIntent.MediaControl(MediaCommand.PLAY_PAUSE), router.parse("gaana chalao"))
        assertEquals(ParsedIntent.MediaControl(MediaCommand.PLAY_PAUSE), router.parse("play music"))
    }

    @Test
    fun `next and previous track`() {
        assertEquals(ParsedIntent.MediaControl(MediaCommand.NEXT), router.parse("next song"))
        assertEquals(ParsedIntent.MediaControl(MediaCommand.PREVIOUS), router.parse("pichla gaana"))
    }

    @Test
    fun `termux command parsed with args`() {
        val parsed = router.parse("termux termux-battery-status")
        assertTrue(parsed is ParsedIntent.TermuxCommand)
        assertEquals(listOf("termux-battery-status"), (parsed as ParsedIntent.TermuxCommand).args)
    }

    @Test
    fun `tavily search query`() {
        val parsed = router.parse("tavily best budget phone 2025")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("TAVILY", (parsed as ParsedIntent.WebQuery).kind)
        assertEquals("best budget phone 2025", parsed.query)
    }

    @Test
    fun `windy forecast query`() {
        val parsed = router.parse("windy forecast Murree")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("WINDY", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `nearby places query`() {
        val parsed = router.parse("nearby restaurant in Karachi")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("NEARBY", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `qareeb masjid roman urdu nearby`() {
        val parsed = router.parse("qareeb masjid")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("NEARBY", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `read notifications still works`() {
        assertEquals(ParsedIntent.ReadNotifications, router.parse("read my notifications"))
    }
}
