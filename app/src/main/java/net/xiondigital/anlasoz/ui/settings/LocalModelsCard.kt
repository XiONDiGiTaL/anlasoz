package net.xiondigital.anlasoz.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xiondigital.anlasoz.ai.LocalModelState
import net.xiondigital.anlasoz.local.DownloadState
import net.xiondigital.anlasoz.local.LocalModelSource
import net.xiondigital.anlasoz.local.ModelCatalog
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.Hairline
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.MetaText
import net.xiondigital.anlasoz.ui.components.PrimaryButton
import net.xiondigital.anlasoz.ui.components.QuietTextButton
import net.xiondigital.anlasoz.ui.components.SecondaryButton
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Xion

/**
 * The on-device engine: what is installed, which file runs the language work,
 * and the curated downloads. Kept out of SettingsScreen so that file stays a
 * list of cards.
 */
@Composable
fun LocalModelsCard(
    state: SettingsUiState,
    onSelectModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDownload: (LocalModelSource) -> Unit,
    onCancelDownload: (String) -> Unit,
    onImport: () -> Unit,
    onBrowse: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onStop: () -> Unit,
    onGpuOffload: (Boolean) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    BrandCard(Modifier.padding(horizontal = 20.dp)) {
        SectionLabel("On-device engine")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "A GGUF model on this phone",
            style = MaterialTheme.typography.titleLarge,
            color = Xion.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "llama.cpp runs inside the app. Nothing is sent anywhere and no key is needed; " +
                "the trade-off is speed and depth against a cloud model.",
            style = MaterialTheme.typography.bodySmall,
            color = Xion.Muted,
        )

        if (!state.engineAvailable) {
            Spacer(Modifier.height(12.dp))
            Notice(
                tone = NoticeTone.Warn,
                text = "This build has no arm64 engine, so on-device models cannot run here. " +
                    "The rest of the app keeps working with a remote provider.",
            )
            return@BrandCard
        }

        Spacer(Modifier.height(12.dp))
        EngineStatus(state)

        val engine = state.engine
        if (engine is LocalModelState.Ready && engine.gpuRejected) {
            Spacer(Modifier.height(10.dp))
            Notice(
                tone = NoticeTone.Warn,
                text = "The GPU driver rejected this model's shaders, so it is running on the " +
                    "CPU. This is a driver limitation in llama.cpp's Vulkan backend on some " +
                    "Adreno devices, not something the app can work around.",
            )
        }

        if (state.gpuAvailable) {
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Use the GPU",
                description = "${state.gpuDevice} runs the matrix work over Vulkan. The weights " +
                    "stay in the same memory, so the model reads long messages in a fraction " +
                    "of the time. Takes effect the next time the model is loaded.",
                checked = state.gpuOffload,
                onCheckedChange = onGpuOffload,
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Installed", color = Xion.MutedDim)
        Spacer(Modifier.height(8.dp))

        if (state.deviceModels.isEmpty()) {
            Text(
                text = "No model files yet. Download one below, or import a .gguf from storage.",
                style = MaterialTheme.typography.bodySmall,
                color = Xion.Muted,
            )
        } else {
            state.deviceModels.forEach { model ->
                val selected = model.name == state.deviceModel
                ModelRow(
                    name = ModelCatalog.byFileName(model.name)?.name ?: model.name,
                    detail = model.displaySize,
                    selected = selected,
                    onSelect = { onSelectModel(model.name) },
                    onDelete = { pendingDelete = model.name },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "Import .gguf", onClick = onImport, enabled = !state.importing)
            SecondaryButton(text = "Browse models", onClick = onBrowse)
        }
        if (state.importing) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Copying the file into app storage…",
                style = MaterialTheme.typography.bodySmall,
                color = Xion.Muted,
            )
        }

        if (state.deviceModels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = when (state.engine) {
                        is LocalModelState.Ready -> "Reload"
                        is LocalModelState.Loading -> "Loading…"
                        else -> "Load now"
                    },
                    onClick = onLoad,
                    enabled = state.deviceModel.isNotBlank() && state.engine !is LocalModelState.Loading,
                    modifier = Modifier.weight(1f),
                )
                when {
                    state.engine is LocalModelState.Loading -> Unit
                    state.engine is LocalModelState.Ready -> {
                        SecondaryButton(text = "Unload", onClick = onUnload)
                        SecondaryButton(text = "Stop", onClick = onStop)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        SectionLabel("Download", color = Xion.MutedDim)
        Spacer(Modifier.height(4.dp))
        MetaText(
            "Free storage: ${ModelCatalog.formatBytes(state.freeStorage)}. " +
                "Keep the app open while a model downloads; an interrupted file is discarded.",
        )
        Spacer(Modifier.height(10.dp))

        ModelCatalog.downloadable.forEach { source ->
            DownloadRow(
                source = source,
                installed = state.deviceModels.any { it.name == source.fileName },
                download = state.downloads[source.id],
                onDownload = { onDownload(source) },
                onCancel = { onCancelDownload(source.id) },
            )
            Spacer(Modifier.height(10.dp))
        }

        MetaText(
            "A phone needs the model plus some working memory: about 2.6 GB for Vexium Core 3B. " +
                "Anything above 4 GB will not fit on most devices.",
            color = Xion.MutedDim,
        )
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Xion.Panel,
            title = { Text("Delete this model?", color = Xion.Ink) },
            text = {
                Text(
                    text = "$target will be removed from this device. You can download it again later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xion.Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteModel(target)
                    },
                ) { Text("Delete", color = Xion.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = Xion.Muted)
                }
            },
        )
    }
}

