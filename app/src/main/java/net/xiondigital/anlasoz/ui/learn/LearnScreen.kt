package net.xiondigital.anlasoz.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.data.model.VocabularyEntry
import net.xiondigital.anlasoz.ui.components.BrandCard
import net.xiondigital.anlasoz.ui.components.BrandTopBar
import net.xiondigital.anlasoz.ui.components.EmptyState
import net.xiondigital.anlasoz.ui.components.FilterPill
import net.xiondigital.anlasoz.ui.components.Hairline
import net.xiondigital.anlasoz.ui.components.IconAction
import net.xiondigital.anlasoz.ui.components.InlineTextField
import net.xiondigital.anlasoz.ui.components.MetaText
import net.xiondigital.anlasoz.ui.components.MessageField
import net.xiondigital.anlasoz.ui.components.PrimaryButton
import net.xiondigital.anlasoz.ui.components.QuietTextButton
import net.xiondigital.anlasoz.ui.components.ScreenTitle
import net.xiondigital.anlasoz.ui.components.SecondaryButton
import net.xiondigital.anlasoz.ui.components.SectionLabel
import net.xiondigital.anlasoz.ui.components.SpeakerButton
import net.xiondigital.anlasoz.ui.components.StatBlock
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Serif
import net.xiondigital.anlasoz.ui.theme.Xion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val filtered = remember(state.vocabulary, state.query) {
        if (state.query.isBlank()) {
            state.vocabulary
        } else {
            val q = state.query.trim().lowercase()
            state.vocabulary.filter {
                it.turkish.lowercase().contains(q) || it.english.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BrandTopBar() }
        item {
            ScreenTitle(
                title = "Your Turkish",
                subtitle = "Built from the messages you actually receive",
            )
        }

        item {
            BrandCard(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatBlock(
                        value = state.stats.savedWords.toString(),
                        label = "Words",
                        modifier = Modifier.weight(1f),
                    )
                    StatBlock(
                        value = state.stats.grammarNotes.toString(),
                        label = "Concepts",
                        modifier = Modifier.weight(1f),
                    )
                    StatBlock(
                        value = state.stats.reviews.toString(),
                        label = "Reviews",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LearnTab.entries.forEach { tab ->
                    FilterPill(
                        label = tab.label,
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                    )
                }
            }
        }

        when (state.tab) {
            LearnTab.VOCABULARY -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = "Search saved words",
                            leadingIcon = Icons.Outlined.Search,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        SecondaryButton(
                            text = "Add",
                            onClick = { viewModel.openEditor() },
                            icon = Icons.Outlined.Add,
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.SpeakerNotes,
                            title = if (state.query.isBlank()) "No saved words yet" else "Nothing matches",
                            body = if (state.query.isBlank()) {
                                "Tap the bookmark beside any word in an analysis, or add a word yourself."
                            } else {
                                "Try a different spelling — Turkish characters are matched exactly."
                            },
                            actionLabel = if (state.query.isBlank()) "Add a word" else null,
                            onAction = if (state.query.isBlank()) {
                                { viewModel.openEditor() }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        VocabularyCard(
                            entry = entry,
                            onStar = { viewModel.toggleStar(entry) },
                            onDelete = { viewModel.deleteWord(entry) },
                            onEdit = { viewModel.openEditor(entry) },
                            onCopied = { scope.launch { snackbarHostState.showSnackbar("Turkish copied") } },
                        )
                    }
                }
            }

            LearnTab.GRAMMAR -> {
                if (state.grammar.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.MenuBook,
                            title = "No grammar notes yet",
                            body = "Each analysis stores the structure it explains here, so patterns repeat themselves over time instead of in a separate textbook.",
                        )
                    }
                } else {
                    items(state.grammar, key = { it.id }) { note ->
                        BrandCard(
                            Modifier.padding(horizontal = 20.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel("Concept")
                                Spacer(Modifier.weight(1f))
                                IconAction(
                                    icon = Icons.Outlined.Delete,
                                    description = "Delete note",
                                    onClick = { viewModel.deleteGrammar(note) },
                                    tint = Xion.MutedDim,
                                    size = 32.dp,
                                )
                            }
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Xion.Ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = note.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Xion.Body,
                            )
                            if (note.exampleTr.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Xion.PanelHi)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = note.exampleTr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Xion.Ink,
                                    )
                                    if (note.exampleEn.isNotBlank()) {
                                        Text(
                                            text = note.exampleEn,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Xion.Muted,
                                        )
                                    }
                                }
                            }
                            if (note.sourceText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                MetaText("From: ${note.sourceText}", color = Xion.MutedDim)
                            }
                        }
                    }
                }
            }

            LearnTab.PRACTICE -> {
                if (state.deck.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.MenuBook,
                            title = "Nothing to practice yet",
                            body = "Save a few words and this becomes a review deck built from your own conversations — cloze sentences where possible, recall cards otherwise.",
                            actionLabel = "Go to vocabulary",
                            onAction = { viewModel.selectTab(LearnTab.VOCABULARY) },
                        )
                    }
                } else if (state.reviewed >= state.deck.size) {
                    item {
                        BrandCard(Modifier.padding(horizontal = 20.dp)) {
                            SectionLabel("Round complete", color = Xion.BlueHi)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${state.deck.size} cards reviewed",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Xion.Ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Repeating a word after a gap is what makes it stick. Start another round whenever you like.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Xion.Muted,
                            )
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "New round",
                                onClick = viewModel::refreshDeck,
                                icon = Icons.Outlined.Refresh,
                            )
                        }
                    }
                } else {
                    item {
                        PracticeCardView(
                            index = state.cardIndex,
                            total = state.deck.size,
                            prompt = state.deck[state.cardIndex].prompt,
                            answer = state.deck[state.cardIndex].answer,
                            detail = state.deck[state.cardIndex].detail,
                            example = state.deck[state.cardIndex].example,
                            revealed = state.revealed,
                            onReveal = viewModel::reveal,
                            onGotIt = { viewModel.rate(true) },
                            onAgain = { viewModel.rate(false) },
                        )
                    }
                }
            }
        }
    }

    if (state.showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEditor,
            sheetState = sheetState,
            containerColor = Xion.Panel,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Xion.LineStrong),
                )
            },
        ) {
            VocabularyEditor(
                editing = state.editing,
                onSave = viewModel::saveEntry,
                onCancel = viewModel::closeEditor,
            )
        }
    }
}

