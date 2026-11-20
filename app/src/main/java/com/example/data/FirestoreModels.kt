package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = StudyLanguage.ENGLISH.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudStudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "General",
    val category: String = "NOTE", // NOTE, SUMMARY, AI_ANSWER
    val style: String = "Short Notes",
    val sourceName: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun createdAtMillis(): Long = (createdAt ?: updatedAt)?.toDate()?.time ?: 0L
}

@Keep
@IgnoreExtraProperties
data class CloudQuiz(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "General",
    val sourceType: String = "NOTES", // PDF, DOCX, TXT, IMAGE, NOTES, MOCK_TEST
    val sourceName: String = "",
    val difficulty: String = "Medium", // Easy, Medium, Hard
    val questionType: String = "MCQ", // MCQ, TRUE_FALSE, FILL_BLANKS, SHORT_ANSWER
    val durationMinutes: Int = 10,
    val questionCount: Int = 0,
    val questions: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun createdAtMillis(): Long = (createdAt ?: updatedAt)?.toDate()?.time ?: 0L

    fun toQuizQuestions(): List<QuizQuestion> = questions.mapIndexedNotNull { idx, raw ->
        val questionText = (raw["question"] as? String)?.trim().orEmpty()
        if (questionText.isBlank()) return@mapIndexedNotNull null
        val index = (raw["index"] as? Number)?.toInt() ?: idx
        val rawOptions = raw["options"] as? List<*>
        val options = rawOptions?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val correctAnswer = (raw["correctAnswer"] as? String)?.trim().orEmpty()
        val explanation = (raw["explanation"] as? String)?.trim().orEmpty()
        val typeName = (raw["type"] as? String)?.trim().orEmpty()
        QuizQuestion(
            index = index,
            question = questionText,
            options = options,
            correctAnswer = correctAnswer,
            explanation = explanation,
            type = QuestionType.fromName(typeName.ifBlank { questionType })
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CloudQuizAttempt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val quizId: String = "",
    val quizTitle: String = "",
    val subject: String = "General",
    val mockTest: Boolean = false,
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val percentage: Int = 0,
    val timeSpentSeconds: Int = 0,
    val userAnswers: Map<String, String> = emptyMap(),
    @ServerTimestamp val completedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun completedAtMillis(): Long = (completedAt ?: createdAt ?: updatedAt)?.toDate()?.time ?: 0L

    fun toAnswersMap(): Map<Int, String> = buildMap {
        userAnswers.forEach { (k, v) ->
            k.toIntOrNull()?.let { put(it, v) }
        }
    }
}

object FirestorePayloadMapper {
    private val VALID_CATEGORIES = setOf("NOTE", "SUMMARY", "AI_ANSWER")
    private val VALID_SOURCE_TYPES = setOf("PDF", "DOCX", "TXT", "IMAGE", "NOTES", "MOCK_TEST")
    private val VALID_DIFFICULTIES = setOf("Easy", "Medium", "Hard")
    private val VALID_QUESTION_TYPES = setOf("MCQ", "TRUE_FALSE", "FILL_BLANKS", "SHORT_ANSWER")
    private val VALID_LANGUAGES = StudyLanguage.entries.map { it.name }.toSet()

    fun sanitizeCategory(raw: String): String =
        if (raw in VALID_CATEGORIES) raw else "NOTE"

    fun sanitizeSourceType(raw: String): String =
        if (raw in VALID_SOURCE_TYPES) raw else "NOTES"

    fun sanitizeDifficulty(raw: String): String =
        if (raw in VALID_DIFFICULTIES) raw else QuizDifficulty.fromLabel(raw).label

    fun sanitizeQuestionType(raw: String): String =
        if (raw in VALID_QUESTION_TYPES) raw else QuestionType.fromName(raw).name

    fun sanitizeLanguage(raw: String): String =
        if (raw in VALID_LANGUAGES) raw else StudyLanguage.ENGLISH.name

    fun questionsToMaps(questions: List<QuizQuestion>): List<Map<String, Any>> =
        questions.take(100).mapIndexed { idx, q ->
            mapOf(
                "index" to idx,
                "question" to q.question.trim().take(2000),
                "options" to q.options.take(10).map { it.trim().take(400) },
                "correctAnswer" to q.correctAnswer.trim().take(1000),
                "explanation" to q.explanation.trim().take(2000),
                "type" to sanitizeQuestionType(q.type.name)
            )
        }

    fun answersToStringMap(answers: Map<Int, String>): Map<String, String> = buildMap {
        answers.entries.take(100).forEach { (k, v) ->
            put(k.toString(), v.trim().take(1000))
        }
    }
}
