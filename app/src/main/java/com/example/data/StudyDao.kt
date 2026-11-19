package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // Quizzes
    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun observeQuizzes(): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :quizId LIMIT 1")
    suspend fun getQuizById(quizId: Long): QuizEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Query("UPDATE quizzes SET title = :newTitle WHERE id = :id")
    suspend fun renameQuiz(id: Long, newTitle: String)

    @Query("DELETE FROM quizzes WHERE id = :id")
    suspend fun deleteQuiz(id: Long)

    // Quiz Attempts
    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    fun observeAttempts(): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Query("DELETE FROM quiz_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    // Study Notes, Summaries & Saved AI Answers
    @Query("SELECT * FROM study_notes ORDER BY createdAt DESC")
    fun observeNotes(): Flow<List<StudyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: StudyNoteEntity): Long

    @Query("UPDATE study_notes SET title = :newTitle WHERE id = :id")
    suspend fun renameNote(id: Long, newTitle: String)

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // Flashcard Decks
    @Query("SELECT * FROM flashcard_decks ORDER BY createdAt DESC")
    fun observeFlashcardDecks(): Flow<List<FlashcardDeckEntity>>

    @Query("SELECT * FROM flashcard_decks WHERE id = :deckId LIMIT 1")
    suspend fun getDeckById(deckId: Long): FlashcardDeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcardDeck(deck: FlashcardDeckEntity): Long

    @Update
    suspend fun updateFlashcardDeck(deck: FlashcardDeckEntity)

    @Query("UPDATE flashcard_decks SET title = :newTitle WHERE id = :id")
    suspend fun renameFlashcardDeck(id: Long, newTitle: String)

    @Query("DELETE FROM flashcard_decks WHERE id = :id")
    suspend fun deleteFlashcardDeck(id: Long)

    // Chat Messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET savedToLibrary = 1 WHERE id = :id")
    suspend fun markChatMessageSaved(id: Long)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteChatMessage(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // Planner Tasks
    @Query("SELECT * FROM planner_tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun observePlannerTasks(): Flow<List<PlannerTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannerTask(task: PlannerTaskEntity): Long

    @Query("UPDATE planner_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setPlannerTaskCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM planner_tasks WHERE id = :id")
    suspend fun deletePlannerTask(id: Long)

    // Exam Countdowns
    @Query("SELECT * FROM exam_countdowns ORDER BY targetEpochDay ASC")
    fun observeExamCountdowns(): Flow<List<ExamCountdownEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamCountdown(countdown: ExamCountdownEntity): Long

    @Query("DELETE FROM exam_countdowns WHERE id = :id")
    suspend fun deleteExamCountdown(id: Long)

    // Uploaded Docs Metadata
    @Query("SELECT * FROM uploaded_docs ORDER BY uploadedAt DESC")
    fun observeUploadedDocs(): Flow<List<UploadedDocEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedDoc(doc: UploadedDocEntity): Long

    @Query("UPDATE uploaded_docs SET fileName = :newName WHERE id = :id")
    suspend fun renameUploadedDoc(id: Long, newName: String)

    @Query("DELETE FROM uploaded_docs WHERE id = :id")
    suspend fun deleteUploadedDoc(id: Long)
}
