package net.xiondigital.anlasoz.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.xiondigital.anlasoz.AppContainer
import net.xiondigital.anlasoz.data.model.Analysis
import net.xiondigital.anlasoz.data.model.MessageThread

enum class HistoryFilter(val label: String, val direction: String) {
    ALL("All", ""),
    FAVORITES("Favorites", ""),
    TURKISH("TR → EN", "TR_EN"),
    ENGLISH("EN → TR", "EN_TR"),
}

data class HistoryUiState(
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val threadFilterId: Long = 0L,
    val items: List<Analysis> = emptyList(),
    val threads: List<MessageThread> = emptyList(),
    val selected: Analysis? = null,
    val savedWords: Set<String> = emptySet(),
    val message: String? = null,
    val exporting: Boolean = false,
    val showClearAll: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val filters = MutableStateFlow(Filters())
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var messageJob: Job? = null

    private data class Filters(
        val query: String = "",
        val filter: HistoryFilter = HistoryFilter.ALL,
        val threadId: Long = 0L,
    )

    init {
        viewModelScope.launch {
            container.translations.observeThreads().collect { threads ->
                _state.update { it.copy(threads = threads) }
            }
        }
        viewModelScope.launch {
            container.library.observeVocabulary().collect { vocabulary ->
                _state.update { it.copy(savedWords = vocabulary.map { word -> word.turkish.lowercase() }.toSet()) }
            }
        }
        viewModelScope.launch {
            filters
                .flatMapLatest { f ->
                    container.translations.observeHistory(
                        query = f.query,
                        onlyFavorites = f.filter == HistoryFilter.FAVORITES,
                        direction = f.filter.direction,
                        threadId = f.threadId,
                    )
                }
                .collect { items ->
                    _state.update { it.copy(items = items) }
                }
        }
    }

    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
        _state.update { it.copy(query = query) }
    }

    fun setFilter(filter: HistoryFilter) {
        filters.update { it.copy(filter = filter) }
        _state.update { it.copy(filter = filter) }
    }

    fun setThreadFilter(id: Long) {
        filters.update { it.copy(threadId = id) }
        _state.update { it.copy(threadFilterId = id) }
    }

    fun select(item: Analysis?) {
        _state.update { it.copy(selected = item) }
    }

    /** Saves a word from a stored analysis, keeping its original message as the source. */
    fun saveWord(item: Analysis, word: net.xiondigital.anlasoz.data.model.VocabItem) {
        viewModelScope.launch {
            val ok = container.library.saveVocabulary(word, item.id)
            show(
                if (ok) "Saved \"${word.turkish}\" to vocabulary"
                else "\"${word.turkish}\" is already in your vocabulary",
            )
        }
    }

    fun toggleFavorite(item: Analysis) {
        viewModelScope.launch {
            container.translations.setFavorite(item.id, !item.favorite)
            _state.update { current ->
                current.copy(
                    selected = current.selected?.takeIf { it.id == item.id }?.copy(favorite = !item.favorite)
                        ?: current.selected,
                )
            }
        }
    }

    fun delete(item: Analysis) {
        viewModelScope.launch {
            container.translations.deleteAnalysis(item.id)
            _state.update { it.copy(selected = null) }
            show("Deleted")
        }
    }

    fun askClearAll(ask: Boolean) {
        _state.update { it.copy(showClearAll = ask) }
    }

    fun clearAll() {
        viewModelScope.launch {
            container.translations.clearHistory()
            _state.update { it.copy(showClearAll = false) }
            show("Translation history cleared")
        }
    }

    fun exportHistory() {
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            try {
                val uri = container.exporter.exportHistory(
                    threads = container.translations.allThreads(),
                    analyses = container.translations.allAnalyses(),
                    vocabulary = container.library.vocabularySnapshot(),
                    grammar = container.library.grammarSnapshot(),
                )
                container.exporter.share(uri, "application/json", "Export AnlaSöz history")
            } catch (e: Exception) {
                show("Export failed: ${e.message?.take(80) ?: "unknown error"}")
            } finally {
                _state.update { it.copy(exporting = false) }
            }
        }
    }

    fun exportVocabulary() {
        viewModelScope.launch {
            try {
                val uri = container.exporter.exportVocabularyCsv(container.library.vocabularySnapshot())
                container.exporter.share(uri, "text/csv", "Export vocabulary")
            } catch (e: Exception) {
                show("Export failed: ${e.message?.take(80) ?: "unknown error"}")
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
