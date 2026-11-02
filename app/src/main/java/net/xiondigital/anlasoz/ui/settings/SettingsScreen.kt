package net.xiondigital.anlasoz.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.BuildConfig
import net.xiondigital.anlasoz.ai.ProviderPresets
import net.xiondigital.anlasoz.audio.SpeechController
import net.xiondigital.anlasoz.data.model.ReplyStyle
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.BrandTopBar
import net.xiondigital.anlasoz.ui.components.ElementTile
import net.xiondigital.anlasoz.ui.components.FilterPill
import net.xiondigital.anlasoz.ui.components.Hairline
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.InlineTextField
import net.xiondigital.anlasoz.ui.components.MetaText
import net.xiondigital.anlasoz.ui.components.PrimaryButton
import net.xiondigital.anlasoz.ui.components.QuietTextButton
import net.xiondigital.anlasoz.ui.components.ScreenTitle
import net.xiondigital.anlasoz.ui.components.SecondaryButton
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Xion
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshKeyState()
        onPauseOrDispose { }
    }

    fun openTtsSettings() {
        try {
            context.startActivity(SpeechController.ttsSettingsIntent())
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    fun installVoiceData() {
        try {
            context.startActivity(SpeechController.installVoiceDataIntent())
        } catch (e: ActivityNotFoundException) {
            openTtsSettings()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BrandTopBar(
                actions = {
                    IconAction(
                        icon = Icons.Outlined.ArrowBack,
                        description = "Back",
                        onClick = onBack,
                    )
                },
            )
        }
        item {
            ScreenTitle(
                title = "Settings",
                subtitle = "Model, speech, context and privacy",
            )
        }

        // ---- model -------------------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Model")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Any OpenAI-compatible endpoint",
                    style = MaterialTheme.typography.titleLarge,
                    color = Xion.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The app speaks the chat-completions shape only, so the provider can change without touching the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Muted,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderPresets.all.forEach { preset ->
                        FilterPill(
                            label = preset.name,
                            selected = state.presetId == preset.id,
                            onClick = { viewModel.selectPreset(preset) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.preset.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("Base URL", color = Xion.MutedDim)
                Spacer(Modifier.height(6.dp))
                InlineTextField(
                    value = state.baseUrlDraft,
                    onValueChange = viewModel::setBaseUrlDraft,
                    placeholder = "https://api.example.com/v1",
                    keyboardType = KeyboardType.Uri,
                )
                if (state.baseUrlDraft.trim().startsWith("http://")) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Plain HTTP is unencrypted. Keep it for a model on your own machine; use HTTPS for anything remote.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Warn,
                    )
                }
                Spacer(Modifier.height(10.dp))
                SectionLabel("Model", color = Xion.MutedDim)
                Spacer(Modifier.height(6.dp))
                InlineTextField(
                    value = state.modelDraft,
                    onValueChange = viewModel::setModelDraft,
                    placeholder = "model-name",
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Save",
                        onClick = viewModel::saveProvider,
                        enabled = state.dirty,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Test",
                        onClick = viewModel::testConnection,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ConnectionResult(state.testing)

                Spacer(Modifier.height(16.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))

                SectionLabel("API key", color = Xion.MutedDim)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (state.hasKey) Xion.Ok else Xion.MutedDim),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = when {
                            !state.hasKey -> "No key stored"
                            state.keyEncrypted -> "Key stored · Android Keystore"
                            else -> "Key stored · app-private storage"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.hasKey) Xion.Body else Xion.Muted,
                    )
                }
                if (!state.keyEncrypted && state.hasKey) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "This device could not create a Keystore master key, so the key lives in app-private preferences instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Warn,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineTextField(
                        value = state.keyDraft,
                        onValueChange = viewModel::setKeyDraft,
                        placeholder = if (state.hasKey) "Paste a new key to replace" else "Paste your API key",
                        masked = !state.keyVisible,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Password,
                    )
                    IconAction(
                        icon = if (state.keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        description = if (state.keyVisible) "Hide key" else "Show key",
                        onClick = viewModel::toggleKeyVisible,
                        tint = Xion.Muted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(text = "Save key", onClick = viewModel::saveKey)
                    if (state.hasKey) {
                        QuietTextButton(
                            text = "Remove key",
                            onClick = viewModel::clearKey,
                            color = Xion.Danger,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The key never ships inside the APK and is not included in backups. For a shared or production build, put a small proxy in front of the provider and point the base URL at it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
            }
        }

        // ---- context -----------------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Conversation context")
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Use the conversation",
                    description = "Earlier messages in the active thread are sent with each request so short replies like “Ben de” can be read correctly.",
                    checked = state.contextEnabled,
                    onCheckedChange = viewModel::setContextEnabled,
                )
                Spacer(Modifier.height(10.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = "Messages remembered",
                    value = state.contextLimit.toFloat(),
                    range = 0f..12f,
                    steps = 11,
                    enabled = state.contextEnabled,
                    valueLabel = "${state.contextLimit}",
                    onValueChange = { viewModel.setContextLimit(it.toInt()) },
                )
                MetaText("Only the messages in the selected conversation are used. Clearing a thread removes that context.")
            }
        }

        // ---- speech ------------------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Turkish speech")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.tts.turkishAvailable) {
                            Icons.Outlined.Check
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (state.tts.turkishAvailable) Xion.Ok else Xion.Warn,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !state.tts.ready -> "Preparing the speech engine…"
                            state.tts.turkishAvailable -> "Turkish voice ready (tr-TR)"
                            else -> "No Turkish voice installed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Xion.Ink,
                    )
                }
                if (state.tts.engineName != null) {
                    Spacer(Modifier.height(4.dp))
                    MetaText("Engine: ${state.tts.engineName}", color = Xion.MutedDim)
                }
                if (!state.tts.turkishAvailable && state.tts.ready) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "AnlaSöz will not read Turkish with an English voice. Install Turkish voice data, then test again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Warn,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(text = "Install voice data", onClick = { installVoiceData() })
                        SecondaryButton(text = "Voice settings", onClick = { openTtsSettings() })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SliderRow(
                    label = "Speed",
                    value = state.ttsSpeed,
                    range = 0.5f..1.5f,
                    steps = 9,
                    valueLabel = String.format(Locale.US, "%.2f×", state.ttsSpeed),
                    onValueChange = viewModel::setTtsSpeed,
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "Speak new results automatically",
                    description = "Play the Turkish text as soon as an analysis or reply arrives.",
                    checked = state.ttsAutoPlay,
                    onCheckedChange = viewModel::setTtsAutoPlay,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(text = "Hear a sample", onClick = viewModel::testVoice)
                    if (state.tts.speaking) {
                        QuietTextButton(text = "Stop", onClick = viewModel::stopVoice)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Analyze after dictation",
                    description = "When a transcription finishes, run the analysis immediately.",
                    checked = state.autoTranslateTranscript,
                    onCheckedChange = viewModel::setAutoTranslateTranscript,
                )
            }
        }

        // ---- reply defaults ----------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Default reply tone")
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReplyStyle.entries.forEach { style ->
                        FilterPill(
                            label = style.label,
                            selected = state.defaultStyle == style,
                            onClick = { viewModel.setDefaultStyle(style) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                MetaText(state.defaultStyle.instruction, color = Xion.MutedDim)
            }
        }

        // ---- privacy -----------------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Privacy")
                Spacer(Modifier.height(10.dp))
                PrivacyLine("Keys", "Encrypted with the Android Keystore, excluded from backups, never logged.")
                PrivacyLine("History", "Stored in a local SQLite database. Nothing is uploaded except the text you choose to analyze.")
                PrivacyLine("Audio", "The microphone only opens when you tap it. No background recording.")
                PrivacyLine("Analytics", "None. No tracking SDKs, no crash uploads.")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Clear all data",
                        onClick = { confirmClear = true },
                    )
                }
                Spacer(Modifier.height(8.dp))
                MetaText(
                    "Removes history, vocabulary, grammar notes and the stored key from this device.",
                    color = Xion.MutedDim,
                )
            }
        }

        // ---- about -------------------------------------------------------------
        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ElementTile(size = 196.dp, full = true)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "AnlaSöz",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Xion.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "ELEMENT x07 · TURKISH LANGUAGE SYSTEM",
                        style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.4.sp),
                        color = Xion.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.MutedDim,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Visual identity from xiondigital.net. Element mark in the Kryvex tile grammar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.MutedDim,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    PaletteRow()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Xion.Panel,
            title = { Text("Clear everything?", color = Xion.Ink) },
            text = {
                Text(
                    "This deletes all conversations, saved words, grammar notes and the stored API key. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xion.Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearAllData()
                    },
                ) { Text("Delete", color = Xion.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = Xion.Muted)
                }
            },
        )
    }

}

