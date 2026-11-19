package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocumentProcessor
import com.example.ai.MaterialReadResult
import com.example.ai.PreparedMaterial
import com.example.ai.StudyAiService
import com.example.data.StudyLanguage
import com.example.data.StudyNoteEntity
import com.example.data.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesSummarizerUiState(
    val isSummarizerTab: Boolean = false,
    val subject: String = "Physics",
    val selectedNoteStyle: String = "Short Notes",
    val topicOrInput: String = "",
    val material: PreparedMaterial? = null,
    val isReadingFile: Boolean = false,
    val isGenerating: Boolean = false,
    val generatedContent: String = "",
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null
)

class NotesSummarizerViewModel(
    private val repository: StudyRepository,
    private val aiService: StudyAiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesSummarizerUiState())
    val uiState: StateFlow<NotesSummarizerUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setTab(isSummarizer: Boolean) {
        _uiState.update { it.copy(isSummarizerTab = isSummarizer, errorMessage = null) }
    }

    fun setSubject(subject: String) {
        _uiState.update { it.copy(subject = subject) }
    }

    fun setNoteStyle(style: String) {
        _uiState.update { it.copy(selectedNoteStyle = style) }
    }

    fun setTopicOrInput(input: String) {
        _uiState.update { it.copy(topicOrInput = input, errorMessage = null) }
    }

    fun clearMaterial() {
        _uiState.update { it.copy(material = null) }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingFile = true, errorMessage = null) }
            when (val res = DocumentProcessor.processUri(context, uri)) {
                is MaterialReadResult.Success -> {
                    repository.recordUploadedDocument(res.material)
                    _uiState.update {
                        it.copy(
                            material = res.material,
                            isReadingFile = false,
                            errorMessage = null
                        )
                    }
                }
                is MaterialReadResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            material = null,
                            isReadingFile = false,
                            errorMessage = res.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun generate(language: StudyLanguage) {
        val state = _uiState.value
        if (state.isGenerating || state.isReadingFile) return

        if (state.material == null && state.topicOrInput.trim().length < 4) {
            _uiState.update {
                it.copy(
                    errorMessage = if (state.isSummarizerTab) {
                        "Upload a PDF, DOCX, TXT, Image, or paste chapter text to summarize."
                    } else {
                        "Enter a study topic, paste notes, or attach a document to generate notes."
                    }
                )
            }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generatedContent = "",
                    isSaved = false,
                    errorMessage = null
                )
            }
            try {
                aiService.streamNotesOrSummary(
                    isSummarizer = state.isSummarizerTab,
                    noteStyle = state.selectedNoteStyle,
                    subject = state.subject,
                    topicOrNotes = state.topicOrInput,
                    material = state.material,
                    language = language
                ).collect { chunked ->
                    _uiState.update { it.copy(generatedContent = chunked) }
                }
                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Failed to generate content. Please retry."
                    )
                }
            }
        }
    }

    fun saveToLibrary() {
        val state = _uiState.value
        if (state.generatedContent.isBlank()) return
        viewModelScope.launch {
            val baseTitle = state.material?.displayName
                ?: state.topicOrInput.trim().take(48).ifBlank { state.subject }
            val title = if (state.isSummarizerTab) {
                "Summary: $baseTitle"
            } else {
                "${state.selectedNoteStyle}: $baseTitle"
            }

            repository.saveNote(
                StudyNoteEntity(
                    title = title,
                    subject = state.subject.ifBlank { "General" },
                    category = if (state.isSummarizerTab) "SUMMARY" else "NOTE",
                    style = if (state.isSummarizerTab) "PDF Summary" else state.selectedNoteStyle,
                    sourceName = state.material?.displayName.orEmpty(),
                    content = state.generatedContent
                )
            )
            _uiState.update {
                it.copy(
                    isSaved = true,
                    snackbarMessage = "Saved to My Library"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
