package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocumentProcessor
import com.example.ai.MaterialReadResult
import com.example.ai.PreparedMaterial
import com.example.ai.StudyAiService
import com.example.data.ChatMessageEntity
import com.example.data.StudyLanguage
import com.example.data.StudyNoteEntity
import com.example.data.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val selectedSubject: String = "General",
    val selectedMode: String = "Step-by-Step",
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val attachedImage: Bitmap? = null,
    val attachedImageName: String? = null,
    val errorMessage: String? = null,
    val lastUserPrompt: String? = null,
    val snackbarMessage: String? = null
)

class ChatViewModel(
    private val repository: StudyRepository,
    private val aiService: StudyAiService
) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setSubject(subject: String) {
        _uiState.update { it.copy(selectedSubject = subject) }
    }

    fun setMode(mode: String) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun attachImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val res = DocumentProcessor.processUri(context, uri)) {
                is MaterialReadResult.Success -> {
                    val mat = res.material
                    if (mat is PreparedMaterial.Image) {
                        _uiState.update {
                            it.copy(
                                attachedImage = mat.bitmap,
                                attachedImageName = mat.displayName,
                                selectedMode = "Doubt Solver",
                                errorMessage = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(errorMessage = "Please choose an image file for visual doubt solving.")
                        }
                    }
                }
                is MaterialReadResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.errorMessage) }
                }
            }
        }
    }

    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImage = null, attachedImageName = null) }
    }

    fun sendMessage(prompt: String, language: StudyLanguage) {
        val trimmed = prompt.trim()
        val state = _uiState.value
        if (state.isStreaming) return
        if (trimmed.isEmpty() && state.attachedImage == null) return

        val effectivePrompt = trimmed.ifEmpty {
            "Solve the educational problem or explain the concept shown in this image step by step."
        }
        val bitmapToSend = state.attachedImage

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStreaming = true,
                    streamingText = "",
                    errorMessage = null,
                    lastUserPrompt = effectivePrompt,
                    attachedImage = null,
                    attachedImageName = null
                )
            }

            try {
                val historyPairs = messages.value.takeLast(6).map { it.role to it.text }
                repository.insertChatMessage(
                    ChatMessageEntity(
                        role = "user",
                        text = effectivePrompt,
                        subject = state.selectedSubject,
                        mode = state.selectedMode,
                        hasImage = bitmapToSend != null
                    )
                )

                var finalAnswer = ""
                aiService.streamStudyAssistant(
                    question = effectivePrompt,
                    subject = state.selectedSubject,
                    mode = state.selectedMode,
                    language = language,
                    recentHistory = historyPairs,
                    attachedBitmap = bitmapToSend
                ).collect { partial ->
                    finalAnswer = partial
                    _uiState.update { it.copy(streamingText = partial) }
                }

                if (finalAnswer.isNotBlank()) {
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "assistant",
                            text = finalAnswer,
                            subject = state.selectedSubject,
                            mode = state.selectedMode
                        )
                    )
                }
                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = e.localizedMessage ?: "Failed to get an AI response. Please retry."
                    )
                }
            }
        }
    }

    fun regenerateLastAnswer(language: StudyLanguage) {
        if (_uiState.value.isStreaming) return
        val currentMessages = messages.value
        val lastUser = currentMessages.lastOrNull { it.role == "user" }
            ?: _uiState.value.lastUserPrompt?.let {
                ChatMessageEntity(role = "user", text = it, subject = _uiState.value.selectedSubject, mode = _uiState.value.selectedMode)
            } ?: return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = null) }
            try {
                val lastMsg = currentMessages.lastOrNull()
                if (lastMsg != null && lastMsg.role == "assistant") {
                    repository.deleteChatMessage(lastMsg.id)
                }

                val historyPairs = currentMessages
                    .dropLastWhile { it.role == "assistant" }
                    .dropLast(1)
                    .takeLast(6)
                    .map { it.role to it.text }

                var finalAnswer = ""
                aiService.streamStudyAssistant(
                    question = lastUser.text,
                    subject = lastUser.subject,
                    mode = lastUser.mode,
                    language = language,
                    recentHistory = historyPairs
                ).collect { partial ->
                    finalAnswer = partial
                    _uiState.update { it.copy(streamingText = partial) }
                }

                if (finalAnswer.isNotBlank()) {
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            role = "assistant",
                            text = finalAnswer,
                            subject = lastUser.subject,
                            mode = lastUser.mode
                        )
                    )
                }
                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = e.localizedMessage ?: "Regeneration failed. Please retry."
                    )
                }
            }
        }
    }

    fun saveAnswerToLibrary(message: ChatMessageEntity) {
        viewModelScope.launch {
            val title = message.text.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.replace(Regex("^[#*\\-\\s]+"), "")
                ?.take(56)
                ?.ifBlank { "Saved AI Explanation" }
                ?: "Saved AI Explanation"

            repository.saveNote(
                StudyNoteEntity(
                    title = title,
                    subject = message.subject,
                    category = "AI_ANSWER",
                    style = message.mode,
                    content = message.text
                )
            )
            repository.markChatMessageSaved(message.id)
            _uiState.update { it.copy(snackbarMessage = "Saved answer to My Library") }
        }
    }

    fun clearChat() {
        activeJob?.cancel()
        viewModelScope.launch {
            repository.clearChatHistory()
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingText = "",
                    errorMessage = null,
                    snackbarMessage = "Conversation cleared"
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