@Composable
private fun ConnectionResult(test: ConnectionTest) {
    when (test) {
        ConnectionTest.Idle -> Unit
        ConnectionTest.Testing -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Testing the endpoint…",
                style = MaterialTheme.typography.bodySmall,
                color = Xion.Muted,
            )
        }
        is ConnectionTest.Success -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Xion.Ok,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = test.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Ok,
                )
            }
        }
        is ConnectionTest.Failed -> {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Xion.Danger,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = test.headline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Danger,
                    )
                    Text(
                        text = test.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Xion.Ink)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Xion.Muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Xion.Blue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Xion.Muted,
                uncheckedTrackColor = Xion.PanelHi,
                uncheckedBorderColor = Xion.LineStrong,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(label, color = Xion.MutedDim, modifier = Modifier.weight(1f))
            Text(
                text = valueLabel,
                style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                color = if (enabled) Xion.BlueHi else Xion.MutedDim,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Xion.BlueHi,
                activeTrackColor = Xion.Blue,
                inactiveTrackColor = Xion.PanelHi,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Xion.MutedDim,
                disabledActiveTrackColor = Xion.LineStrong,
                disabledInactiveTrackColor = Xion.PanelHi,
            ),
        )
    }
}

@Composable
private fun PrivacyLine(title: String, body: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Xion.Blue),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title.uppercase(),
                style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.1.sp),
                color = Xion.Muted,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Xion.Body,
            )
        }
    }
}

@Composable
private fun PaletteRow() {
    val swatches = listOf(
        "BG" to Xion.Bg,
        "PANEL" to Xion.Panel,
        "LINE" to Xion.LineStrong,
        "INK" to Xion.Ink,
        "BLUE" to Xion.Blue,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                        .border(1.dp, Xion.Line, RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = name,
                    style = MonoLabel.copy(fontSize = 7.5.sp, letterSpacing = 0.8.sp),
                    color = Xion.MutedDim,
                )
            }
        }
    }
}
