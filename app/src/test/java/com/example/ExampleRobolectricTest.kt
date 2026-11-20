package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.StudyAiService
import com.example.ai.configureAppCheck
import com.example.ai.ensureFirebaseInitialized
import com.example.ai.getFirebaseStatusSummary
import com.example.data.FlashcardItem
import com.example.data.QuestionType
import com.example.data.QuizQuestion
import com.example.data.StudyJsonCodec
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string resources from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Study Genius AI", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.fb_fallback_app_id).isNotBlank())
        assertTrue(context.getString(R.string.fb_fallback_api_key).isNotBlank())
        assertTrue(context.getString(R.string.recaptcha_site_key).isNotBlank())
    }

    @Test
    fun `firebase app app check and StudyAiService models initialize reliably`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.clearInstancesForTest()

        val app = ensureFirebaseInitialized(context)
        assertNotNull("ensureFirebaseInitialized returned null!", app)
        assertNotNull("FirebaseApp.getInstance() should succeed", FirebaseApp.getInstance())

        configureAppCheck(context, null)
        val (ready, summary) = getFirebaseStatusSummary(context)
        assertTrue("Expected Firebase status ready, got: $summary", ready)

        val aiService = StudyAiService(context)
        assertNotNull("chatModel should initialize", aiService.chatModel)
        assertNotNull("quizModel should initialize", aiService.quizModel)
        assertNotNull("flashcardsModel should initialize", aiService.flashcardsModel)
    }

    @Test
    fun `parseQuizPayload normalizes MCQ option letters and True False answers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val aiService = StudyAiService(context)

        val mcqJson = """
            {
              "title": "Physics Laws",
              "subject": "Physics",
              "questions": [
                {
                  "question": "What is the SI unit of force?",
                  "options": ["Joule", "Newton", "Watt", "Pascal"],
                  "correctAnswer": "B",
                  "explanation": "Force is measured in Newtons (kg·m/s²)."
                }
              ]
            }
        """.trimIndent()
        val mcq = aiService.parseQuizPayload(mcqJson, "Physics", QuestionType.MCQ)
        assertEquals("Physics Laws", mcq.title)
        assertEquals(1, mcq.questions.size)
        assertEquals("Newton", mcq.questions.first().correctAnswer)

        val tfJson = """
            {
              "title": "Cell Biology",
              "subject": "Biology",
              "questions": [
                {
                  "question": "Prokaryotes have a membrane-bound nucleus.",
                  "options": ["True", "False"],
                  "correctAnswer": "false",
                  "explanation": "Prokaryotic cells lack a membrane-bound nucleus."
                }
              ]
            }
        """.trimIndent()
        val tf = aiService.parseQuizPayload(tfJson, "Biology", QuestionType.TRUE_FALSE)
        assertEquals("False", tf.questions.first().correctAnswer)
    }

    @Test
    fun `StudyJsonCodec round-trips questions answers and flashcards`() {
        val questions = listOf(
            QuizQuestion(
                index = 0,
                question = "2 + 2 = ?",
                options = listOf("3", "4", "5", "6"),
                correctAnswer = "4",
                explanation = "Basic addition.",
                type = QuestionType.MCQ
            )
        )
        val decodedQuestions = StudyJsonCodec.decodeQuestions(StudyJsonCodec.encodeQuestions(questions))
        assertEquals(questions, decodedQuestions)

        val answers = mapOf(0 to "4", 1 to "True")
        val decodedAnswers = StudyJsonCodec.decodeAnswersMap(StudyJsonCodec.encodeAnswersMap(answers))
        assertEquals(answers, decodedAnswers)

        val cards = listOf(
            FlashcardItem(id = 0, front = "F = ?", back = "ma", hint = "Newton's 2nd law", mastered = true)
        )
        val decodedCards = StudyJsonCodec.decodeFlashcards(StudyJsonCodec.encodeFlashcards(cards))
        assertEquals(cards, decodedCards)
    }

    @Test
    fun `firestore_database_id resource and native CloudQuiz payload mapping work safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue("firestore_database_id must not be blank", dbId.isNotBlank())
        assertTrue("firestore_database_id must not be (default)", dbId != "(default)")

        val questions = listOf(
            QuizQuestion(
                index = 0,
                question = "What is H2O?",
                options = listOf("Water", "Oxygen", "Hydrogen", "Salt"),
                correctAnswer = "Water",
                explanation = "H2O is the chemical formula for water.",
                type = QuestionType.MCQ
            )
        )
        // Simulate Firestore Long deserialization for integer fields
        val simulatedFirestoreMaps = com.example.data.FirestorePayloadMapper.questionsToMaps(questions).map { map ->
            map + ("index" to 0L)
        }
        val cloudQuiz = com.example.data.CloudQuiz(
            id = "quiz_abc",
            userId = "user_1",
            title = "Chemistry Basics",
            subject = "Chemistry",
            questionType = "MCQ",
            questions = simulatedFirestoreMaps
        )
        val decoded = cloudQuiz.toQuizQuestions()
        assertEquals(1, decoded.size)
        assertEquals("What is H2O?", decoded.first().question)
        assertEquals(0, decoded.first().index)
        assertEquals("Water", decoded.first().correctAnswer)

        val attempt = com.example.data.CloudQuizAttempt(
            id = "attempt_1",
            quizId = "quiz_abc",
            userAnswers = com.example.data.FirestorePayloadMapper.answersToStringMap(mapOf(0 to "Water"))
        )
        assertEquals(mapOf(0 to "Water"), attempt.toAnswersMap())
    }
}
