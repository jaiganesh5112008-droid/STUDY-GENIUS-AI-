package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocumentProcessor
import com.example.ai.MaterialReadResult
import com.example.ai.PreparedMaterial
import com.example.ai.StudyAiService
import com.example.data.FlashcardDeckEntity
import com.example.data.FlashcardItem
import com.example.data.StudyJsonCodec
import com.example.data.StudyLanguage
import com.example.data.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashcardsUiState(
    val activeDeck: FlashcardDeckEntity? = null,
    val activeCards: List<FlashcardItem> = emptyList(),
    val visibleIndices: List<Int> = emptyList(),
    val currentVisiblePos: Int = 0,
    val isFlipped: Boolean = false,
    val revisionModeOnlyMissed: Boolean = false,
    val showGeneratorSheet: Boolean = false,
    val genSubject: String = "Biology",
    val genTopic: String = "",
    val genCount: Int = 10,
    val genMaterial: PreparedMaterial? = null,
    val isReadingFile: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

class FlashcardsViewModel(
    private val repository: StudyRepository,
    private val aiService: StudyAiService
) : ViewModel() {

    val decks: StateFlow<List<FlashcardDeckEntity>> = repository.flashcardDecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(FlashcardsUiState())
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()

    fun openDeck(deckId: Long) {
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId) ?: return@launch
            val cards = StudyJsonCodec.decodeFlashcards(deck.cardsJson)
            _uiState.update {
                it.copy(
                    activeDeck = deck,
                    activeCards = cards,
                    visibleIndices = cards.indices.toList(),
                    currentVisiblePos = 0,
                    isFlipped = false,
                    revisionModeOnlyMissed = false,
                    errorMessage = null
                )
            }
        }
    }

    fun closeActiveDeck() {
        _uiState.update { it.copy(activeDeck = null, isFlipped = false) }
    }

    fun toggleFlip() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun toggleRevisionMode() {
        val state = _uiState.value
        val newRevisionMode = !state.revisionModeOnlyMissed
        val indices = if (newRevisionMode) {
            state.activeCards.indices.filter { !state.activeCards[it].mastered }
        } else {
            state.activeCards.indices.toList()
        }
        _uiState.update {
            it.copy(
                revisionModeOnlyMissed = newRevisionMode,
                visibleIndices = indices,
                currentVisiblePos = 0,
                isFlipped = false
            )
        }
    }

    fun markCurrentCard(mastered: Boolean) {
        val state = _uiState.value
        val deck = state.activeDeck ?: return
        val realIdx = state.visibleIndices.getOrNull(state.currentVisiblePos) ?: return

        val updatedCards = state.activeCards.mapIndexed { idx, card ->
            if (idx == realIdx) card.copy(mastered = mastered) else card
        }
        val masteredCount = updatedCards.count { it.mastered }
        val updatedDeck = deck.copy(
            cardsJson = StudyJsonCodec.encodeFlashcards(updatedCards),
            masteredCount = masteredCount,
            totalCount = updatedCards.size
        )

        val updatedVisible = if (state.revisionModeOnlyMissed) {
            updatedCards.indices.filter { !updatedCards[it].mastered }
        } else {
            updatedCards.indices.toList()
        }

        val nextPos = if (state.revisionModeOnlyMissed) {
            state.currentVisiblePos.coerceAtMost((updatedVisible.size - 1).coerceAtLeast(0))
        } else {
            (state.currentVisiblePos + 1).coerceAtMost((updatedVisible.size - 1).coerceAtLeast(0))
        }

        _uiState.update {
            it.copy(
                activeDeck = updatedDeck,
                activeCards = updatedCards,
                visibleIndices = updatedVisible,
                currentVisiblePos = nextPos,
                isFlipped = false
            )
        }

        viewModelScope.launch {
            repository.updateFlashcardDeck(updatedDeck)
        }
    }

    fun nextCard() {
        val state = _uiState.value
        if (state.currentVisiblePos < state.visibleIndices.lastIndex) {
            _uiState.update { it.copy(currentVisiblePos = it.currentVisiblePos + 1, isFlipped = false) }
        }
    }

    fun previousCard() {
        val state = _uiState.value
        if (state.currentVisiblePos > 0) {
            _uiState.update { it.copy(currentVisiblePos = it.currentVisiblePos - 1, isFlipped = false) }
        }
    }

    fun setShowGenerator(show: Boolean) {
        _uiState.update { it.copy(showGeneratorSheet = show, errorMessage = null) }
    }

    fun setGenSubject(subject: String) {
        _uiState.update { it.copy(genSubject = subject) }
    }

    fun setGenTopic(topic: String) {
        _uiState.update { it.copy(genTopic = topic, errorMessage = null) }
    }

    fun setGenCount(count: Int) {
        _uiState.update { it.copy(genCount = count.coerceIn(4, 20)) }
    }

    fun clearGenMaterial() {
        _uiState.update { it.copy(genMaterial = null) }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingFile = true, errorMessage = null) }
            when (val res = DocumentProcessor.processUri(context, uri)) {
                is MaterialReadResult.Success -> {
                    repository.recordUploadedDocument(res.material)
                    _uiState.update { it.copy(genMaterial = res.material, isReadingFile = false) }
                }
                is MaterialReadResult.Failure -> {
                    _uiState.update { it.copy(isReadingFile = false, errorMessage = res.errorMessage) }
                }
            }
        }
    }

    fun generateDeck(language: StudyLanguage) {
        val state = _uiState.value
        if (state.isGenerating || state.isReadingFile) return
        if (state.genMaterial == null && state.genTopic.trim().length < 3) {
            _uiState.update { it.copy(errorMessage = "Enter a topic, paste notes, or attach a file to generate flashcards.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            try {
                val payload = aiService.generateFlashcards(
                    subject = state.genSubject,
                    topicOrNotes = state.genTopic,
                    material = state.genMaterial,
                    cardCount = state.genCount,
                    language = language
                )
                val deckEntity = FlashcardDeckEntity(
                    title = payload.deckTitle,
                    subject = payload.subject,
                    cardsJson = StudyJsonCodec.encodeFlashcards(payload.cards),
                    masteredCount = 0,
                    totalCount = payload.cards.size
                )
                val id = repository.saveFlashcardDeck(deckEntity)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        showGeneratorSheet = false,
                        genTopic = "",
                        genMaterial = null
                    )
                }
                openDeck(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Failed to generate flashcards. Please retry."
                    )
                }
            }
        }
    }

    fun deleteDeck(id: Long) {
        viewModelScope.launch {
            repository.deleteFlashcardDeck(id)
            if (_uiState.value.activeDeck?.id == id) {
                _uiState.update { it.copy(activeDeck = null) }
            }
        }
    }
}
