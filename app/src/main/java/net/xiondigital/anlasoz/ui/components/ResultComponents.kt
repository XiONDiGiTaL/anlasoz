package net.xiondigital.anlasoz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.xiondigital.anlasoz.core.TurkishText
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.BreakdownItem
import net.xiondigital.anlasoz.data.model.Direction
import net.xiondigital.anlasoz.data.model.ReplySuggestion
import net.xiondigital.anlasoz.data.model.VocabItem
import net.xiondigital.anlasoz.ui.theme.MonoLabel
import net.xiondigital.anlasoz.ui.theme.Serif
import net.xiondigital.anlasoz.ui.theme.Sora
import net.xiondigital.anlasoz.ui.theme.Xion

/** Which Turkish string the speaker and copy actions should act on. */
fun Analysis.turkishText(): String =
    if (direction == Direction.TR_EN) sourceText else naturalTranslation

@Composable
fun AnalysisResultCard(
    analysis: Analysis,
    savedWords: Set<String>,
    onSaveWord: (VocabItem) -> Unit,
    onCopied: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showOriginal: Boolean = false,
) {
    val turkish = analysis.turkishText()
    val directionLabel = if (analysis.direction == Direction.TR_EN) "TR → EN" else "EN → TR"
    val heroLabel = if (analysis.direction == Direction.TR_EN) "Natural English" else "Natural Turkish"

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandCard {
            if (showOriginal && analysis.sourceText.isNotBlank()) {
                SectionLabel("Original")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = analysis.sourceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Xion.Body,
                )
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectionPill(directionLabel)
                Spacer(Modifier.width(10.dp))
                SectionLabel(heroLabel, Modifier.weight(1f))
                IconAction(
                    icon = if (analysis.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    description = if (analysis.favorite) "Remove from favorites" else "Save to favorites",
                    onClick = onToggleFavorite,
                    tint = if (analysis.favorite) Xion.BlueHi else Xion.Muted,
                )
            }
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Text(
                    text = analysis.naturalTranslation,
                    style = if (analysis.direction == Direction.TR_EN) {
                        MaterialTheme.typography.displayLarge.copy(fontFamily = Serif)
                    } else {
                        MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 22.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    color = Xion.Ink,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakerButton(text = turkish, utteranceKey = "analysis-${analysis.id}")
                CopyButton(
                    text = turkish,
                    onCopied = onCopied,
                    description = "Copy Turkish text",
                )
                StopSpeechButton()
                Spacer(Modifier.weight(1f))
                MetaText("${TurkishText.wordCount(turkish)} words · tr-TR")
            }
            if (analysis.tones.isNotEmpty() || analysis.toneNote.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    analysis.tones.forEach { ToneChip(it) }
                }
                if (analysis.toneNote.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = analysis.toneNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Muted,
                    )
                }
            }
        }

        BrandCard {
            if (analysis.literalTranslation.isNotBlank()) {
                val fromTurkish = analysis.direction == Direction.TR_EN
                ExpandableSection(
                    title = if (fromTurkish) "Literal structure" else "Back-translation",
                    initiallyExpanded = false,
                ) {
                    Text(
                        text = analysis.literalTranslation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Xion.Body,
                    )
                    if (fromTurkish) {
                        Text(
                            text = "Turkish is verb-final: the verb carries tense and person at the end of the clause, and the subject is often dropped when it is understood.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Xion.MutedDim,
                        )
                    }
                }
            }

            if (analysis.contextual.isNotBlank() || analysis.interpretations.isNotEmpty()) {
                ExpandableSection(title = "Contextual reading", initiallyExpanded = true) {
                    if (analysis.contextual.isNotBlank()) {
                        Text(
                            text = analysis.contextual,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Xion.Body,
                        )
                    }
                    analysis.interpretations.forEach { alternative ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .width(5.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Xion.Blue),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = alternative,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Xion.Muted,
                            )
                        }
                    }
                }
            }

            if (analysis.breakdown.isNotEmpty()) {
                ExpandableSection(
                    title = "Sentence breakdown",
                    subtitle = "${analysis.breakdown.size} parts",
                ) {
                    analysis.breakdown.forEachIndexed { index, part ->
                        BreakdownRow(part)
                        if (index < analysis.breakdown.lastIndex) Hairline()
                    }
                }
            }

            if (analysis.grammarNotes.isNotBlank()) {
                ExpandableSection(title = "Grammar") {
                    Text(
                        text = analysis.grammarNotes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Xion.Body,
                    )
                }
            }

            if (turkish.isNotBlank()) {
                ExpandableSection(title = "Pronunciation") {
                    PronunciationBlock(turkish)
                }
            }

            if (analysis.vocabulary.isNotEmpty()) {
                ExpandableSection(
                    title = "Vocabulary",
                    subtitle = "${analysis.vocabulary.size} items",
                ) {
                    analysis.vocabulary.forEachIndexed { index, word ->
                        VocabularyRow(
                            word = word,
                            saved = savedWords.contains(word.turkish.trim().lowercase()),
                            onSave = { onSaveWord(word) },
                        )
                        if (index < analysis.vocabulary.lastIndex) Hairline()
                    }
                }
            }

            if (analysis.replies.isNotEmpty()) {
                ExpandableSection(
                    title = "Suggested replies",
                    subtitle = "${analysis.replies.size}",
                    initiallyExpanded = analysis.direction == Direction.TR_EN,
                ) {
                    ReplySuggestions(
                        replies = analysis.replies,
                        onCopied = onCopied,
                        keyPrefix = "analysis-${analysis.id}",
                    )
                }
            }

            if (analysis.confidence.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Confidence", color = Xion.MutedDim)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = analysis.confidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(part: BreakdownItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(0.42f)) {
            Text(
                text = part.turkish,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Sora),
                color = Xion.Ink,
            )
            if (part.note.isNotBlank()) {
                Text(
                    text = part.note,
                    style = MonoLabel.copy(fontSize = 9.5.sp, letterSpacing = 0.4.sp),
                    color = Xion.MutedDim,
                )
            }
        }
        Column(Modifier.weight(0.58f)) {
            Text(
                text = part.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = Xion.Body,
            )
        }
    }
}

