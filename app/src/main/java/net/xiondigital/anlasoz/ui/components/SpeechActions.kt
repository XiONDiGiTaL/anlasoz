package net.xiondigital.anlasoz.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.xiondigital.anlasoz.ui.LocalSpeech
import net.xiondigital.anlasoz.ui.theme.Xion

/**
 * Turkish playback control. Pause/resume is sentence-accurate, and the button
 * reflects whichever utterance is currently playing.
 */
@Composable
fun SpeakerButton(
    text: String,
    utteranceKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    val speech = LocalSpeech.current
    val state by speech.state.collectAsStateWithLifecycle()
    val isThis = state.speakingKey == utteranceKey
    val speaking = isThis && state.speaking
    val paused = isThis && state.paused

    val icon = when {
        speaking -> Icons.Outlined.Pause
        paused -> Icons.Outlined.PlayArrow
        else -> Icons.Outlined.VolumeUp
    }
    val description = when {
        speaking -> "Pause Turkish pronunciation"
        paused -> "Resume Turkish pronunciation"
        else -> "Hear this in Turkish"
    }
    IconAction(
        icon = icon,
        description = description,
        onClick = {
            when {
                speaking -> speech.pause()
                paused -> speech.resume()
                else -> speech.speak(text, utteranceKey, state.rate)
            }
        },
        modifier = modifier,
        tint = if (isThis) Xion.BlueHi else Xion.Body,
        size = size,
    )
}

/** Copies exactly the Turkish string — no labels, no commentary. */
@Composable
fun CopyButton(
    text: String,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Copy Turkish text",
    size: Dp = 38.dp,
    tint: androidx.compose.ui.graphics.Color = Xion.Body,
) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    IconAction(
        icon = Icons.Outlined.ContentCopy,
        description = description,
        onClick = {
            clipboard.setText(AnnotatedString(text))
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onCopied()
        },
        modifier = modifier,
        tint = tint,
        size = size,
    )
}

/** Stops the current queue; shown next to a playing hero phrase. */
@Composable
fun StopSpeechButton(modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val speech = LocalSpeech.current
    val state by speech.state.collectAsStateWithLifecycle()
    if (!state.speaking && !state.paused) return
    IconAction(
        icon = Icons.Outlined.Stop,
        description = "Stop Turkish audio",
        onClick = { speech.stop() },
        modifier = modifier,
        tint = Xion.Muted,
        size = size,
    )
}
