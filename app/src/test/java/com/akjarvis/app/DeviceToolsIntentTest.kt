package com.akjarvis.app

import com.akjarvis.app.ai.AiProvider
import com.akjarvis.app.ai.ProviderKind
import com.akjarvis.app.ai.SystemPromptBuilder
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.domain.IntentRouter
import com.akjarvis.app.domain.ParsedIntent
import com.akjarvis.app.domain.VolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolsIntentTest {

    private val router = IntentRouter()

    @Test
    fun `flashlight on is detected`() {
        val parsed = router.parse("flashlight on karo")
        assertTrue(parsed is ParsedIntent.Flashlight)
        assertEquals(true, (parsed as ParsedIntent.Flashlight).on)
    }

    @Test
    fun `torch off is detected`() {
        val parsed = router.parse("torch band karo")
        assertTrue(parsed is ParsedIntent.Flashlight)
        assertEquals(false, (parsed as ParsedIntent.Flashlight).on)
    }

    @Test
    fun `bare torch toggles`() {
        val parsed = router.parse("torch")
        assertTrue(parsed is ParsedIntent.Flashlight)
        assertEquals(null, (parsed as ParsedIntent.Flashlight).on)
    }

    @Test
    fun `battery status is detected before settings`() {
        val parsed = router.parse("battery status check karo")
        assertTrue(parsed is ParsedIntent.BatteryStatus)
    }

    @Test
    fun `battery settings still opens settings`() {
        val parsed = router.parse("open battery settings")
        assertTrue(parsed is ParsedIntent.OpenSettings)
        assertEquals("battery", (parsed as ParsedIntent.OpenSettings).target)
    }

    @Test
    fun `volume up and down are detected`() {
        assertEquals(VolumeDirection.UP, (router.parse("volume up") as ParsedIntent.Volume).direction)
        assertEquals(VolumeDirection.DOWN, (router.parse("awaz kam karo") as ParsedIntent.Volume).direction)
        assertEquals(VolumeDirection.MUTE, (router.parse("mute karo") as ParsedIntent.Volume).direction)
    }

    @Test
    fun `volume settings opens sound settings`() {
        val parsed = router.parse("open volume settings")
        assertTrue(parsed is ParsedIntent.OpenSettings)
        assertEquals("sound", (parsed as ParsedIntent.OpenSettings).target)
    }

    @Test
    fun `share and copy commands are detected`() {
        assertTrue(router.parse("share hello world") is ParsedIntent.ShareText)
        val copied = router.parse("copy meeting at 5 pm")
        assertTrue(copied is ParsedIntent.CopyText)
        assertEquals("meeting at 5 pm", (copied as ParsedIntent.CopyText).text)
    }

    @Test
    fun `remember stores fact and bare remember recalls`() {
        val remember = router.parse("remember my locker code is 4821")
        assertTrue(remember is ParsedIntent.Remember)
        assertEquals("my locker code is 4821", (remember as ParsedIntent.Remember).fact)
        assertTrue(router.parse("what do you remember") is ParsedIntent.RecallMemory)
    }

    @Test
    fun `provider defaults point at free tier endpoints`() {
        assertEquals("https://api.z.ai/api/paas/v4/", AiProvider.Z_AI.defaultBaseUrl)
        assertEquals("glm-4.5-flash", AiProvider.Z_AI.defaultModel)
        assertEquals("https://api.groq.com/openai/v1/", AiProvider.GROQ.defaultBaseUrl)
        assertEquals("https://openrouter.ai/api/v1/", AiProvider.OPENROUTER.defaultBaseUrl)
        assertTrue(AiProvider.OPENROUTER.defaultModel.endsWith(":free"))
        assertEquals(ProviderKind.ANTHROPIC, AiProvider.ANTHROPIC.kind)
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, AiProvider.GEMINI.kind)
    }

    @Test
    fun `system prompt includes memory facts`() {
        val prompt = SystemPromptBuilder.build(AppSettings(), listOf("user likes chai", "locker code 4821"))
        assertTrue(prompt.contains("user likes chai"))
        assertTrue(prompt.contains("locker code 4821"))
    }
}