@Composable
private fun VocabularyRow(
    word: VocabItem,
    saved: Boolean,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = word.turkish,
                    style = MaterialTheme.typography.titleMedium,
                    color = Xion.Ink,
                )
                if (word.pronunciation.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = word.pronunciation,
                        style = MonoLabel.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp),
                        color = Xion.BlueHi,
                    )
                }
            }
            Text(
                text = word.english,
                style = MaterialTheme.typography.bodyMedium,
                color = Xion.Body,
            )
            if (word.example.isNotBlank()) {
                Text(
                    text = word.example,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = Xion.Muted,
                )
            }
            if (word.note.isNotBlank()) {
                Text(
                    text = word.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.MutedDim,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (saved) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Xion.BlueSofter)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Xion.BlueHi,
                    modifier = Modifier.height(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "SAVED",
                    style = MonoLabel.copy(fontSize = 8.5.sp, letterSpacing = 1.1.sp),
                    color = Xion.BlueHi,
                )
            }
        } else {
            IconAction(
                icon = Icons.Outlined.BookmarkAdd,
                description = "Save ${word.turkish} to vocabulary",
                onClick = onSave,
                tint = Xion.BlueHi,
            )
        }
    }
}

/** Deterministic offline pronunciation help, always available without a network. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationBlock(turkish: String) {
    val words = TurkishText.syllabifyPhrase(turkish).take(12)
    if (words.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            words.forEach { (_, syllables) ->
                if (syllables.isEmpty()) return@forEach
                Text(
                    text = syllables.joinToString("·"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Sora),
                    color = Xion.Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Xion.PanelHi)
                        .border(1.dp, Xion.Line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    val stress = TurkishText.stressNote(turkish)
    if (stress.isNotBlank()) {
        Text(
            text = stress,
            style = MaterialTheme.typography.bodySmall,
            color = Xion.Muted,
        )
    }

    val notes = TurkishText.pronunciationNotes(turkish)
    if (notes.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        notes.forEach { (letter, note) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Text(
                    text = letter.toString(),
                    style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    color = Xion.BlueHi,
                    modifier = Modifier.width(22.dp),
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Xion.Muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ReplySuggestions(
    replies: List<ReplySuggestion>,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    keyPrefix: String = "reply",
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        replies.forEachIndexed { index, reply ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Xion.PanelHi)
                    .padding(12.dp),
            ) {
                Text(
                    text = reply.turkish,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Sora),
                    color = Xion.Ink,
                )
                if (reply.english.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = reply.english,
                        style = MaterialTheme.typography.bodySmall,
                        color = Xion.Muted,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CopyButton(
                        text = reply.turkish,
                        onCopied = onCopied,
                        description = "Copy suggested reply",
                        size = 34.dp,
                    )
                    SpeakerButton(
                        text = reply.turkish,
                        utteranceKey = "$keyPrefix-$index-${reply.turkish.hashCode()}",
                        size = 34.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (reply.tone.isNotBlank()) {
                        Text(
                            text = reply.tone.lowercase(),
                            style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                            color = Xion.MutedDim,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnswerText(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Xion.Ink,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = modifier,
        )
    }
}
