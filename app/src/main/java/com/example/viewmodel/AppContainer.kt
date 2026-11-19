package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.StudyAiService
import com.example.data.StudyDatabase
import com.example.data.StudyRepository
import com.example.data.UserPreferencesRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = StudyDatabase.getInstance(appContext)
    private val prefs = UserPreferencesRepository(appContext)

    val repository = StudyRepository(database.studyDao(), prefs)
    val aiService = StudyAiService()

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(MainViewModel::class.java) ->
                    MainViewModel(repository) as T
                modelClass.isAssignableFrom(ChatViewModel::class.java) ->
                    ChatViewModel(repository, aiService) as T
                modelClass.isAssignableFrom(QuizHubViewModel::class.java) ->
                    QuizHubViewModel(repository, aiService) as T
                modelClass.isAssignableFrom(QuizEngineViewModel::class.java) ->
                    QuizEngineViewModel(repository) as T
                modelClass.isAssignableFrom(NotesSummarizerViewModel::class.java) ->
                    NotesSummarizerViewModel(repository, aiService) as T
                modelClass.isAssignableFrom(FlashcardsViewModel::class.java) ->
                    FlashcardsViewModel(repository, aiService) as T
                modelClass.isAssignableFrom(PlannerLibraryViewModel::class.java) ->
                    PlannerLibraryViewModel(repository) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