@Composable
private fun EngineStatus(state: SettingsUiState) {
    val engine = state.engine
    val (tone, text) = when (engine) {
        is LocalModelState.Unavailable -> NoticeTone.Warn to "Engine unavailable"
        is LocalModelState.Idle -> NoticeTone.Muted to
            if (state.deviceModel.isBlank()) {
                "No model selected"
            } else {
                "${state.deviceModel} · not loaded yet"
            }
        is LocalModelState.Loading -> NoticeTone.Work to "Loading ${engine.fileName}…"
        is LocalModelState.Ready -> NoticeTone.Ok to
            "${engine.fileName} · ${ModelCatalog.formatBytes(engine.bytes)} · " +
                "${engine.contextSize} tokens · loaded in ${engine.loadMillis / 1000}s · " +
                (if (engine.offloaded) engine.device.ifBlank { "GPU" } else "CPU")
        is LocalModelState.Failed -> NoticeTone.Warn to "${engine.fileName}: ${engine.message}"
    }
    val generating = engine is LocalModelState.Ready && state.generatedTokens > 0
    Notice(
        tone = tone,
        text = if (generating) "$text · generating ${state.generatedTokens} tokens" else text,
    )
}

@Composable
private fun ModelRow(
    name: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Xion.PanelHi else Xion.Panel)
            .border(
                1.dp,
                if (selected) Xion.Blue else Xion.Line,
                RoundedCornerShape(12.dp),
            )
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) Xion.BlueHi else Xion.MutedDim),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Xion.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = Xion.Muted)
        }
        if (!selected) {
            QuietTextButton(text = "Use", onClick = onSelect, color = Xion.BlueHi)
        } else {
            Text(
                text = "SELECTED",
                style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.1.sp),
                color = Xion.BlueHi,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconAction(
            icon = Icons.Outlined.DeleteOutline,
            description = "Delete $name",
            onClick = onDelete,
            tint = Xion.Muted,
        )
    }
}

@Composable
private fun DownloadRow(
    source: LocalModelSource,
    installed: Boolean,
    download: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Xion.Ink,
                    )
                    if (source.recommended) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "DEFAULT",
                            style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                            color = Xion.Ok,
                        )
                    }
                }
                Text(
                    text = source.publisher,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
                Text(
                    text = source.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Muted,
                )
            }
            Spacer(Modifier.width(10.dp))
            when {
                installed -> Text(
                    text = "INSTALLED",
                    style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                    color = Xion.Ok,
                )
                download is DownloadState.Running -> QuietTextButton(
                    text = "Cancel",
                    onClick = onCancel,
                    color = Xion.Danger,
                )
                else -> SecondaryButton(text = "Install", onClick = onDownload)
            }
        }

        when (download) {
            is DownloadState.Running -> {
                Spacer(Modifier.height(6.dp))
                val fraction = download.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Xion.Blue,
                        trackColor = Xion.LineStrong,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Xion.Blue,
                        trackColor = Xion.LineStrong,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${ModelCatalog.formatBytes(download.received)} of " +
                        (download.total?.let { ModelCatalog.formatBytes(it) } ?: "unknown"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Muted,
                )
            }
            is DownloadState.Failed -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = download.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Danger,
                )
            }
            else -> Unit
        }
    }
}

private enum class NoticeTone { Ok, Warn, Work, Muted }

@Composable
private fun Notice(tone: NoticeTone, text: String) {
    val color = when (tone) {
        NoticeTone.Ok -> Xion.Ok
        NoticeTone.Warn -> Xion.Warn
        NoticeTone.Work -> Xion.BlueHi
        NoticeTone.Muted -> Xion.Muted
    }
    val icon = when (tone) {
        NoticeTone.Ok -> Icons.Outlined.Check
        NoticeTone.Warn -> Icons.Outlined.ErrorOutline
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Xion.PanelHi)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (tone == NoticeTone.Muted) Xion.Muted else color,
        )
    }
}
