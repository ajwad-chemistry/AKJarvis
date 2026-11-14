package com.akjarvis.app

import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebIntentTest {

    private val router = IntentRouter()

    @Test
    fun `weather command with city`() {
        val parsed = router.parse("weather in Karachi")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("WEATHER", (parsed as ParsedIntent.WebQuery).kind)
        assertEquals("karachi", parsed.query)
    }

    @Test
    fun `mausam without city defaults blank`() {
        val parsed = router.parse("mausam kaisa hai")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("WEATHER", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `namaz prayer times`() {
        val parsed = router.parse("namaz ke waqt Lahore")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("PRAYER", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `currency dollar rate`() {
        val parsed = router.parse("dollar rate batao")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("CURRENCY", (parsed as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `meaning of word`() {
        val parsed = router.parse("meaning of serendipity")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("MEANING", (parsed as ParsedIntent.WebQuery).kind)
        assertEquals("serendipity", (parsed as ParsedIntent.WebQuery).query)
    }

    @Test
    fun `time in city goes to web not local clock`() {
        val parsed = router.parse("time in Tokyo")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("TIMEZONE", (parsed as ParsedIntent.WebQuery).kind)
        assertEquals("tokyo", (parsed as ParsedIntent.WebQuery).query)
    }

    @Test
    fun `plain time still local`() {
        assertTrue(router.parse("kitne baje hain") is ParsedIntent.ShowTime)
    }

    @Test
    fun `research command`() {
        val parsed = router.parse("research black holes")
        assertTrue(parsed is ParsedIntent.WebQuery)
        assertEquals("RESEARCH", (parsed as ParsedIntent.WebQuery).kind)
        assertEquals("black holes", (parsed as ParsedIntent.WebQuery).query)
    }

    @Test
    fun `crypto and joke commands`() {
        assertEquals("CRYPTO", (router.parse("bitcoin price") as ParsedIntent.WebQuery).kind)
        assertEquals("JOKE", (router.parse("tell me a joke") as ParsedIntent.WebQuery).kind)
    }

    @Test
    fun `multi command splits into intents`() {
        val parsed = router.parseAll("torch on karo aur volume up")
        assertEquals(2, parsed.size)
        assertTrue(parsed[0] is ParsedIntent.Flashlight)
        assertTrue(parsed[1] is ParsedIntent.Volume)
    }

    @Test
    fun `multi command with non command stays single`() {
        val parsed = router.parseAll("search for cats and dogs")
        assertEquals(1, parsed.size)
        assertTrue(parsed[0] is ParsedIntent.WebSearch)
    }

    @Test
    fun `call by contact name`() {
        val parsed = router.parse("mama ko call karo")
        assertTrue(parsed is ParsedIntent.Dial)
        assertEquals(null, (parsed as ParsedIntent.Dial).number)
        assertEquals("mama", parsed.name)
    }

    @Test
    fun `call by number still works`() {
        val parsed = router.parse("call 03001234567")
        assertTrue(parsed is ParsedIntent.Dial)
        assertEquals("03001234567", (parsed as ParsedIntent.Dial).number)
    }
}
