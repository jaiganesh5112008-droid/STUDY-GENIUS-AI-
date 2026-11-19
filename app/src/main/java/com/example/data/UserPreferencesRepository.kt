package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "study_genius_prefs"
)

data class UserPreferences(
    val darkTheme: Boolean = true,
    val language: StudyLanguage = StudyLanguage.ENGLISH,
    val studentName: String = "Scholar",
    val weeklyGoalMinutes: Int = 180,
    val studiedMinutesThisWeek: Int = 0,
    val streakDays: Int = 1,
    val lastStudyEpochDay: Long = 0L,
    val hasSeenWelcome: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LANGUAGE = stringPreferencesKey("language")
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val WEEKLY_GOAL_MIN = intPreferencesKey("weekly_goal_minutes")
        val STUDIED_MIN = intPreferencesKey("studied_minutes_week")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val LAST_STUDY_DAY = longPreferencesKey("last_study_epoch_day")
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
    }

    val preferencesFlow: Flow<UserPreferences> = context.studyPrefsDataStore.data.map { prefs ->
        UserPreferences(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            language = StudyLanguage.fromName(prefs[Keys.LANGUAGE]),
            studentName = prefs[Keys.STUDENT_NAME]?.takeIf { it.isNotBlank() } ?: "Scholar",
            weeklyGoalMinutes = prefs[Keys.WEEKLY_GOAL_MIN] ?: 180,
            studiedMinutesThisWeek = prefs[Keys.STUDIED_MIN] ?: 0,
            streakDays = prefs[Keys.STREAK_DAYS] ?: 1,
            lastStudyEpochDay = prefs[Keys.LAST_STUDY_DAY] ?: 0L,
            hasSeenWelcome = prefs[Keys.HAS_SEEN_WELCOME] ?: false
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.studyPrefsDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setLanguage(language: StudyLanguage) {
        context.studyPrefsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setStudentName(name: String) {
        context.studyPrefsDataStore.edit {
            it[Keys.STUDENT_NAME] = name.trim().ifBlank { "Scholar" }
        }
    }

    suspend fun setWeeklyGoalMinutes(minutes: Int) {
        context.studyPrefsDataStore.edit {
            it[Keys.WEEKLY_GOAL_MIN] = minutes.coerceIn(30, 1200)
        }
    }

    suspend fun markWelcomeSeen() {
        context.studyPrefsDataStore.edit { it[Keys.HAS_SEEN_WELCOME] = true }
    }

    suspend fun recordStudyActivity(addedMinutes: Int) {
        val today = System.currentTimeMillis() / 86_400_000L
        context.studyPrefsDataStore.edit { prefs ->
            val currentMin = prefs[Keys.STUDIED_MIN] ?: 0
            prefs[Keys.STUDIED_MIN] = currentMin + addedMinutes.coerceAtLeast(1)

            val lastDay = prefs[Keys.LAST_STUDY_DAY] ?: 0L
            val currentStreak = prefs[Keys.STREAK_DAYS] ?: 0
            when {
                lastDay == 0L -> prefs[Keys.STREAK_DAYS] = 1
                lastDay == today -> if (currentStreak == 0) prefs[Keys.STREAK_DAYS] = 1
                lastDay == today - 1L -> prefs[Keys.STREAK_DAYS] = currentStreak + 1
                else -> prefs[Keys.STREAK_DAYS] = 1
            }
            prefs[Keys.LAST_STUDY_DAY] = today
        }
    }
}
