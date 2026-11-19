package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.FlashcardItem
import com.example.data.QuestionType
import com.example.data.QuizDifficulty
import com.example.data.QuizQuestion
import com.example.data.StudyLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "StudyAiService"
private const val MODEL_DEFAULT = "gemini-3.8-flash"

data class GeneratedQuizPayload(
    val title: String,
    val subject: String,
    val questions: List<QuizQuestion>
)

data class GeneratedFlashcardsPayload(
    val deckTitle: String,
    val subject: String,
    val cards: List<FlashcardItem>
)

class StudyAiService {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_DEFAULT)
    }

    private val quizSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short descriptive quiz title"),
            "subject" to Schema.string(description = "Academic subject of the quiz"),
            "questions" to Schema.array(
                description = "Generated quiz questions strictly grounded in the source material or topic",
                items = Schema.obj(
                    properties = mapOf(
                        "question" to Schema.string(description = "Clear educational question"),
                        "options" to Schema.array(
                            items = Schema.string(),
                            description = "Exactly 4 answer choices for MCQ, 2 choices (True, False) for True/False, or empty array for Fill in the Blanks / Short Answer"
                        ),
                        "correctAnswer" to Schema.string(description = "Exact text of the correct answer matching one option when options are provided"),
                        "explanation" to Schema.string(description = "Concise, clear educational explanation of why the answer is correct")
                    )
                )
            )
        )
    )

    private val quizModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )
    }

    private val flashcardsSchema = Schema.obj(
        properties = mapOf(
            "deckTitle" to Schema.string(description = "Concise deck title"),
            "subject" to Schema.string(description = "Academic subject"),
            "cards" to Schema.array(
                description = "Study flashcards with question front, answer back, and helpful memory hint",
                items = Schema.obj(
                    properties = mapOf(
                        "front" to Schema.string(description = "Clear question, concept, or formula prompt"),
                        "back" to Schema.string(description = "Concise, accurate answer or definition"),
                        "hint" to Schema.string(description = "Short mnemonic or cue to help recall")
                    )
                )
            )
        )
    )

    private val flashcardsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashcardsSchema
            }
        )
    }

    fun streamStudyAssistant(
        question: String,
        subject: String,
        mode: String,
        language: StudyLanguage,
        recentHistory: List<Pair<String, String>>,
        attachedBitmap: Bitmap? = null
    ): Flow<String> = flow {
        val systemPrompt = buildString {
            appendLine("You are Study Genius AI, a clear, encouraging academic tutor.")
            appendLine("Respond in ${language.promptLabel}.")
            appendLine("Subject focus: $subject.")
            appendLine("Explanation style: $mode.")
            appendLine("Structure your answer clearly with headings, step-by-step reasoning, concrete examples, and clear math formatting when relevant.")
            appendLine("If an image is attached, carefully read every formula, diagram, or text visible in the image before solving step by step.")
        }

        val inputContent = content {
            text(systemPrompt)
            if (recentHistory.isNotEmpty()) {
                val historySnippet = recentHistory.takeLast(6).joinToString("\n") { (role, msg) ->
                    "$role: ${msg.take(600)}"
                }
                text("Recent conversation context:\n$historySnippet")
            }
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text("Student question:\n$question")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI returned an empty response. Please retry.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Study assistant stream timed out", e)
            throw IllegalStateException("Request timed out after 60 seconds. Check your connection and retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Study assistant stream failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Could not reach Gemini AI. Please retry.")
        }
    }

    suspend fun generateQuizFromMaterial(
        material: PreparedMaterial?,
        pastedNotes: String,
        subject: String,
        topic: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        questionType: QuestionType,
        language: StudyLanguage,
        isMockTest: Boolean
    ): GeneratedQuizPayload {
        val typeInstruction = when (questionType) {
            QuestionType.MCQ -> "Multiple Choice Questions with EXACTLY 4 distinct options per question. correctAnswer MUST match one option verbatim."
            QuestionType.TRUE_FALSE -> "True or False questions with EXACTLY 2 options: [\"True\", \"False\"]. correctAnswer MUST be \"True\" or \"False\"."
            QuestionType.FILL_BLANKS -> "Fill in the Blanks questions using '_____' for the missing term. Provide an empty options array []. correctAnswer should be the concise missing word or phrase."
            QuestionType.SHORT_ANSWER -> "Short Answer questions requiring a 1-2 sentence or formula response. Provide an empty options array []. correctAnswer should be the key expected answer."
        }

        val promptInstructions = buildString {
            appendLine("You are Study Genius AI, an expert academic assessment creator.")
            appendLine("Language: Write all questions, options, answers, and explanations in ${language.promptLabel}.")
            appendLine("Generate EXACTLY $questionCount questions.")
            appendLine("Difficulty level: ${difficulty.label}.")
            appendLine("Question format: $typeInstruction")
            appendLine("Subject: ${subject.ifBlank { "General Studies" }}.")
            if (isMockTest) {
                appendLine("This is a realistic timed Mock Exam on topic: ${topic.ifBlank { subject }}. Cover key exam-relevant concepts with progressive depth.")
            } else {
                appendLine("STRICT GROUNDING RULE: Generate questions ONLY from the provided study material (document, image, or notes). Do NOT invent facts outside the source material.")
                appendLine("If an image is provided, read all text, formulas, labels, and diagrams carefully.")
            }
            appendLine("Every question MUST include a clear, concise educational explanation.")
        }

        val inputContent = content {
            text(promptInstructions)
            when (material) {
                is PreparedMaterial.Pdf -> {
                    inlineData(material.bytes, material.mimeType)
                    text("Source document name: ${material.displayName}")
                }
                is PreparedMaterial.Image -> {
                    image(material.bitmap)
                    text("Source study image: ${material.displayName}")
                }
                is PreparedMaterial.Text -> {
                    text("Source document (${material.displayName}):\n${material.extractedText}")
                }
                null -> Unit
            }
            if (pastedNotes.isNotBlank()) {
                text("Additional study notes:\n${pastedNotes.take(40_000)}")
            }
        }

        try {
            val response = withTimeout(60_000L) {
                quizModel.generateContent(inputContent)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty quiz response.")

            return parseQuizPayload(rawJson, subject, questionType)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quiz generation timed out", e)
            throw IllegalStateException("Quiz generation timed out after 60 seconds. Try fewer questions or a smaller document.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to generate quiz. Please check your connection and retry.")
        }
    }

    private fun parseQuizPayload(
        rawJson: String,
        fallbackSubject: String,
        questionType: QuestionType
    ): GeneratedQuizPayload {
        val root = JSONObject(rawJson)
        val title = root.optString("title").ifBlank { "AI Study Quiz" }
        val subject = root.optString("subject").ifBlank { fallbackSubject.ifBlank { "General" } }
        val arr = root.getJSONArray("questions")
        val questions = mutableListOf<QuizQuestion>()

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val qText = item.optString("question").trim()
            if (qText.isEmpty()) continue
            val optArr = item.optJSONArray("options")
            val rawOptions = mutableListOf<String>()
            if (optArr != null) {
                for (j in 0 until optArr.length()) {
                    val opt = optArr.optString(j).trim()
                    if (opt.isNotEmpty()) rawOptions.add(opt)
                }
            }
            val finalOptions = when (questionType) {
                QuestionType.TRUE_FALSE -> listOf("True", "False")
                QuestionType.MCQ -> rawOptions.take(4)
                QuestionType.FILL_BLANKS, QuestionType.SHORT_ANSWER -> emptyList()
            }
            val correct = item.optString("correctAnswer").trim()
            val explanation = item.optString("explanation").trim()
                .ifBlank { "Review the core concept from your study material." }

            questions.add(
                QuizQuestion(
                    index = questions.size,
                    question = qText,
                    options = finalOptions,
                    correctAnswer = correct,
                    explanation = explanation,
                    type = questionType
                )
            )
        }

        if (questions.isEmpty()) {
            throw IllegalStateException("Could not extract valid questions from the provided content. Try clearer study material.")
        }

        return GeneratedQuizPayload(
            title = title,
            subject = subject,
            questions = questions
        )
    }

    fun streamNotesOrSummary(
        isSummarizer: Boolean,
        noteStyle: String,
        subject: String,
        topicOrNotes: String,
        material: PreparedMaterial?,
        language: StudyLanguage
    ): Flow<String> = flow {
        val prompt = buildString {
            appendLine("You are Study Genius AI, an expert academic note-maker and textbook summarizer.")
            appendLine("Write strictly in ${language.promptLabel}.")
            appendLine("Subject: ${subject.ifBlank { "General Studies" }}.")
            if (isSummarizer) {
                appendLine("Produce a comprehensive, well-structured PDF / Document Summary organized into these clear sections:")
                appendLine("1. Chapter Summary")
                appendLine("2. Main Topics")
                appendLine("3. Important Concepts")
                appendLine("4. Key Definitions")
                appendLine("5. Important Formulas & Rules (if applicable)")
                appendLine("6. Quick Revision Notes")
                appendLine("Include page numbers or section references whenever detectable from the document.")
                appendLine("Base the summary strictly on the provided source material.")
            } else {
                appendLine("Generate high-clarity '$noteStyle' designed for fast student comprehension and exam retention.")
                appendLine("Use clean section headers, bullet points, highlighted definitions, key formulas, and quick memory tips.")
            }
        }

        val inputContent = content {
            text(prompt)
            when (material) {
                is PreparedMaterial.Pdf -> {
                    inlineData(material.bytes, material.mimeType)
                    text("Document name: ${material.displayName}")
                }
                is PreparedMaterial.Image -> {
                    image(material.bitmap)
                    text("Study image: ${material.displayName}")
                }
                is PreparedMaterial.Text -> {
                    text("Source text (${material.displayName}):\n${material.extractedText}")
                }
                null -> Unit
            }
            if (topicOrNotes.isNotBlank()) {
                text("Topic / Student input:\n${topicOrNotes.take(40_000)}")
            }
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI returned an empty output. Please retry.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Notes/summary generation timed out", e)
            throw IllegalStateException("Generation timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Notes/summary generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to generate content. Please retry.")
        }
    }

    suspend fun generateFlashcards(
        subject: String,
        topicOrNotes: String,
        material: PreparedMaterial?,
        cardCount: Int,
        language: StudyLanguage
    ): GeneratedFlashcardsPayload {
        val prompt = buildString {
            appendLine("You are Study Genius AI. Create EXACTLY $cardCount high-retention active-recall flashcards.")
            appendLine("Write in ${language.promptLabel}.")
            appendLine("Subject: ${subject.ifBlank { "General Studies" }}.")
            appendLine("Each card must have a focused question or concept on the front, a crisp accurate answer on the back, and a short recall hint.")
            if (material != null) {
                appendLine("Base all flashcards strictly on the provided source document or image.")
            }
        }

        val inputContent = content {
            text(prompt)
            when (material) {
                is PreparedMaterial.Pdf -> inlineData(material.bytes, material.mimeType)
                is PreparedMaterial.Image -> image(material.bitmap)
                is PreparedMaterial.Text -> text("Source material:\n${material.extractedText}")
                null -> Unit
            }
            if (topicOrNotes.isNotBlank()) {
                text("Topic or notes:\n${topicOrNotes.take(30_000)}")
            }
        }

        try {
            val response = withTimeout(60_000L) {
                flashcardsModel.generateContent(inputContent)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned empty flashcards.")

            val root = JSONObject(rawJson)
            val title = root.optString("deckTitle").ifBlank { "$subject Flashcards" }
            val resolvedSubject = root.optString("subject").ifBlank { subject.ifBlank { "General" } }
            val arr = root.getJSONArray("cards")
            val cards = mutableListOf<FlashcardItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val front = obj.optString("front").trim()
                val back = obj.optString("back").trim()
                val hint = obj.optString("hint").trim()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    cards.add(
                        FlashcardItem(
                            id = i,
                            front = front,
                            back = back,
                            hint = hint,
                            mastered = false
                        )
                    )
                }
            }

            if (cards.isEmpty()) {
                throw IllegalStateException("No valid flashcards could be generated from this input.")
            }

            return GeneratedFlashcardsPayload(
                deckTitle = title,
                subject = resolvedSubject,
                cards = cards
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flashcard generation timed out", e)
            throw IllegalStateException("Flashcard generation timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to generate flashcards. Please retry.")
        }
    }
}
