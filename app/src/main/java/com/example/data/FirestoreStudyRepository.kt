package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreStudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: error("User must be signed in with Google before accessing cloud storage.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun notesCollection(userId: String) = userDoc(userId).collection("notes")
    private fun quizzesCollection(userId: String) = userDoc(userId).collection("quizzes")
    private fun attemptsCollection(userId: String) = userDoc(userId).collection("quizAttempts")

    // --- REALTIME SNAPSHOT FLOWS ---

    fun observeNotes(userId: String): Flow<List<CloudStudyNote>> =
        notesCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudStudyNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeQuizzes(userId: String): Flow<List<CloudQuiz>> =
        quizzesCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudQuiz::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeQuizAttempts(userId: String): Flow<List<CloudQuizAttempt>> =
        attemptsCollection(userId)
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudQuizAttempt::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    // --- USER PROFILE ---

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredLanguage: StudyLanguage
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthorizedUid(userId)
            val safeName = displayName.trim().ifBlank { "Scholar" }.take(100)
            val safeEmail = email?.trim()?.takeIf { it.isNotBlank() }?.take(254)
            val safePhoto = photoUrl?.trim()?.takeIf { it.isNotBlank() }?.take(500)
            val safeLang = FirestorePayloadMapper.sanitizeLanguage(preferredLanguage.name)

            val docRef = userDoc(uid)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    put("preferredLanguage", safeLang)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("preferredLanguage", safeLang)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- CLOUD STUDY NOTES ---

    suspend fun addNote(
        userId: String,
        title: String,
        subject: String,
        category: String,
        style: String,
        sourceName: String,
        content: String
    ): Result<String> {
        val path = "users/$userId/notes"
        return try {
            val uid = requireAuthorizedUid(userId)
            val safeTitle = title.trim().ifBlank { "Untitled Study Note" }.take(200)
            val safeSubject = subject.trim().ifBlank { "General" }.take(80)
            val safeCategory = FirestorePayloadMapper.sanitizeCategory(category)
            val safeStyle = style.trim().ifBlank { "Short Notes" }.take(80)
            val safeSource = sourceName.trim().take(200)
            val safeContent = content.trim().take(50_000)
            require(safeContent.isNotEmpty()) { "Note content cannot be empty." }

            val docRef = notesCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "subject" to safeSubject,
                "category" to safeCategory,
                "style" to safeStyle,
                "sourceName" to safeSource,
                "content" to safeContent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameNote(userId: String, noteId: String, newTitle: String): Result<Unit> {
        val path = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(noteId.isNotBlank()) { "Note ID must not be blank." }
            val safeTitle = newTitle.trim().take(200)
            require(safeTitle.isNotEmpty()) { "Note title must not be blank." }

            notesCollection(uid).document(noteId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val path = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(noteId.isNotBlank()) { "Note ID must not be blank." }
            notesCollection(uid).document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CLOUD QUIZZES ---

    suspend fun saveQuiz(
        userId: String,
        title: String,
        subject: String,
        sourceType: String,
        sourceName: String,
        difficulty: String,
        questionType: String,
        durationMinutes: Int,
        questions: List<QuizQuestion>
    ): Result<String> {
        val path = "users/$userId/quizzes"
        return try {
            val uid = requireAuthorizedUid(userId)
            val questionMaps = FirestorePayloadMapper.questionsToMaps(questions)
            require(questionMaps.isNotEmpty()) { "A quiz must contain at least one question." }

            val docRef = quizzesCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "AI Study Quiz" }.take(200),
                "subject" to subject.trim().ifBlank { "General" }.take(80),
                "sourceType" to FirestorePayloadMapper.sanitizeSourceType(sourceType),
                "sourceName" to sourceName.trim().take(200),
                "difficulty" to FirestorePayloadMapper.sanitizeDifficulty(difficulty),
                "questionType" to FirestorePayloadMapper.sanitizeQuestionType(questionType),
                "durationMinutes" to durationMinutes.coerceIn(1, 180),
                "questionCount" to questionMaps.size.coerceIn(1, 100),
                "questions" to questionMaps,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun getQuizById(userId: String, quizId: String): Result<CloudQuiz?> {
        val path = "users/$userId/quizzes/$quizId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(quizId.isNotBlank()) { "Quiz ID must not be blank." }
            val snap = quizzesCollection(uid).document(quizId).get().await()
            val quiz = if (snap.exists()) {
                snap.toObject(CloudQuiz::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(quiz)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun renameQuiz(userId: String, quizId: String, newTitle: String): Result<Unit> {
        val path = "users/$userId/quizzes/$quizId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(quizId.isNotBlank()) { "Quiz ID must not be blank." }
            val safeTitle = newTitle.trim().take(200)
            require(safeTitle.isNotEmpty()) { "Quiz title must not be blank." }

            quizzesCollection(uid).document(quizId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuiz(userId: String, quizId: String): Result<Unit> {
        val path = "users/$userId/quizzes/$quizId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(quizId.isNotBlank()) { "Quiz ID must not be blank." }
            quizzesCollection(uid).document(quizId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CLOUD QUIZ HISTORY (ATTEMPTS) ---

    suspend fun saveQuizAttempt(
        userId: String,
        quizId: String,
        quizTitle: String,
        subject: String,
        mockTest: Boolean,
        score: Int,
        totalQuestions: Int,
        percentage: Int,
        timeSpentSeconds: Int,
        userAnswers: Map<Int, String>
    ): Result<String> {
        val path = "users/$userId/quizAttempts"
        return try {
            val uid = requireAuthorizedUid(userId)
            val safeQuizId = quizId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "quiz_history" }.take(128)
            val safeTotal = totalQuestions.coerceIn(1, 100)
            val safeScore = score.coerceIn(0, safeTotal)
            val safePct = percentage.coerceIn(0, 100)
            val safeTime = timeSpentSeconds.coerceIn(0, 86_400)

            val docRef = attemptsCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "quizId" to safeQuizId,
                "quizTitle" to quizTitle.trim().ifBlank { "Study Quiz" }.take(200),
                "subject" to subject.trim().ifBlank { "General" }.take(80),
                "mockTest" to mockTest,
                "score" to safeScore,
                "totalQuestions" to safeTotal,
                "percentage" to safePct,
                "timeSpentSeconds" to safeTime,
                "userAnswers" to FirestorePayloadMapper.answersToStringMap(userAnswers),
                "completedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuizAttempt(userId: String, attemptId: String): Result<Unit> {
        val path = "users/$userId/quizAttempts/$attemptId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(attemptId.isNotBlank()) { "Attempt ID must not be blank." }
            attemptsCollection(uid).document(attemptId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
