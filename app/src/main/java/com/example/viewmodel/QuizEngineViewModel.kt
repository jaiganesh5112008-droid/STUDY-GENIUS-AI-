package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QuestionType
import com.example.data.QuizAttemptEntity
import com.example.data.QuizEntity
import com.example.data.QuizQuestion
import com.example.data.StudyJsonCodec
import com.example.data.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GradedQuestionResult(
    val question: QuizQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
)

data class QuizEngineUiState(
    val isLoading: Boolean = true,
    val quiz: QuizEntity? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: Map<Int, String> = emptyMap(),
    val markedForReview: Set<Int> = emptySet(),
    val remainingSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val isSubmitted: Boolean = false,
    val gradedResults: List<GradedQuestionResult> = emptyList(),
    val score: Int = 0,
    val percentage: Int = 0,
    val errorMessage: String? = null
)

class QuizEngineViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizEngineUiState())
    val uiState: StateFlow<QuizEngineUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun loadQuiz(quizId: Long) {
        timerJob?.cancel()
        viewModelScope.launch {
            _uiState.value = QuizEngineUiState(isLoading = true)
            val entity = repository.getQuizById(quizId)
            if (entity == null) {
                _uiState.value = QuizEngineUiState(
                    isLoading = false,
                    errorMessage = "Quiz could not be found in local storage."
                )
                return@launch
            }
            val parsedQuestions = StudyJsonCodec.decodeQuestions(entity.questionsJson)
            if (parsedQuestions.isEmpty()) {
                _uiState.value = QuizEngineUiState(
                    isLoading = false,
                    errorMessage = "This quiz contains no valid questions."
                )
                return@launch
            }
            val totalSecs = (entity.durationMinutes.coerceAtLeast(1)) * 60
            _uiState.value = QuizEngineUiState(
                isLoading = false,
                quiz = entity,
                questions = parsedQuestions,
                currentIndex = 0,
                userAnswers = emptyMap(),
                markedForReview = emptySet(),
                remainingSeconds = totalSecs,
                totalDurationSeconds = totalSecs,
                isSubmitted = false
            )
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (!_uiState.value.isSubmitted && _uiState.value.remainingSeconds > 0) {
                delay(1_000L)
                val next = _uiState.value.remainingSeconds - 1
                _uiState.update { it.copy(remainingSeconds = next.coerceAtLeast(0)) }
                if (next <= 0) {
                    submitQuiz()
                    break
                }
            }
        }
    }

    fun selectAnswer(questionIndex: Int, answer: String) {
        if (_uiState.value.isSubmitted) return
        _uiState.update { state ->
            state.copy(userAnswers = state.userAnswers + (questionIndex to answer))
        }
    }

    fun toggleMarkForReview(questionIndex: Int) {
        if (_uiState.value.isSubmitted) return
        _uiState.update { state ->
            val updated = if (questionIndex in state.markedForReview) {
                state.markedForReview - questionIndex
            } else {
                state.markedForReview + questionIndex
            }
            state.copy(markedForReview = updated)
        }
    }

    fun goToQuestion(index: Int) {
        val count = _uiState.value.questions.size
        if (index in 0 until count) {
            _uiState.update { it.copy(currentIndex = index) }
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex < state.questions.lastIndex) {
            _uiState.update { it.copy(currentIndex = state.currentIndex + 1) }
        }
    }

    fun previousQuestion() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            _uiState.update { it.copy(currentIndex = state.currentIndex - 1) }
        }
    }

    fun submitQuiz() {
        val state = _uiState.value
        if (state.isSubmitted || state.quiz == null) return
        timerJob?.cancel()

        val graded = state.questions.mapIndexed { idx, q ->
            val answer = state.userAnswers[idx].orEmpty().trim()
            val isCorrect = evaluateAnswer(q, answer)
            GradedQuestionResult(
                question = q,
                userAnswer = answer,
                isCorrect = isCorrect
            )
        }
        val correctCount = graded.count { it.isCorrect }
        val total = graded.size.coerceAtLeast(1)
        val percentage = ((correctCount * 100.0) / total).toInt()
        val timeSpent = (state.totalDurationSeconds - state.remainingSeconds).coerceAtLeast(1)

        _uiState.update {
            it.copy(
                isSubmitted = true,
                gradedResults = graded,
                score = correctCount,
                percentage = percentage
            )
        }

        viewModelScope.launch {
            repository.saveAttempt(
                QuizAttemptEntity(
                    quizId = state.quiz.id,
                    quizTitle = state.quiz.title,
                    subject = state.quiz.subject,
                    isMockTest = state.quiz.sourceType == "MOCK_TEST",
                    score = correctCount,
                    totalQuestions = graded.size,
                    percentage = percentage,
                    timeSpentSeconds = timeSpent,
                    userAnswersJson = StudyJsonCodec.encodeAnswersMap(state.userAnswers)
                )
            )
        }
    }

    fun retryQuiz() {
        val quiz = _uiState.value.quiz ?: return
        loadQuiz(quiz.id)
    }

    private fun evaluateAnswer(question: QuizQuestion, userAnswer: String): Boolean {
        if (userAnswer.isBlank()) return false
        val expected = question.correctAnswer.trim()
        return when (question.type) {
            QuestionType.MCQ, QuestionType.TRUE_FALSE ->
                userAnswer.equals(expected, ignoreCase = true)
            QuestionType.FILL_BLANKS -> {
                val normUser = normalizeText(userAnswer)
                val normExp = normalizeText(expected)
                normUser == normExp || (normUser.length >= 3 && (normExp.contains(normUser) || normUser.contains(normExp)))
            }
            QuestionType.SHORT_ANSWER -> {
                val normUser = normalizeText(userAnswer)
                val normExp = normalizeText(expected)
                if (normUser == normExp) return true
                val expTokens = normExp.split(" ").filter { it.length > 3 }.toSet()
                if (expTokens.isEmpty()) return normUser.contains(normExp)
                val userTokens = normUser.split(" ").filter { it.length > 3 }.toSet()
                val matches = expTokens.intersect(userTokens).size
                matches.toFloat() / expTokens.size.toFloat() >= 0.45f
            }
        }
    }

    private fun normalizeText(input: String): String =
        input.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u0DFF\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
