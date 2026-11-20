package com.example.data

import com.example.ai.PreparedMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class StudyRepository(
    private val dao: StudyDao,
    val preferences: UserPreferencesRepository,
    val cloudRepository: FirestoreStudyRepository
) {
    // Device-local Room streams
    val flashcardDecks: Flow<List<FlashcardDeckEntity>> = dao.observeFlashcardDecks()
    val chatMessages: Flow<List<ChatMessageEntity>> = dao.observeChatMessages()
    val plannerTasks: Flow<List<PlannerTaskEntity>> = dao.observePlannerTasks()
    val examCountdowns: Flow<List<ExamCountdownEntity>> = dao.observeExamCountdowns()
    val uploadedDocs: Flow<List<UploadedDocEntity>> = dao.observeUploadedDocs()
    val userPreferences: Flow<UserPreferences> = preferences.preferencesFlow

    // Cloud Firestore real-time streams (strictly scoped to authenticated userId)
    fun observeCloudNotes(userId: String): Flow<List<CloudStudyNote>> =
        cloudRepository.observeNotes(userId)

    fun observeCloudQuizzes(userId: String): Flow<List<CloudQuiz>> =
        cloudRepository.observeQuizzes(userId)

    fun observeCloudAttempts(userId: String): Flow<List<CloudQuizAttempt>> =
        cloudRepository.observeQuizAttempts(userId)

    // User profile sync
    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredLanguage: StudyLanguage
    ): Result<Unit> = cloudRepository.ensureUserProfile(
        userId = userId,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl,
        preferredLanguage = preferredLanguage
    )

    // Cloud Quizzes
    suspend fun saveCloudQuiz(
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
        val result = cloudRepository.saveQuiz(
            userId = userId,
            title = title,
            subject = subject,
            sourceType = sourceType,
            sourceName = sourceName,
            difficulty = difficulty,
            questionType = questionType,
            durationMinutes = durationMinutes,
            questions = questions
        )
        if (result.isSuccess) {
            preferences.recordStudyActivity(5)
        }
        return result
    }

    suspend fun getCloudQuizById(userId: String, quizId: String): Result<CloudQuiz?> =
        cloudRepository.getQuizById(userId, quizId)

    suspend fun renameCloudQuiz(userId: String, quizId: String, newTitle: String): Result<Unit> =
        cloudRepository.renameQuiz(userId, quizId, newTitle)

    suspend fun deleteCloudQuiz(userId: String, quizId: String): Result<Unit> =
        cloudRepository.deleteQuiz(userId, quizId)

    // Cloud Quiz History (Attempts)
    suspend fun saveCloudAttempt(
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
        val result = cloudRepository.saveQuizAttempt(
            userId = userId,
            quizId = quizId,
            quizTitle = quizTitle,
            subject = subject,
            mockTest = mockTest,
            score = score,
            totalQuestions = totalQuestions,
            percentage = percentage,
            timeSpentSeconds = timeSpentSeconds,
            userAnswers = userAnswers
        )
        if (result.isSuccess) {
            val minutes = (timeSpentSeconds / 60).coerceAtLeast(3)
            preferences.recordStudyActivity(minutes)
        }
        return result
    }

    suspend fun deleteCloudAttempt(userId: String, attemptId: String): Result<Unit> =
        cloudRepository.deleteQuizAttempt(userId, attemptId)

    // Cloud Notes
    suspend fun saveCloudNote(
        userId: String,
        title: String,
        subject: String,
        category: String,
        style: String,
        sourceName: String = "",
        content: String
    ): Result<String> {
        val result = cloudRepository.addNote(
            userId = userId,
            title = title,
            subject = subject,
            category = category,
            style = style,
            sourceName = sourceName,
            content = content
        )
        if (result.isSuccess) {
            preferences.recordStudyActivity(5)
        }
        return result
    }

    suspend fun renameCloudNote(userId: String, noteId: String, newTitle: String): Result<Unit> =
        cloudRepository.renameNote(userId, noteId, newTitle)

    suspend fun deleteCloudNote(userId: String, noteId: String): Result<Unit> =
        cloudRepository.deleteNote(userId, noteId)

    /**
     * Migrates any legacy v1.1 local Room notes, quizzes, and quiz attempts into Cloud Firestore
     * once the user signs in, then clears the migrated legacy Room rows so they do not duplicate.
     */
    suspend fun migrateLegacyLocalDataToCloud(userId: String): Int {
        var migrated = 0
        try {
            val localNotes = dao.observeNotes().first()
            localNotes.forEach { note ->
                val res = cloudRepository.addNote(
                    userId = userId,
                    title = note.title,
                    subject = note.subject,
                    category = note.category,
                    style = note.style,
                    sourceName = note.sourceName,
                    content = note.content
                )
                if (res.isSuccess) {
                    dao.deleteNote(note.id)
                    migrated++
                }
            }

            val quizIdMap = mutableMapOf<Long, String>()
            val localQuizzes = dao.observeQuizzes().first()
            localQuizzes.forEach { quiz ->
                val questions = StudyJsonCodec.decodeQuestions(quiz.questionsJson)
                if (questions.isNotEmpty()) {
                    val res = cloudRepository.saveQuiz(
                        userId = userId,
                        title = quiz.title,
                        subject = quiz.subject,
                        sourceType = quiz.sourceType,
                        sourceName = quiz.sourceName,
                        difficulty = quiz.difficulty,
                        questionType = quiz.questionType,
                        durationMinutes = quiz.durationMinutes,
                        questions = questions
                    )
                    res.getOrNull()?.let { cloudId ->
                        quizIdMap[quiz.id] = cloudId
                        dao.deleteQuiz(quiz.id)
                        migrated++
                    }
                }
            }

            val localAttempts = dao.observeAttempts().first()
            localAttempts.forEach { attempt ->
                val mappedQuizId = quizIdMap[attempt.quizId] ?: "legacy_quiz_${attempt.quizId}"
                val res = cloudRepository.saveQuizAttempt(
                    userId = userId,
                    quizId = mappedQuizId,
                    quizTitle = attempt.quizTitle,
                    subject = attempt.subject,
                    mockTest = attempt.isMockTest,
                    score = attempt.score,
                    totalQuestions = attempt.totalQuestions,
                    percentage = attempt.percentage,
                    timeSpentSeconds = attempt.timeSpentSeconds,
                    userAnswers = StudyJsonCodec.decodeAnswersMap(attempt.userAnswersJson)
                )
                if (res.isSuccess) {
                    dao.deleteAttempt(attempt.id)
                    migrated++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal: remaining legacy rows can retry on next sign-in
        }
        return migrated
    }

    // Device-local Room operations (Flashcards, Chat, Planner, Countdowns, Docs)
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

    // Backup & Restore (combines Cloud Firestore notes/quizzes/history + local Room study tools)
    suspend fun exportBackupJson(userId: String): String {
        val prefs = userPreferences.first()
        val quizList = observeCloudQuizzes(userId).first()
        val attemptList = observeCloudAttempts(userId).first()
        val noteList = observeCloudNotes(userId).first()
        val deckList = flashcardDecks.first()
        val chatList = chatMessages.first()
        val taskList = plannerTasks.first()
        val examList = examCountdowns.first()
        val docList = uploadedDocs.first()

        val root = JSONObject().apply {
            put("app", "Study Genius AI")
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put(
                "preferences",
                JSONObject().apply {
                    put("darkTheme", prefs.darkTheme)
                    put("language", prefs.language.name)
                    put("studentName", prefs.studentName)
                    put("weeklyGoalMinutes", prefs.weeklyGoalMinutes)
                    put("studiedMinutesThisWeek", prefs.studiedMinutesThisWeek)
                    put("streakDays", prefs.streakDays)
                    put("lastStudyEpochDay", prefs.lastStudyEpochDay)
                }
            )
            put(
                "quizzes",
                JSONArray().apply {
                    quizList.forEach { q ->
                        put(
                            JSONObject().apply {
                                put("id", q.id)
                                put("title", q.title)
                                put("subject", q.subject)
                                put("sourceType", q.sourceType)
                                put("sourceName", q.sourceName)
                                put("difficulty", q.difficulty)
                                put("questionType", q.questionType)
                                put("durationMinutes", q.durationMinutes)
                                put("questionCount", q.questionCount)
                                put("questionsJson", StudyJsonCodec.encodeQuestions(q.toQuizQuestions()))
                                put("createdAt", q.createdAtMillis())
                            }
                        )
                    }
                }
            )
            put(
                "attempts",
                JSONArray().apply {
                    attemptList.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("quizId", a.quizId)
                                put("quizTitle", a.quizTitle)
                                put("subject", a.subject)
                                put("isMockTest", a.mockTest)
                                put("score", a.score)
                                put("totalQuestions", a.totalQuestions)
                                put("percentage", a.percentage)
                                put("timeSpentSeconds", a.timeSpentSeconds)
                                put("userAnswersJson", StudyJsonCodec.encodeAnswersMap(a.toAnswersMap()))
                                put("completedAt", a.completedAtMillis())
                            }
                        )
                    }
                }
            )
            put(
                "notes",
                JSONArray().apply {
                    noteList.forEach { n ->
                        put(
                            JSONObject().apply {
                                put("title", n.title)
                                put("subject", n.subject)
                                put("category", n.category)
                                put("style", n.style)
                                put("sourceName", n.sourceName)
                                put("content", n.content)
                                put("createdAt", n.createdAtMillis())
                            }
                        )
                    }
                }
            )
            put(
                "flashcardDecks",
                JSONArray().apply {
                    deckList.forEach { d ->
                        put(
                            JSONObject().apply {
                                put("title", d.title)
                                put("subject", d.subject)
                                put("cardsJson", d.cardsJson)
                                put("masteredCount", d.masteredCount)
                                put("totalCount", d.totalCount)
                                put("createdAt", d.createdAt)
                            }
                        )
                    }
                }
            )
            put(
                "chatMessages",
                JSONArray().apply {
                    chatList.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("role", c.role)
                                put("text", c.text)
                                put("subject", c.subject)
                                put("mode", c.mode)
                                put("hasImage", c.hasImage)
                                put("savedToLibrary", c.savedToLibrary)
                                put("timestamp", c.timestamp)
                            }
                        )
                    }
                }
            )
            put(
                "plannerTasks",
                JSONArray().apply {
                    taskList.forEach { t ->
                        put(
                            JSONObject().apply {
                                put("title", t.title)
                                put("subject", t.subject)
                                put("dayLabel", t.dayLabel)
                                put("timeSlot", t.timeSlot)
                                put("reminderLabel", t.reminderLabel)
                                put("durationMinutes", t.durationMinutes)
                                put("isCompleted", t.isCompleted)
                                put("createdAt", t.createdAt)
                            }
                        )
                    }
                }
            )
            put(
                "examCountdowns",
                JSONArray().apply {
                    examList.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("examName", e.examName)
                                put("subject", e.subject)
                                put("targetEpochDay", e.targetEpochDay)
                                put("createdAt", e.createdAt)
                            }
                        )
                    }
                }
            )
            put(
                "uploadedDocs",
                JSONArray().apply {
                    docList.forEach { u ->
                        put(
                            JSONObject().apply {
                                put("fileName", u.fileName)
                                put("mimeType", u.mimeType)
                                put("sizeBytes", u.sizeBytes)
                                put("preview", u.preview)
                                put("uploadedAt", u.uploadedAt)
                            }
                        )
                    }
                }
            )
        }
        return root.toString(2)
    }

    suspend fun importBackupJson(userId: String, rawJson: String): Int {
        val root = JSONObject(rawJson)
        var restoredCount = 0

        root.optJSONObject("preferences")?.let { p ->
            preferences.restorePreferences(
                UserPreferences(
                    darkTheme = p.optBoolean("darkTheme", true),
                    language = StudyLanguage.fromName(p.optString("language", "ENGLISH")),
                    studentName = p.optString("studentName", "Scholar"),
                    weeklyGoalMinutes = p.optInt("weeklyGoalMinutes", 180),
                    studiedMinutesThisWeek = p.optInt("studiedMinutesThisWeek", 0),
                    streakDays = p.optInt("streakDays", 1),
                    lastStudyEpochDay = p.optLong("lastStudyEpochDay", 0L)
                )
            )
        }

        val quizIdMap = mutableMapOf<String, String>()
        root.optJSONArray("quizzes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val oldId = obj.optString("id", "").ifBlank { obj.optLong("id", -1L).takeIf { it > 0 }?.toString().orEmpty() }
                val questions = StudyJsonCodec.decodeQuestions(obj.optString("questionsJson", "[]"))
                if (questions.isNotEmpty()) {
                    val saved = cloudRepository.saveQuiz(
                        userId = userId,
                        title = obj.optString("title", "Restored Quiz"),
                        subject = obj.optString("subject", "General"),
                        sourceType = obj.optString("sourceType", "NOTES"),
                        sourceName = obj.optString("sourceName", ""),
                        difficulty = obj.optString("difficulty", "Medium"),
                        questionType = obj.optString("questionType", QuestionType.MCQ.name),
                        durationMinutes = obj.optInt("durationMinutes", 10),
                        questions = questions
                    )
                    saved.getOrNull()?.let { newId ->
                        if (oldId.isNotBlank()) quizIdMap[oldId] = newId
                        restoredCount++
                    }
                }
            }
        }

        root.optJSONArray("attempts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val oldQuizId = obj.optString("quizId", "").ifBlank { obj.optLong("quizId", 0L).toString() }
                val mappedQuizId = quizIdMap[oldQuizId] ?: oldQuizId.ifBlank { "restored_quiz" }
                val saved = cloudRepository.saveQuizAttempt(
                    userId = userId,
                    quizId = mappedQuizId,
                    quizTitle = obj.optString("quizTitle", "Quiz"),
                    subject = obj.optString("subject", "General"),
                    mockTest = obj.optBoolean("isMockTest", obj.optBoolean("mockTest", false)),
                    score = obj.optInt("score", 0),
                    totalQuestions = obj.optInt("totalQuestions", 1).coerceAtLeast(1),
                    percentage = obj.optInt("percentage", 0),
                    timeSpentSeconds = obj.optInt("timeSpentSeconds", 60),
                    userAnswers = StudyJsonCodec.decodeAnswersMap(obj.optString("userAnswersJson", "{}"))
                )
                if (saved.isSuccess) restoredCount++
            }
        }

        root.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val content = obj.optString("content", "")
                if (content.isNotBlank()) {
                    val saved = cloudRepository.addNote(
                        userId = userId,
                        title = obj.optString("title", "Restored Note"),
                        subject = obj.optString("subject", "General"),
                        category = obj.optString("category", "NOTE"),
                        style = obj.optString("style", "Short Notes"),
                        sourceName = obj.optString("sourceName", ""),
                        content = content
                    )
                    if (saved.isSuccess) restoredCount++
                }
            }
        }

        root.optJSONArray("flashcardDecks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insertFlashcardDeck(
                    FlashcardDeckEntity(
                        title = obj.optString("title", "Restored Deck"),
                        subject = obj.optString("subject", "General"),
                        cardsJson = obj.optString("cardsJson", "[]"),
                        masteredCount = obj.optInt("masteredCount", 0),
                        totalCount = obj.optInt("totalCount", 0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                restoredCount++
            }
        }

        root.optJSONArray("chatMessages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insertChatMessage(
                    ChatMessageEntity(
                        role = obj.optString("role", "user"),
                        text = obj.optString("text", ""),
                        subject = obj.optString("subject", "General"),
                        mode = obj.optString("mode", "Step-by-Step"),
                        hasImage = obj.optBoolean("hasImage", false),
                        savedToLibrary = obj.optBoolean("savedToLibrary", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
                restoredCount++
            }
        }

        root.optJSONArray("plannerTasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insertPlannerTask(
                    PlannerTaskEntity(
                        title = obj.optString("title", "Study Block"),
                        subject = obj.optString("subject", "General"),
                        dayLabel = obj.optString("dayLabel", "Today"),
                        timeSlot = obj.optString("timeSlot", "06:00 PM - 07:00 PM"),
                        reminderLabel = obj.optString("reminderLabel", ""),
                        durationMinutes = obj.optInt("durationMinutes", 45),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                restoredCount++
            }
        }

        root.optJSONArray("examCountdowns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insertExamCountdown(
                    ExamCountdownEntity(
                        examName = obj.optString("examName", "Exam"),
                        subject = obj.optString("subject", "All Subjects"),
                        targetEpochDay = obj.optLong("targetEpochDay", System.currentTimeMillis() / 86_400_000L + 7),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                restoredCount++
            }
        }

        root.optJSONArray("uploadedDocs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insertUploadedDoc(
                    UploadedDocEntity(
                        fileName = obj.optString("fileName", "Document"),
                        mimeType = obj.optString("mimeType", "application/pdf"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        preview = obj.optString("preview", ""),
                        uploadedAt = obj.optLong("uploadedAt", System.currentTimeMillis())
                    )
                )
                restoredCount++
            }
        }

        return restoredCount
    }
}
