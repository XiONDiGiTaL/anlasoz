package net.xiondigital.anlasoz.ui.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.audio.SttController
import net.xiondigital.anlasoz.core.TurkishText
import net.xiondigital.anlasoz.ui.components.AnalysisResultCard
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.BrandTopBar
import net.xiondigital.anlasoz.ui.components.DirectionPill
import net.xiondigital.anlasoz.ui.components.FailureCard
import net.xiondigital.anlasoz.ui.components.Hairline
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.LoadingAnalysisSkeleton
import net.xiondigital.anlasoz.ui.components.MetaText
import net.xiondigital.anlasoz.ui.components.MessageField
import net.xiondigital.anlasoz.ui.components.PrimaryButton
import net.xiondigital.anlasoz.ui.components.ScreenTitle
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.components.SetupCard
import net.xiondigital.anlasoz.ui.components.ThreadMenu
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Xion
import java.util.Locale

private val exampleMessages = listOf(
    "Yarın görüşürüz.",
    "Ne yapıyorsun?",
    "Bugün hava çok güzel.",
    "Kahve içer misin?",
    "See you tomorrow at nine.",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(
    viewModel: TranslateViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val stt = remember { SttController(context) }
    val sttState by stt.state.collectAsStateWithLifecycle()
    var partial by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { stt.destroy() }
    }
    SideEffect {
        stt.onTranscript = { text, isFinal ->
            if (isFinal) {
                partial = ""
                viewModel.useTranscript(text)
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
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.onSharedText(sharedText)
            onSharedTextConsumed()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val listen = {
        stt.updateLocale(if (state.hintLanguage == "en") Locale.US else Locale("tr", "TR"))
        stt.start()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen() else notify("Microphone permission is required for transcription")
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
                    ThreadMenu(
                        currentThread = state.threadTitle,
                        threads = state.threads,
                        onSelect = viewModel::switchThread,
                        onNew = viewModel::newThread,
                    )
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
                title = "Understand",
                subtitle = if (state.contextEnabled) {
                    "Context on — earlier messages in this conversation are considered"
                } else {
                    "Context off — each message is read on its own"
                },
            )
        }

        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Message")
                    Spacer(Modifier.width(8.dp))
                    DirectionPill(
                        when (state.hintLanguage) {
                            "tr" -> "TR DETECTED"
                            "en" -> "EN DETECTED"
                            else -> "AUTO"
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${TurkishText.wordCount(state.input)} W",
                        style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                        color = Xion.MutedDim,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MessageField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = "Paste the message you received, or type what you want to say…",
                    enabled = state.status != TranslateStatus.LOADING,
                    minHeight = 96.dp,
                )
                Spacer(Modifier.height(8.dp))
                Hairline()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(
                        icon = if (sttState.listening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                        description = if (sttState.listening) "Stop dictation" else "Dictate a message",
                        onClick = onMic,
                        tint = if (sttState.listening) Xion.BlueHi else Xion.Body,
                    )
                    IconAction(
                        icon = Icons.Outlined.Close,
                        description = "Clear message",
                        onClick = viewModel::clearInput,
                        enabled = state.input.isNotEmpty(),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (sttState.listening) {
                            "LISTENING…"
                        } else {
                            "TAP THE MIC TO DICTATE"
                        },
                        style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.2.sp),
                        color = if (sttState.listening) Xion.BlueHi else Xion.MutedDim,
                    )
                }
            }
        }

        if (sttState.listening || partial.isNotBlank()) {
            item {
                ListeningRow(partial = partial, error = sttState.error)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(
                    text = if (state.status == TranslateStatus.LOADING) "Analyzing" else "Analyze",
                    onClick = viewModel::analyze,
                    loading = state.status == TranslateStatus.LOADING,
                    icon = Icons.Outlined.AutoAwesome,
                )
                if (state.status != TranslateStatus.LOADING) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.configured) {
                            "Turkish in, English out — or the reverse, with the reasoning."
                        } else {
                            "Choose a model in Settings before the first analysis."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.MutedDim,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }

        when (state.status) {
            TranslateStatus.SETUP -> item {
                SetupCard(
                    onOpenSettings = onOpenSettings,
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
                state.analysis?.let { analysis ->
                    item {
                        AnalysisResultCard(
                            analysis = analysis,
                            savedWords = state.savedWords,
                            onSaveWord = viewModel::saveWord,
                            onCopied = { notify("Turkish copied") },
                            onToggleFavorite = viewModel::toggleFavorite,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }

            TranslateStatus.IDLE -> item {
                if (!state.configured) {
                    SetupCard(
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    return@item
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionLabel("Try a message", Modifier.padding(bottom = 10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        exampleMessages.forEach { example ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Xion.PanelHi)
                                    .clickable { viewModel.onInputChange(example) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = example,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Xion.Body,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Everything you analyze stays on this device. Saved messages remain readable offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.MutedDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningRow(partial: String, error: String?) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Xion.Blue.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (error != null) error else "Listening — speak naturally",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) Xion.Danger else Xion.BlueHi,
            )
            if (partial.isNotBlank()) {
                Text(
                    text = partial,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Muted,
                )
            }
        }
        Icon(
            Icons.Outlined.Mic,
            contentDescription = null,
            tint = Xion.MutedDim,
            modifier = Modifier.size(14.dp),
        )
    }
}
