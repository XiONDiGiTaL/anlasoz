package net.xiondigital.anlasoz.ui.reply

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.audio.SttController
import net.xiondigital.anlasoz.data.model.ReplyStyle
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.BrandTopBar
import net.xiondigital.anlasoz.ui.components.CopyButton
import net.xiondigital.anlasoz.ui.components.DirectionPill
import net.xiondigital.anlasoz.ui.components.FailureCard
import net.xiondigital.anlasoz.ui.components.FilterPill
import net.xiondigital.anlasoz.ui.components.Hairline
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.LoadingAnalysisSkeleton
import net.xiondigital.anlasoz.ui.components.MessageField
import net.xiondigital.anlasoz.ui.components.MetaText
import net.xiondigital.anlasoz.ui.components.PrimaryButton
import net.xiondigital.anlasoz.ui.components.ReplySuggestions
import net.xiondigital.anlasoz.ui.components.ScreenTitle
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.components.SetupCard
import net.xiondigital.anlasoz.ui.components.SpeakerButton
import net.xiondigital.anlasoz.ui.components.StopSpeechButton
import net.xiondigital.anlasoz.ui.components.ToneChip
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Xion
import net.xiondigital.anlasoz.ui.translate.TranslateStatus
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyScreen(
    viewModel: ReplyViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val context = LocalContext.current

    val stt = remember { SttController(context) }
    val sttState by stt.state.collectAsStateWithLifecycle()
    var partial by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { stt.destroy() } }
    SideEffect {
        stt.onTranscript = { text, isFinal ->
            if (isFinal) {
                partial = ""
                viewModel.onInputChange(text)
            } else {
                partial = text
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshConfig() }
    LifecycleResumeEffect(Unit) {
        scope.launch { viewModel.refreshConfig() }
        onPauseOrDispose { }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val listen = {
        stt.updateLocale(Locale.US)
        stt.start()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen() else notify("Microphone permission is required for dictation")
    }
    val onMic = {
        when {
            sttState.listening -> stt.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> listen()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BrandTopBar(
                actions = {
                    IconAction(
                        icon = Icons.Outlined.Settings,
                        description = "Settings",
                        onClick = onOpenSettings,
                    )
                },
            )
        }
        item {
            ScreenTitle(
                title = "Write in Turkish",
                subtitle = "English in — Turkish a native speaker would actually send",
            )
        }

        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("What you want to say")
                    Spacer(Modifier.weight(1f))
                    DirectionPill("EN → TR")
                }
                Spacer(Modifier.height(10.dp))
                MessageField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = "I'll be at the office until six. Let me know when you're free.",
                    enabled = state.status != TranslateStatus.LOADING,
                )
                Spacer(Modifier.height(8.dp))
                Hairline()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(
                        icon = if (sttState.listening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        description = if (sttState.listening) "Stop dictation" else "Dictate in English",
                        onClick = onMic,
                        tint = if (sttState.listening) Xion.BlueHi else Xion.Body,
                    )
                    IconAction(
                        icon = Icons.Outlined.Close,
                        description = "Clear",
                        onClick = viewModel::clear,
                        enabled = state.input.isNotEmpty(),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (sttState.listening) "LISTENING…" else "MIC DICTATES IN ENGLISH",
                        style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.2.sp),
                        color = if (sttState.listening) Xion.BlueHi else Xion.MutedDim,
                    )
                }
                if (partial.isNotBlank()) {
                    Text(
                        text = partial,
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Muted,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Tone", Modifier.padding(bottom = 8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReplyStyle.entries.forEach { style ->
                        FilterPill(
                            label = style.label,
                            selected = state.style == style,
                            onClick = { viewModel.selectStyle(style) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.style.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(
                    text = if (state.status == TranslateStatus.LOADING) "Composing" else "Generate Turkish",
                    onClick = viewModel::generate,
                    loading = state.status == TranslateStatus.LOADING,
                    icon = Icons.Outlined.Send,
                )
            }
        }

        when (state.status) {
            TranslateStatus.SETUP -> item {
                SetupCard(
                    onOpenSettings = onOpenSettings,
                    body = "Reply generation uses the same model as translation. Add a key and model name in Settings once.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            TranslateStatus.ERROR -> item {
                FailureCard(
                    failure = state.failure ?: net.xiondigital.anlasoz.ai.AiFailure.UNKNOWN,
                    detail = state.failureDetail,
                    onRetry = viewModel::retry,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            TranslateStatus.LOADING -> item {
                LoadingAnalysisSkeleton(Modifier.padding(horizontal = 20.dp))
            }

            TranslateStatus.RESULT -> {
                val result = state.result
                if (result != null) {
                    item {
                        BrandCard(Modifier.padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel("Send this")
                                Spacer(Modifier.weight(1f))
                                DirectionPill("EN → TR")
                            }
                            Spacer(Modifier.height(12.dp))
                            SelectionContainer {
                                Text(
                                    text = result.naturalTranslation,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 22.sp,
                                        lineHeight = 31.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Xion.Ink,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpeakerButton(
                                    text = result.naturalTranslation,
                                    utteranceKey = "reply-${result.id}",
                                )
                                CopyButton(
                                    text = result.naturalTranslation,
                                    onCopied = { notify("Turkish copied") },
                                    description = "Copy Turkish message",
                                    tint = Xion.BlueHi,
                                )
                                StopSpeechButton()
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = state.style.label.uppercase(),
                                    style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                                    color = Xion.MutedDim,
                                )
                            }
                            if (result.literalTranslation.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Hairline()
                                Spacer(Modifier.height(12.dp))
                                SectionLabel("Back-translation")
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = result.literalTranslation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Xion.Body,
                                )
                            }
                            if (result.tones.isNotEmpty() || result.toneNote.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    result.tones.filter { it.isNotBlank() }.forEach { ToneChip(it) }
                                }
                                if (result.toneNote.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = result.toneNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Xion.Muted,
                                    )
                                }
                            }
                            if (result.confidence.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                MetaText(result.confidence)
                            }
                        }
                    }

                    if (result.replies.isNotEmpty()) {
                        item {
                            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                                SectionLabel("Alternatives")
                                Spacer(Modifier.height(10.dp))
                                ReplySuggestions(
                                    replies = result.replies,
                                    onCopied = { notify("Turkish copied") },
                                    keyPrefix = "reply-alt-${result.id}",
                                )
                            }
                        }
                    }
                }
            }

            TranslateStatus.IDLE -> item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "The reply keeps your meaning, not your word order. Choose a tone above; “Natural” stays neutral unless you ask for warmth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.MutedDim,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Context: ${if (state.threadId > 0) "active conversation" else "none"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Xion.Muted,
                        )
                        Spacer(Modifier.width(6.dp))
                        Hairline(Modifier.weight(1f), color = Xion.Line)
                    }
                }
            }
        }
    }
}
