package net.xiondigital.anlasoz.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.ui.components.AnalysisResultCard
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.BrandTopBar
import net.xiondigital.anlasoz.ui.components.DirectionPill
import net.xiondigital.anlasoz.ui.components.EmptyState
import net.xiondigital.anlasoz.ui.components.FilterPill
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.InlineTextField
import net.xiondigital.anlasoz.ui.components.ScreenTitle
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Xion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
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
                    Box {
                        IconAction(
                            icon = Icons.Outlined.MoreVert,
                            description = "History actions",
                            onClick = { menuOpen = true },
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = Xion.PanelHi,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Xion.LineStrong),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export history (JSON)", color = Xion.Ink) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Xion.BlueHi,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.exportHistory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export vocabulary (CSV)", color = Xion.Ink) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Xion.BlueHi,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.exportVocabulary()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear history", color = Xion.Danger) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        tint = Xion.Danger,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.askClearAll(true)
                                },
                            )
                        }
                    }
                },
            )
        }

        item {
            ScreenTitle(
                title = "History",
                subtitle = if (state.items.isEmpty()) {
                    "Every analyzed message stays here, offline"
                } else {
                    "${state.items.size} ${if (state.items.size == 1) "exchange" else "exchanges"} · readable without a connection"
                },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                InlineTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search Turkish or English",
                    leadingIcon = Icons.Outlined.Search,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterPill(
                            label = filter.label,
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                        )
                    }
                }
                if (state.threads.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    ThreadFilter(
                        threads = state.threads.map { it.id to it.title },
                        selectedId = state.threadFilterId,
                        onSelect = viewModel::setThreadFilter,
                    )
                }
            }
        }

        if (state.items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = if (state.query.isBlank()) "Nothing saved yet" else "No matches",
                    body = if (state.query.isBlank()) {
                        "Analyze a message and it lands here automatically, with its context and grammar notes."
                    } else {
                        "Try a shorter search, or clear the filters above."
                    },
                )
            }
        } else {
            items(state.items, key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    onOpen = { viewModel.select(item) },
                    onStar = { viewModel.toggleFavorite(item) },
                )
            }
        }
    }

    state.selected?.let { selected ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.select(null) },
            sheetState = sheetState,
            containerColor = Xion.Bg,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(3.dp)
                        .background(Xion.LineStrong, RoundedCornerShape(2.dp)),
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 30.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(
                        if (selected.direction == Direction.TR_EN) "Turkish message" else "English message",
                    )
                    Spacer(Modifier.weight(1f))
                    IconAction(
                        icon = Icons.Outlined.Delete,
                        description = "Delete this exchange",
                        onClick = { viewModel.delete(selected) },
                        tint = Xion.Danger,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AnalysisResultCard(
                    analysis = selected,
                    savedWords = state.savedWords,
                    onSaveWord = { word -> viewModel.saveWord(selected, word) },
                    onCopied = { scope.launch { snackbarHostState.showSnackbar("Turkish copied") } },
                    onToggleFavorite = { viewModel.toggleFavorite(selected) },
                    showOriginal = true,
                )
            }
        }
    }

    if (state.showClearAll) {
        AlertDialog(
            onDismissRequest = { viewModel.askClearAll(false) },
            containerColor = Xion.Panel,
            title = {
                Text("Clear translation history?", color = Xion.Ink)
            },
            text = {
                Text(
                    "This deletes every analyzed message in every conversation. Saved vocabulary and grammar notes stay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xion.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearAll) {
                    Text("Clear", color = Xion.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.askClearAll(false) }) {
                    Text("Cancel", color = Xion.Muted)
                }
            },
        )
    }
}

@Composable
private fun ThreadFilter(
    threads: List<Pair<Long, String>>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = threads.firstOrNull { it.first == selectedId }?.second ?: "All conversations"
    Box {
        Row(
            modifier = Modifier
                .clickable { open = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Xion.BlueHi,
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = "Filter by conversation",
                tint = Xion.BlueHi,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Xion.PanelHi,
            shape = RoundedCornerShape(12.dp),
        ) {
            DropdownMenuItem(
                text = { Text("All conversations", color = Xion.Ink) },
                onClick = {
                    open = false
                    onSelect(0L)
                },
            )
            threads.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title, color = Xion.Ink) },
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: Analysis,
    onOpen: () -> Unit,
    onStar: () -> Unit,
) {
    BrandCard(
        Modifier
            .padding(horizontal = 20.dp)
            .clickable(onClick = onOpen),
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionPill(if (item.direction == Direction.TR_EN) "TR → EN" else "EN → TR")
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatTimestamp(item.createdAt),
                style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.sp),
                color = Xion.MutedDim,
            )
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                description = if (item.favorite) "Remove favorite" else "Favorite",
                onClick = onStar,
                tint = if (item.favorite) Xion.BlueHi else Xion.MutedDim,
                size = 30.dp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.sourceText,
            style = MaterialTheme.typography.bodyMedium,
            color = Xion.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.naturalTranslation,
            style = MaterialTheme.typography.titleMedium,
            color = Xion.Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.tones.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.tones.take(3).forEach { tone ->
                    Text(
                        text = tone.lowercase(),
                        style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.sp),
                        color = Xion.MutedDim,
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant)
    return when (instant.toLocalDate()) {
        today -> "TODAY $time"
        today.minusDays(1) -> "YESTERDAY $time"
        else -> DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH).format(instant).uppercase()
    }
}

