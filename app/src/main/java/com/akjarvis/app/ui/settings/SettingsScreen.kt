package com.akjarvis.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akjarvis.app.ai.AiProvider
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.domain.model.AiMode
import com.akjarvis.app.domain.model.LanguageMode
import com.akjarvis.app.domain.model.PersonalityStyle
import com.akjarvis.app.domain.model.ThemeMode
import com.akjarvis.app.voice.WakeWordService

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setWakeWordEnabled(granted)
        if (granted) WakeWordService.start(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        SectionTitle("AI mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.aiMode == AiMode.CLOUD,
                onClick = { viewModel.setAiMode(AiMode.CLOUD) },
                label = { Text("☁ Cloud AI") },
            )
            FilterChip(
                selected = settings.aiMode == AiMode.FALLBACK,
                onClick = { viewModel.setAiMode(AiMode.FALLBACK) },
                label = { Text("⚡ Offline") },
            )
        }
        Text(
            text = "Cloud AI uses your chosen provider's API. If the cloud fails, the assistant automatically falls back to offline mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionTitle("AI provider")
        ProviderChips(
            selected = settings.provider,
            onSelect = viewModel::setProvider,
        )
        Text(
            text = settings.provider.freeTierNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionTitle("Cloud AI configuration")
        SecretTextField(
            label = "API key",
            value = settings.apiKey,
            onValueChange = viewModel::setApiKey,
        )
        LabeledTextField(
            label = "API base URL",
            value = settings.apiBaseUrl,
            onValueChange = viewModel::setApiBaseUrl,
        )
        LabeledTextField(
            label = "Model",
            value = settings.model,
            onValueChange = viewModel::setModel,
        )
        LabeledTextField(
            label = "Cricket API key (optional — free at cricapi.com)",
            value = settings.cricketApiKey,
            onValueChange = viewModel::setCricketApiKey,
        )

        SectionTitle("Personality")
        LabeledTextField(
            label = "Assistant name",
            value = settings.assistantName,
            onValueChange = viewModel::setAssistantName,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonalityStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.personalityStyle == style,
                    onClick = { viewModel.setPersonalityStyle(style) },
                    label = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        LabeledTextField(
            label = "Custom instructions (optional)",
            value = settings.customInstructions,
            onValueChange = viewModel::setCustomInstructions,
            maxLines = 3,
        )

        SectionTitle("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageMode.entries.forEach { language ->
                FilterChip(
                    selected = settings.language == language,
                    onClick = { viewModel.setLanguage(language) },
                    label = {
                        Text(
                            when (language) {
                                LanguageMode.ENGLISH -> "English"
                                LanguageMode.URDU -> "اردو"
                                LanguageMode.ROMAN_URDU -> "Roman Urdu"
                            }
                        )
                    },
                )
            }
        }

        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { viewModel.setTheme(theme) },
                    label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        SectionTitle("Voice")
        SettingSwitch(
            title = "Speak replies aloud",
            checked = settings.speakReplies,
            onCheckedChange = viewModel::setSpeakReplies,
        )
        SettingSwitch(
            title = "Hands-free follow-up",
            subtitle = "After a spoken reply, keeps the mic open for 7 seconds so you can answer without tapping. Microphone is active only in that window.",
            checked = settings.handsFree,
            onCheckedChange = viewModel::setHandsFree,
        )
        SettingSwitch(
            title = "Wake word detection",
            subtitle = "Listens in the background for your wake phrase. Uses more battery.",
            checked = settings.wakeWordEnabled,
            onCheckedChange = { enabled ->
                val micGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (enabled && !micGranted) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.setWakeWordEnabled(enabled)
                    if (enabled) WakeWordService.start(context) else WakeWordService.stop(context)
                }
            },
        )
        if (settings.wakeWordEnabled) {
            LabeledTextField(
                label = "Wake phrase",
                value = settings.wakePhrase,
                onValueChange = viewModel::setWakePhrase,
            )
        }

        SectionTitle("Data & privacy")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenPermissions) { Text("Permissions") }
            OutlinedButton(onClick = viewModel::clearChat) { Text("Clear chat history") }
            OutlinedButton(onClick = viewModel::clearMemory) { Text("Clear memory") }
        }

        SectionTitle("About")
        updateInfo?.let { update ->
            Text(
                text = "⬆ Update available: v${update.versionName} — ${update.notes}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = "AK Jarvis v1.2.0 — hybrid AI assistant (Gemini / Claude / Groq / OpenRouter / Z.ai / custom + offline mode) with 30+ built-in web skills. Your API keys stay on this device and are never included in backups. The app never records audio silently, never reads your passwords or OTPs, and never sends messages or calls without your confirmation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ProviderChips(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiProvider.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { provider ->
                    FilterChip(
                        selected = selected == provider,
                        onClick = { onSelect(provider) },
                        label = { Text(provider.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = maxLines == 1,
        maxLines = maxLines,
    )
}

@Composable
private fun SecretTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide" else "Show")
            }
        },
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
