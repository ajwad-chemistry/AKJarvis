package com.akjarvis.app

import com.akjarvis.app.ai.SystemPromptBuilder
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.domain.model.LanguageMode
import com.akjarvis.app.domain.model.PersonalityStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptBuilderTest {

    @Test
    fun `prompt contains assistant name and language instruction`() {
        val prompt = SystemPromptBuilder.build(
            AppSettings(
                assistantName = "AK",
                language = LanguageMode.ROMAN_URDU,
                personalityStyle = PersonalityStyle.FUNNY,
                customInstructions = "Always mention cricket.",
            )
        )
        assertTrue(prompt.contains("AK"))
        assertTrue(prompt.contains("Roman Urdu"))
        assertTrue(prompt.contains("cricket"))
    }

    @Test
    fun `prompt forbids secret collection`() {
        val prompt = SystemPromptBuilder.build(AppSettings())
        assertTrue(prompt.contains("Never ask for passwords"))
    }
}
