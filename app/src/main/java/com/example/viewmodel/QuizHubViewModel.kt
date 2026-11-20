package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocumentProcessor
import com.example.ai.MaterialReadResult
import com.example.ai.PreparedMaterial
import com.example.ai.StudyAiService
import com.example.data.QuestionType
import com.example.data.QuizDifficulty
import com.example.data.StudyLanguage
import com.example.data.StudyRepository
import com.example.data.friendlyFirestoreMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizBuilderMode(val title: String, val subtitle: String) {
    PDF_TO_QUIZ("PDF & Doc to Quiz", "Upload PDF, DOCX, TXT, Image, or paste notes"),
    IMAGE_TO_QUIZ("Image to Quiz", "Snap textbook pages or diagrams to extract questions"),
    MOCK_TEST("Mock Test Generator", "Create realistic timed practice exams by subject")
}

data class QuizBuilderUiState(
    val mode: QuizBuilderMode = QuizBuilderMode.PDF_TO_QUIZ,
    val material: PreparedMaterial? = null,
    val pastedNotes: String = "",
    val subject: String = "Science",
    val topic: String = "",
    val questionCount: Int = 8,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    val questionType: QuestionType = QuestionType.MCQ,
    val durationMinutes: Int = 10,
    val isReadingFile: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val generatedQuizId: String? = null
)

class QuizHubViewModel(
    private val repository: StudyRepository,
    private val aiService: StudyAiService,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizBuilderUiState())
    val uiState: StateFlow<QuizBuilderUiState> = _uiState.asStateFlow()

    fun setMode(mode: QuizBuilderMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                errorMessage = null,
                durationMinutes = if (mode == QuizBuilderMode.MOCK_TEST) 20 else 10,
                questionCount = if (mode == QuizBuilderMode.MOCK_TEST) 12 else 8
            )
        }
    }

    fun setPastedNotes(text: String) {
        _uiState.update { it.copy(pastedNotes = text, errorMessage = null) }
    }

    fun setSubject(subject: String) {
        _uiState.update { it.copy(subject = subject, errorMessage = null) }
    }

    fun setTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, errorMessage = null) }
    }

    fun setQuestionCount(count: Int) {
        _uiState.update { it.copy(questionCount = count.coerceIn(3, 20)) }
    }

    fun setDifficulty(difficulty: QuizDifficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun setQuestionType(type: QuestionType) {
        _uiState.update { it.copy(questionType = type) }
    }

    fun setDurationMinutes(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes.coerceIn(3, 90)) }
    }

    fun removeMaterial() {
        _uiState.update { it.copy(material = null, errorMessage = null) }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingFile = true, errorMessage = null) }
            when (val result = DocumentProcessor.processUri(context, uri)) {
                is MaterialReadResult.Success -> {
                    repository.recordUploadedDocument(result.material)
                    _uiState.update {
                        it.copy(
                            material = result.material,
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
                            errorMessage = result.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun generateQuiz(language: StudyLanguage) {
        val state = _uiState.value
        if (state.isGenerating || state.isReadingFile) return

        val isMock = state.mode == QuizBuilderMode.MOCK_TEST
        if (isMock) {
            if (state.subject.isBlank() && state.topic.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please select a subject or enter a topic for the mock test.") }
                return
            }
        } else {
            if (state.material == null && state.pastedNotes.trim().length < 10) {
                val msg = if (state.mode == QuizBuilderMode.IMAGE_TO_QUIZ) {
                    "Please capture a photo or upload a study image first."
                } else {
                    "Please upload a readable PDF, DOCX, TXT, Image, or paste your study notes first."
                }
                _uiState.update { it.copy(errorMessage = msg) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            try {
                val payload = aiService.generateQuizFromMaterial(
                    material = if (isMock) null else state.material,
                    pastedNotes = if (isMock) "" else state.pastedNotes,
                    subject = state.subject,
                    topic = state.topic,
                    questionCount = state.questionCount,
                    difficulty = state.difficulty,
                    questionType = state.questionType,
                    language = language,
                    isMockTest = isMock
                )

                val sourceType = when {
                    isMock -> "MOCK_TEST"
                    state.material is PreparedMaterial.Pdf -> "PDF"
                    state.material is PreparedMaterial.Image -> "IMAGE"
                    state.material is PreparedMaterial.Text ->
                        if (state.material.displayName.lowercase().endsWith(".docx")) "DOCX" else "TXT"
                    else -> "NOTES"
                }

                val saveResult = repository.saveCloudQuiz(
                    userId = currentUserId,
                    title = payload.title,
                    subject = payload.subject,
                    sourceType = sourceType,
                    sourceName = state.material?.displayName ?: state.topic.ifBlank { state.subject },
                    difficulty = state.difficulty.label,
                    questionType = state.questionType.name,
                    durationMinutes = state.durationMinutes,
                    questions = payload.questions
                )

                saveResult.onSuccess { quizId ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            generatedQuizId = quizId
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = friendlyFirestoreMessage(err, "Quiz generated, but failed to save to cloud.")
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.localizedMessage ?: "Quiz generation failed. Please try again."
                    )
                }
            }
        }
    }

    fun consumeGeneratedQuizNavigation() {
        _uiState.update { it.copy(generatedQuizId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
