package net.xiondigital.anlasoz.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.core.PracticeCard
import net.xiondigital.anlasoz.data.model.GrammarNote
import net.xiondigital.anlasoz.data.model.VocabularyEntry
import net.xiondigital.anlasoz.data.repo.LibraryStats

enum class LearnTab(val label: String) {
    VOCABULARY("Vocabulary"),
    GRAMMAR("Grammar"),
    PRACTICE("Practice"),
}

data class LearnUiState(
    val tab: LearnTab = LearnTab.VOCABULARY,
    val query: String = "",
    val vocabulary: List<VocabularyEntry> = emptyList(),
    val grammar: List<GrammarNote> = emptyList(),
    val stats: LibraryStats = LibraryStats(),
    val deck: List<PracticeCard> = emptyList(),
    val cardIndex: Int = 0,
    val revealed: Boolean = false,
    val reviewed: Int = 0,
    val message: String? = null,
    val showEditor: Boolean = false,
    val editing: VocabularyEntry? = null,
)

class LearnViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            container.library.observeVocabulary().collect { vocabulary ->
                _state.update { it.copy(vocabulary = vocabulary) }
            }
        }
        viewModelScope.launch {
            container.library.observeGrammar().collect { grammar ->
                _state.update { it.copy(grammar = grammar) }
            }
        }
        viewModelScope.launch {
            container.library.observeStats().collect { stats ->
                _state.update { it.copy(stats = stats) }
            }
        }
        refreshDeck()
    }

    fun selectTab(tab: LearnTab) {
        _state.update { it.copy(tab = tab, revealed = false) }
        if (tab == LearnTab.PRACTICE) refreshDeck()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun openEditor(entry: VocabularyEntry? = null) {
        _state.update { it.copy(showEditor = true, editing = entry) }
    }

    fun closeEditor() {
        _state.update { it.copy(showEditor = false, editing = null) }
    }

    fun saveEntry(
        turkish: String,
        english: String,
        pronunciation: String,
        example: String,
        context: String,
        grammarNote: String,
    ) {
        viewModelScope.launch {
            val editing = _state.value.editing
            val ok = if (editing != null) {
                container.library.updateVocabulary(
                    editing.copy(
                        turkish = turkish.trim(),
                        english = english.trim(),
                        pronunciation = pronunciation.trim(),
                        example = example.trim(),
                        context = context.trim(),
                        grammarNote = grammarNote.trim(),
                    ),
                )
                true
            } else {
                container.library.addVocabulary(
                    turkish = turkish,
                    english = english,
                    pronunciation = pronunciation,
                    example = example,
                    context = context,
                    grammarNote = grammarNote,
                )
            }
            _state.update { it.copy(showEditor = false, editing = null) }
            show(if (ok) "Saved to vocabulary" else "That word is already saved")
            refreshDeck()
        }
    }

    fun deleteWord(entry: VocabularyEntry) {
        viewModelScope.launch {
            container.library.deleteVocabulary(entry.id)
            show("Removed ${entry.turkish}")
            refreshDeck()
        }
    }

    fun toggleStar(entry: VocabularyEntry) {
        viewModelScope.launch { container.library.setStarred(entry.id, !entry.starred) }
    }

    fun deleteGrammar(note: GrammarNote) {
        viewModelScope.launch {
            container.library.deleteGrammar(note)
            show("Grammar note removed")
        }
    }

    fun refreshDeck() {
        viewModelScope.launch {
            val deck = container.library.practiceDeck()
            _state.update {
                it.copy(deck = deck, cardIndex = 0, revealed = false, reviewed = 0)
            }
        }
    }

    fun reveal() {
        _state.update { it.copy(revealed = true) }
    }

    fun rate(gotIt: Boolean) {
        val current = _state.value
        val card = current.deck.getOrNull(current.cardIndex) ?: return
        viewModelScope.launch {
            container.library.markReviewed(card.id)
            // A word the learner was unsure about comes back at the end of this round.
            val deck = if (gotIt) current.deck else current.deck + card
            val next = current.cardIndex + 1
            val finished = next >= deck.size
            _state.update {
                it.copy(
                    deck = deck,
                    cardIndex = next,
                    revealed = false,
                    reviewed = it.reviewed + 1,
                )
            }
            if (finished) {
                show("Round complete · ${_state.value.reviewed} cards reviewed")
            }
        }
    }

    fun consumeMessage() {
        messageJob?.cancel()
        _state.update { it.copy(message = null) }
    }

    private fun show(text: String) {
        _state.update { it.copy(message = text) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2600)
            _state.update { it.copy(message = null) }
        }
    }
}
