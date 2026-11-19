package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QuizAttemptEntity
import com.example.data.StudyLanguage
import com.example.data.StudyRepository
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val timestamp: Long,
    val quizId: Long? = null
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
    val recentAttempts: List<QuizAttemptEntity> = emptyList(),
    val subjectPerformance: List<SubjectPerformance> = emptyList(),
    val badges: List<AchievementBadge> = emptyList()
)

class MainViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.userPreferences,
        repository.attempts,
        repository.notes,
        repository.flashcardDecks,
        repository.plannerTasks
    ) { prefs, attempts, notes, decks, tasks ->
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
                        timestamp = a.completedAt,
                        quizId = a.quizId
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
                        timestamp = n.createdAt
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
            badges = badges
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setDarkTheme(enabled) }
    }

    fun selectLanguage(language: StudyLanguage) {
        viewModelScope.launch { repository.preferences.setLanguage(language) }
    }

    fun updateStudentName(name: String) {
        viewModelScope.launch { repository.preferences.setStudentName(name) }
    }

    fun updateWeeklyGoalMinutes(minutes: Int) {
        viewModelScope.launch { repository.preferences.setWeeklyGoalMinutes(minutes) }
    }

    fun dismissWelcome() {
        viewModelScope.launch { repository.preferences.markWelcomeSeen() }
    }
}
