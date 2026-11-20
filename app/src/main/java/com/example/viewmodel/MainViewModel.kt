package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudQuizAttempt
import com.example.data.CloudStudyNote
import com.example.data.CloudUiState
import com.example.data.OperationType
import com.example.data.StudyLanguage
import com.example.data.StudyRepository
import com.example.data.UserPreferences
import com.example.data.friendlyFirestoreMessage
import com.example.data.handleFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val timestamp: Long,
    val quizId: String? = null
)

data class SubjectPerformance(
    val subject: String,
    val averagePercentage: Int,
    val attemptCount: Int
)

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

data class DashboardUiState(
    val preferences: UserPreferences = UserPreferences(),
    val totalQuizzesCompleted: Int = 0,
    val averageScore: Int = 0,
    val totalNotesSaved: Int = 0,
    val totalFlashcardDecks: Int = 0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val weeklyGoalProgress: Float = 0f,
    val recentActivities: List<RecentActivityItem> = emptyList(),
    val recentAttempts: List<CloudQuizAttempt> = emptyList(),
    val subjectPerformance: List<SubjectPerformance> = emptyList(),
    val badges: List<AchievementBadge> = emptyList(),
    val isCloudLoading: Boolean = true,
    val cloudSyncError: String? = null
)

class MainViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _migrationBanner = MutableStateFlow<String?>(null)
    val migrationBanner: StateFlow<String?> = _migrationBanner.asStateFlow()

    private val cloudAttemptsState: StateFlow<CloudUiState<List<CloudQuizAttempt>>> =
        repository.observeCloudAttempts(currentUserId)
            .map<List<CloudQuizAttempt>, CloudUiState<List<CloudQuizAttempt>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/quizAttempts")
                Log.w(TAG, "Error observing quiz history", rootError)
                emit(CloudUiState.Error(friendlyFirestoreMessage(rootError, "Failed to sync quiz history.")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    private val cloudNotesState: StateFlow<CloudUiState<List<CloudStudyNote>>> =
        repository.observeCloudNotes(currentUserId)
            .map<List<CloudStudyNote>, CloudUiState<List<CloudStudyNote>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/notes")
                Log.w(TAG, "Error observing cloud notes", rootError)
                emit(CloudUiState.Error(friendlyFirestoreMessage(rootError, "Failed to sync study notes.")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CloudUiState.Loading
            )

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.userPreferences,
        cloudAttemptsState,
        cloudNotesState,
        repository.flashcardDecks,
        repository.plannerTasks
    ) { prefs, attemptsState, notesState, decks, tasks ->
        val attempts = (attemptsState as? CloudUiState.Success)?.data.orEmpty()
        val notes = (notesState as? CloudUiState.Success)?.data.orEmpty()
        val isCloudLoading = attemptsState is CloudUiState.Loading || notesState is CloudUiState.Loading
        val cloudError = (attemptsState as? CloudUiState.Error)?.message
            ?: (notesState as? CloudUiState.Error)?.message

        val totalQuizzes = attempts.size
        val avgScore = if (attempts.isNotEmpty()) {
            attempts.map { it.percentage }.average().toInt()
        } else {
            0
        }
        val goalProgress = if (prefs.weeklyGoalMinutes > 0) {
            (prefs.studiedMinutesThisWeek.toFloat() / prefs.weeklyGoalMinutes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val activities = buildList {
            attempts.take(5).forEach { a ->
                add(
                    RecentActivityItem(
                        id = "attempt_${a.id}",
                        title = a.quizTitle,
                        subtitle = "${a.subject} • ${a.score}/${a.totalQuestions} correct",
                        badge = "${a.percentage}%",
                        timestamp = a.completedAtMillis(),
                        quizId = a.quizId.takeIf { it.isNotBlank() }
                    )
                )
            }
            notes.take(4).forEach { n ->
                val label = when (n.category) {
                    "SUMMARY" -> "Summary"
                    "AI_ANSWER" -> "AI Answer"
                    else -> "Notes"
                }
                add(
                    RecentActivityItem(
                        id = "note_${n.id}",
                        title = n.title,
                        subtitle = "${n.subject} • ${n.style}",
                        badge = label,
                        timestamp = n.createdAtMillis()
                    )
                )
            }
            decks.take(3).forEach { d ->
                add(
                    RecentActivityItem(
                        id = "deck_${d.id}",
                        title = d.title,
                        subtitle = "${d.subject} • ${d.masteredCount}/${d.totalCount} mastered",
                        badge = "Cards",
                        timestamp = d.createdAt
                    )
                )
            }
        }.sortedByDescending { it.timestamp }.take(6)

        val subjectStats = attempts
            .groupBy { it.subject.ifBlank { "General" } }
            .map { (subj, list) ->
                SubjectPerformance(
                    subject = subj,
                    averagePercentage = list.map { it.percentage }.average().toInt(),
                    attemptCount = list.size
                )
            }
            .sortedByDescending { it.averagePercentage }

        val masteredCardsTotal = decks.sumOf { it.masteredCount }
        val highScoreExists = attempts.any { it.percentage >= 85 }
        val badges = listOf(
            AchievementBadge(
                id = "first_quiz",
                title = "Quiz Explorer",
                description = "Complete your first AI quiz",
                unlocked = totalQuizzes >= 1
            ),
            AchievementBadge(
                id = "score_ace",
                title = "Precision Ace",
                description = "Score 85% or higher on a quiz",
                unlocked = highScoreExists
            ),
            AchievementBadge(
                id = "note_scholar",
                title = "Note Architect",
                description = "Save 3 or more AI notes or summaries",
                unlocked = notes.size >= 3
            ),
            AchievementBadge(
                id = "card_master",
                title = "Memory Master",
                description = "Master 10 flashcards",
                unlocked = masteredCardsTotal >= 10
            ),
            AchievementBadge(
                id = "streak_builder",
                title = "Streak Igniter",
                description = "Maintain a 3-day study streak",
                unlocked = prefs.streakDays >= 3
            ),
            AchievementBadge(
                id = "mock_pro",
                title = "Exam Ready",
                description = "Complete 5 quizzes or mock tests",
                unlocked = totalQuizzes >= 5
            )
        )

        DashboardUiState(
            preferences = prefs,
            totalQuizzesCompleted = totalQuizzes,
            averageScore = avgScore,
            totalNotesSaved = notes.size,
            totalFlashcardDecks = decks.size,
            completedTasksCount = tasks.count { it.isCompleted },
            totalTasksCount = tasks.size,
            weeklyGoalProgress = goalProgress,
            recentActivities = activities,
            recentAttempts = attempts,
            subjectPerformance = subjectStats,
            badges = badges,
            isCloudLoading = isCloudLoading,
            cloudSyncError = cloudError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardUiState()
    )

    fun syncSignedInProfileAndLegacyData(
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        viewModelScope.launch {
            val prefs = repository.userPreferences.first()
            val resolvedName = displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: prefs.studentName.ifBlank { "Scholar" }

            if (prefs.studentName == "Scholar" && !displayName.isNullOrBlank()) {
                repository.preferences.setStudentName(displayName.trim())
            }

            repository.ensureUserProfile(
                userId = currentUserId,
                displayName = resolvedName,
                email = email,
                photoUrl = photoUrl,
                preferredLanguage = prefs.language
            )

            val migrated = repository.migrateLegacyLocalDataToCloud(currentUserId)
            if (migrated > 0) {
                _migrationBanner.value = "Synced $migrated local study items to Cloud Firestore."
            }
        }
    }

    fun clearMigrationBanner() {
        _migrationBanner.value = null
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setDarkTheme(enabled) }
    }

    fun selectLanguage(language: StudyLanguage) {
        viewModelScope.launch {
            repository.preferences.setLanguage(language)
            val prefs = repository.userPreferences.first()
            repository.ensureUserProfile(
                userId = currentUserId,
                displayName = prefs.studentName,
                email = null,
                photoUrl = null,
                preferredLanguage = language
            )
        }
    }

    fun updateStudentName(name: String) {
        viewModelScope.launch {
            repository.preferences.setStudentName(name)
            val prefs = repository.userPreferences.first()
            repository.ensureUserProfile(
                userId = currentUserId,
                displayName = name,
                email = null,
                photoUrl = null,
                preferredLanguage = prefs.language
            )
        }
    }

    fun updateWeeklyGoalMinutes(minutes: Int) {
        viewModelScope.launch { repository.preferences.setWeeklyGoalMinutes(minutes) }
    }

    fun dismissWelcome() {
        viewModelScope.launch { repository.preferences.markWelcomeSeen() }
    }

    fun exportBackupToUri(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val json = repository.exportBackupJson(currentUserId)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open backup destination.")
                }
                "Study data backup exported successfully."
            }.getOrElse { err ->
                "Backup failed: ${err.localizedMessage ?: "Unknown error"}"
            }
            onResult(message)
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("Could not read backup file.")
                }
                val count = repository.importBackupJson(currentUserId, raw)
                "Synced $count study items to Cloud Firestore & local library."
            }.getOrElse { err ->
                "Restore failed: ${err.localizedMessage ?: "Invalid backup file."}"
            }
            onResult(message)
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
