package com.example.data

import com.example.ai.PreparedMaterial
import kotlinx.coroutines.flow.Flow

class StudyRepository(
    private val dao: StudyDao,
    val preferences: UserPreferencesRepository
) {
    val quizzes: Flow<List<QuizEntity>> = dao.observeQuizzes()
    val attempts: Flow<List<QuizAttemptEntity>> = dao.observeAttempts()
    val notes: Flow<List<StudyNoteEntity>> = dao.observeNotes()
    val flashcardDecks: Flow<List<FlashcardDeckEntity>> = dao.observeFlashcardDecks()
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.observeChatMessages()
    val plannerTasks: Flow<List<PlannerTaskEntity>> = dao.observePlannerTasks()
    val examCountdowns: Flow<List<ExamCountdownEntity>> = dao.observeExamCountdowns()
    val uploadedDocs: Flow<List<UploadedDocEntity>> = dao.observeUploadedDocs()
    val userPreferences: Flow<UserPreferences> = preferences.preferencesFlow

    suspend fun saveQuiz(quiz: QuizEntity): Long {
        preferences.recordStudyActivity(5)
        return dao.insertQuiz(quiz)
    }

    suspend fun getQuizById(id: Long): QuizEntity? = dao.getQuizById(id)

    suspend fun renameQuiz(id: Long, newTitle: String) = dao.renameQuiz(id, newTitle)

    suspend fun deleteQuiz(id: Long) = dao.deleteQuiz(id)

    suspend fun saveAttempt(attempt: QuizAttemptEntity): Long {
        val minutes = (attempt.timeSpentSeconds / 60).coerceAtLeast(3)
        preferences.recordStudyActivity(minutes)
        return dao.insertAttempt(attempt)
    }

    suspend fun deleteAttempt(id: Long) = dao.deleteAttempt(id)

    suspend fun saveNote(note: StudyNoteEntity): Long {
        preferences.recordStudyActivity(5)
        return dao.insertNote(note)
    }

    suspend fun renameNote(id: Long, newTitle: String) = dao.renameNote(id, newTitle)

    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    suspend fun saveFlashcardDeck(deck: FlashcardDeckEntity): Long {
        preferences.recordStudyActivity(5)
        return dao.insertFlashcardDeck(deck)
    }

    suspend fun getDeckById(id: Long): FlashcardDeckEntity? = dao.getDeckById(id)

    suspend fun updateFlashcardDeck(deck: FlashcardDeckEntity) {
        dao.updateFlashcardDeck(deck)
        preferences.recordStudyActivity(2)
    }

    suspend fun renameFlashcardDeck(id: Long, newTitle: String) = dao.renameFlashcardDeck(id, newTitle)

    suspend fun deleteFlashcardDeck(id: Long) = dao.deleteFlashcardDeck(id)

    suspend fun insertChatMessage(message: ChatMessageEntity): Long {
        if (message.role == "assistant") {
            preferences.recordStudyActivity(2)
        }
        return dao.insertChatMessage(message)
    }

    suspend fun markChatMessageSaved(id: Long) = dao.markChatMessageSaved(id)

    suspend fun deleteChatMessage(id: Long) = dao.deleteChatMessage(id)

    suspend fun clearChatHistory() = dao.clearChatHistory()

    suspend fun addPlannerTask(task: PlannerTaskEntity): Long = dao.insertPlannerTask(task)

    suspend fun setTaskCompleted(id: Long, completed: Boolean, durationMinutes: Int) {
        dao.setPlannerTaskCompleted(id, completed)
        if (completed) {
            preferences.recordStudyActivity(durationMinutes.coerceAtLeast(5))
        }
    }

    suspend fun deletePlannerTask(id: Long) = dao.deletePlannerTask(id)

    suspend fun addExamCountdown(countdown: ExamCountdownEntity): Long =
        dao.insertExamCountdown(countdown)

    suspend fun deleteExamCountdown(id: Long) = dao.deleteExamCountdown(id)

    suspend fun recordUploadedDocument(material: PreparedMaterial): Long {
        return dao.insertUploadedDoc(
            UploadedDocEntity(
                fileName = material.displayName,
                mimeType = material.mimeType,
                sizeBytes = material.sizeBytes,
                preview = material.previewSummary
            )
        )
    }

    suspend fun renameUploadedDoc(id: Long, newName: String) = dao.renameUploadedDoc(id, newName)

    suspend fun deleteUploadedDoc(id: Long) = dao.deleteUploadedDoc(id)
}