@Composable
private fun VocabularyCard(
    entry: VocabularyEntry,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCopied: () -> Unit,
) {
    BrandCard(
        Modifier.padding(horizontal = 20.dp),
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SelectionContainer {
                    Text(
                        text = entry.turkish,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Xion.Ink,
                    )
                }
                if (entry.pronunciation.isNotBlank()) {
                    Text(
                        text = entry.pronunciation,
                        style = MonoLabel.copy(fontSize = 9.5.sp, letterSpacing = 0.8.sp),
                        color = Xion.BlueHi,
                    )
                }
            }
            IconAction(
                icon = if (entry.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                description = if (entry.starred) "Unstar" else "Star",
                onClick = onStar,
                tint = if (entry.starred) Xion.BlueHi else Xion.MutedDim,
                size = 34.dp,
            )
            IconAction(
                icon = Icons.Outlined.Delete,
                description = "Delete word",
                onClick = onDelete,
                tint = Xion.MutedDim,
                size = 34.dp,
            )
        }
        Text(
            text = entry.english,
            style = MaterialTheme.typography.bodyLarge,
            color = Xion.Body,
        )
        if (entry.example.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.example,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = Xion.Muted,
            )
        }
        if (entry.context.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.context,
                style = MaterialTheme.typography.bodySmall,
                color = Xion.MutedDim,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeakerButton(text = entry.turkish, utteranceKey = "vocab-${entry.id}", size = 32.dp)
            QuietTextButton(text = "Edit", onClick = onEdit)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${entry.reviews} REVIEWS",
                style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.1.sp),
                color = Xion.MutedDim,
            )
        }
    }
}

