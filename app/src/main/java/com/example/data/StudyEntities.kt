package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class StudyLanguage(
    val displayName: String,
    val nativeLabel: String,
    val shortCode: String,
    val promptLabel: String
) {
    ENGLISH("English", "English", "EN", "English"),
    TAMIL("Tamil", "தமிழ்", "TA", "Tamil (தமிழ்) with clear academic terminology"),
    HINDI("Hindi", "हिन्दी", "HI", "Hindi (हिन्दी) with clear academic terminology"),
    MALAYALAM("Malayalam", "മലയാളം", "ML", "Malayalam (മലയാളം) with clear academic terminology"),
    TELUGU("Telugu", "తెలుగు", "TE", "Telugu (తెలుగు) with clear academic terminology"),
    KANNADA("Kannada", "ಕನ್ನಡ", "KN", "Kannada (ಕನ್ನಡ) with clear academic terminology");

    companion object {
        fun fromName(name: String?): StudyLanguage =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ENGLISH
    }
}

enum class QuizDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    companion object {
        fun fromLabel(label: String): QuizDifficulty =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) || it.name.equals(label, ignoreCase = true) }
                ?: MEDIUM
    }
}

enum class QuestionType(val label: String, val shortLabel: String) {
    MCQ("Multiple Choice", "MCQ"),
    TRUE_FALSE("True or False", "True/False"),
    FILL_BLANKS("Fill in the Blanks", "Blanks"),
    SHORT_ANSWER("Short Answer", "Short Ans");

    companion object {
        fun fromName(name: String): QuestionType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) }
                ?: MCQ
    }
}

data class QuizQuestion(
    val index: Int,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val type: QuestionType = QuestionType.MCQ
)

data class FlashcardItem(
    val id: Int,
    val front: String,
    val back: String,
    val hint: String = "",
    val mastered: Boolean = false
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val sourceType: String, // PDF, DOCX, TXT, IMAGE, NOTES, MOCK_TEST
    val sourceName: String,
    val difficulty: String,
    val questionType: String,
    val durationMinutes: Int,
    val questionCount: Int,
    val questionsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val quizTitle: String,
    val subject: String,
    val isMockTest: Boolean,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Int,
    val timeSpentSeconds: Int,
    val userAnswersJson: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val category: String, // NOTE, SUMMARY, AI_ANSWER
    val style: String,
    val sourceName: String = "",
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcard_decks")
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val cardsJson: String,
    val masteredCount: Int,
    val totalCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val text: String,
    val subject: String,
    val mode: String,
    val hasImage: Boolean = false,
    val savedToLibrary: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "planner_tasks")
data class PlannerTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val dayLabel: String, // e.g., "Today", "Mon", "Tue"
    val timeSlot: String, // e.g., "06:30 PM - 07:30 PM"
    val reminderLabel: String, // e.g., "15 min before"
    val durationMinutes: Int = 45,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exam_countdowns")
data class ExamCountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examName: String,
    val subject: String,
    val targetEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "uploaded_docs")
data class UploadedDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val preview: String,
    val uploadedAt: Long = System.currentTimeMillis()
)

object StudyJsonCodec {
    fun encodeQuestions(questions: List<QuizQuestion>): String {
        val array = JSONArray()
        questions.forEach { q ->
            val obj = JSONObject().apply {
                put("index", q.index)
                put("question", q.question)
                put("options", JSONArray(q.options))
                put("correctAnswer", q.correctAnswer)
                put("explanation", q.explanation)
                put("type", q.type.name)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeQuestions(json: String): List<QuizQuestion> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                val optArr = obj.optJSONArray("options")
                val options = if (optArr != null) {
                    (0 until optArr.length()).map { optArr.optString(it) }
                } else {
                    emptyList()
                }
                QuizQuestion(
                    index = obj.optInt("index", idx),
                    question = obj.optString("question"),
                    options = options,
                    correctAnswer = obj.optString("correctAnswer"),
                    explanation = obj.optString("explanation"),
                    type = QuestionType.fromName(obj.optString("type", QuestionType.MCQ.name))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeAnswersMap(answers: Map<Int, String>): String {
        val obj = JSONObject()
        answers.forEach { (k, v) -> obj.put(k.toString(), v) }
        return obj.toString()
    }

    fun decodeAnswersMap(json: String): Map<Int, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<Int, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                key.toIntOrNull()?.let { result[it] = obj.optString(key) }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeFlashcards(cards: List<FlashcardItem>): String {
        val array = JSONArray()
        cards.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("front", c.front)
                put("back", c.back)
                put("hint", c.hint)
                put("mastered", c.mastered)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeFlashcards(json: String): List<FlashcardItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { idx ->
                val obj = array.getJSONObject(idx)
                FlashcardItem(
                    id = obj.optInt("id", idx),
                    front = obj.optString("front"),
                    back = obj.optString("back"),
                    hint = obj.optString("hint"),
                    mastered = obj.optBoolean("mastered", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
