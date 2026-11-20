package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.R
import com.example.ai.StudyAiService
import com.example.ai.ensureFirebaseInitialized
import com.example.auth.GoogleAuthManager
import com.example.data.FirestoreStudyRepository
import com.example.data.StudyDatabase
import com.example.data.StudyRepository
import com.example.data.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = StudyDatabase.getInstance(appContext)
    private val prefs = UserPreferencesRepository(appContext)

    private val firestoreDatabaseId: String =
        appContext.getString(R.string.firestore_database_id)

    // Ensure FirebaseApp is initialized before obtaining FirebaseAuth and named Firestore instances
    private val firebaseApp by lazy {
        checkNotNull(ensureFirebaseInitialized(appContext)) {
            "FirebaseApp could not be initialized."
        }
    }

    val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance(firebaseApp)
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(firebaseApp, firestoreDatabaseId)
    }

    val cloudRepository: FirestoreStudyRepository by lazy {
        FirestoreStudyRepository(firestore, firebaseAuth)
    }

    val repository: StudyRepository by lazy {
        StudyRepository(database.studyDao(), prefs, cloudRepository)
    }

    val aiService = StudyAiService(appContext)
    val authManager: GoogleAuthManager by lazy { GoogleAuthManager(firebaseAuth) }

    /**
     * Scopes all feature ViewModels to the authenticated Google user's UID so account switches
     * and sign-outs cleanly dispose previous Firestore snapshot listeners.
     */
    fun viewModelFactoryForUser(currentUserId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(MainViewModel::class.java) ->
                        MainViewModel(repository, currentUserId) as T
                    modelClass.isAssignableFrom(ChatViewModel::class.java) ->
                        ChatViewModel(repository, aiService, currentUserId) as T
                    modelClass.isAssignableFrom(QuizHubViewModel::class.java) ->
                        QuizHubViewModel(repository, aiService, currentUserId) as T
                    modelClass.isAssignableFrom(QuizEngineViewModel::class.java) ->
                        QuizEngineViewModel(repository, currentUserId) as T
                    modelClass.isAssignableFrom(NotesSummarizerViewModel::class.java) ->
                        NotesSummarizerViewModel(repository, aiService, currentUserId) as T
                    modelClass.isAssignableFrom(FlashcardsViewModel::class.java) ->
                        FlashcardsViewModel(repository, aiService) as T
                    modelClass.isAssignableFrom(PlannerLibraryViewModel::class.java) ->
                        PlannerLibraryViewModel(repository, currentUserId) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
}