@Composable
private fun PracticeCardView(
    index: Int,
    total: Int,
    prompt: String,
    answer: String,
    detail: String,
    example: String,
    revealed: Boolean,
    onReveal: () -> Unit,
    onGotIt: () -> Unit,
    onAgain: () -> Unit,
) {
    BrandCard(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Practice")
            Spacer(Modifier.weight(1f))
            MetaText("${index + 1} / $total")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = prompt,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Serif, fontSize = 26.sp),
            color = Xion.Ink,
        )
        Spacer(Modifier.height(14.dp))
        if (!revealed) {
            PrimaryButton(text = "Show answer", onClick = onReveal)
        } else {
            Hairline()
            Spacer(Modifier.height(12.dp))
            SectionLabel("Answer")
            Spacer(Modifier.height(6.dp))
            Text(
                text = answer,
                style = MaterialTheme.typography.headlineMedium,
                color = Xion.BlueHi,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xion.Muted,
                )
            }
            if (example.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = Xion.MutedDim,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(text = "Again", onClick = onAgain)
                PrimaryButton(
                    text = "Got it",
                    onClick = onGotIt,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VocabularyEditor(
    editing: VocabularyEntry?,
    onSave: (String, String, String, String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var turkish by rememberSaveable(editing?.id) { mutableStateOf(editing?.turkish.orEmpty()) }
    var english by rememberSaveable(editing?.id) { mutableStateOf(editing?.english.orEmpty()) }
    var pronunciation by rememberSaveable(editing?.id) { mutableStateOf(editing?.pronunciation.orEmpty()) }
    var example by rememberSaveable(editing?.id) { mutableStateOf(editing?.example.orEmpty()) }
    var context by rememberSaveable(editing?.id) { mutableStateOf(editing?.context.orEmpty()) }
    var grammar by rememberSaveable(editing?.id) { mutableStateOf(editing?.grammarNote.orEmpty()) }
    var error by rememberSaveable(editing?.id) { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .imePadding(),
    ) {
        Text(
            text = if (editing == null) "Add a word" else "Edit word",
            style = MaterialTheme.typography.headlineMedium,
            color = Xion.Ink,
        )
        Spacer(Modifier.height(4.dp))
        MetaText("Only the Turkish word and its meaning are required.")
        Spacer(Modifier.height(16.dp))

        EditorField("Turkish", turkish, { turkish = it }, "canım")
        Spacer(Modifier.height(10.dp))
        EditorField("English", english, { english = it }, "dear / my life")
        Spacer(Modifier.height(10.dp))
        EditorField("Pronunciation", pronunciation, { pronunciation = it }, "ca-nım")
        Spacer(Modifier.height(10.dp))
        EditorField("Example", example, { example = it }, "Canım, nasılsın?", minHeight = true)
        Spacer(Modifier.height(10.dp))
        EditorField("Usage", context, { context = it }, "Affectionate, used with close friends and partners", minHeight = true)
        Spacer(Modifier.height(10.dp))
        EditorField("Grammar note", grammar, { grammar = it }, "Possessive suffix -ım on can", minHeight = true)

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, style = MaterialTheme.typography.bodySmall, color = Xion.Danger)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "Cancel", onClick = onCancel)
            PrimaryButton(
                text = if (editing == null) "Save word" else "Update",
                onClick = {
                    if (turkish.isBlank() || english.isBlank()) {
                        error = "Add the Turkish word and its English meaning."
                    } else {
                        onSave(turkish, english, pronunciation, example, context, grammar)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Boolean = false,
) {
    Column {
        SectionLabel(label, color = Xion.MutedDim)
        Spacer(Modifier.height(6.dp))
        if (minHeight) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Xion.PanelHi)
                    .border(1.dp, Xion.Line, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                MessageField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    minHeight = 44.dp,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            InlineTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
            )
        }
    }
}
