package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudQuiz
import com.example.data.CloudQuizAttempt
import com.example.data.CloudStudyNote
import com.example.data.CloudUiState
import com.example.data.ExamCountdownEntity
import com.example.data.FlashcardDeckEntity
import com.example.data.OperationType
import com.example.data.PlannerTaskEntity
import com.example.data.StudyRepository
import com.example.data.UploadedDocEntity
import com.example.data.friendlyFirestoreMessage
import com.example.data.handleFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"),
    QUIZZES("Quizzes"),
    NOTES("Notes & Summaries"),
    FLASHCARDS("Flashcards"),
    AI_ANSWERS("AI Answers"),
    DOCUMENTS("Documents"),
    RESULTS("Results")
}

class PlannerLibraryViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String
) : ViewModel() {

    val plannerTasks: StateFlow<List<PlannerTaskEntity>> = repository.plannerTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val examCountdowns: StateFlow<List<ExamCountdownEntity>> = repository.examCountdowns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val quizzesState: StateFlow<CloudUiState<List<CloudQuiz>>> =
        repository.observeCloudQuizzes(currentUserId)
            .map<List<CloudQuiz>, CloudUiState<List<CloudQuiz>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/quizzes")
                Log.w(TAG, "Error observing quizzes", rootError)
                emit(CloudUiState.Error(friendlyFirestoreMessage(rootError, "Failed to load cloud quizzes.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val attemptsState: StateFlow<CloudUiState<List<CloudQuizAttempt>>> =
        repository.observeCloudAttempts(currentUserId)
            .map<List<CloudQuizAttempt>, CloudUiState<List<CloudQuizAttempt>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/quizAttempts")
                Log.w(TAG, "Error observing quiz attempts", rootError)
                emit(CloudUiState.Error(friendlyFirestoreMessage(rootError, "Failed to load quiz history.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val notesState: StateFlow<CloudUiState<List<CloudStudyNote>>> =
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
                emit(CloudUiState.Error(friendlyFirestoreMessage(rootError, "Failed to load cloud notes.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val flashcardDecks: StateFlow<List<FlashcardDeckEntity>> = repository.flashcardDecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val uploadedDocs: StateFlow<List<UploadedDocEntity>> = repository.uploadedDocs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    // Planner Actions (Local Room)
    fun addTask(
        title: String,
        subject: String,
        dayLabel: String,
        timeSlot: String,
        reminderLabel: String,
        durationMinutes: Int
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addPlannerTask(
                PlannerTaskEntity(
                    title = title.trim(),
                    subject = subject.trim().ifBlank { "General" },
                    dayLabel = dayLabel,
                    timeSlot = timeSlot.ifBlank { "06:00 PM - 07:00 PM" },
                    reminderLabel = reminderLabel,
                    durationMinutes = durationMinutes.coerceIn(10, 240)
                )
            )
        }
    }

    fun toggleTaskCompleted(task: PlannerTaskEntity) {
        viewModelScope.launch {
            repository.setTaskCompleted(task.id, !task.isCompleted, task.durationMinutes)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repository.deletePlannerTask(id) }
    }

    fun addExamCountdown(examName: String, subject: String, daysFromToday: Int) {
        if (examName.isBlank()) return
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        viewModelScope.launch {
            repository.addExamCountdown(
                ExamCountdownEntity(
                    examName = examName.trim(),
                    subject = subject.trim().ifBlank { "All Subjects" },
                    targetEpochDay = todayEpochDay + daysFromToday.coerceIn(1, 365)
                )
            )
        }
    }

    fun deleteExamCountdown(id: Long) {
        viewModelScope.launch { repository.deleteExamCountdown(id) }
    }

    // Cloud Library Rename & Delete Actions
    fun renameQuiz(id: String, newTitle: String) {
        if (id.isBlank() || newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameCloudQuiz(currentUserId, id, newTitle.trim())
                .onFailure { err ->
                    _actionMessage.value = friendlyFirestoreMessage(err, "Failed to rename quiz.")
                }
        }
    }

    fun deleteQuiz(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            repository.deleteCloudQuiz(currentUserId, id)
                .onFailure { err ->
                    _actionMessage.value = friendlyFirestoreMessage(err, "Failed to delete quiz.")
                }
        }
    }

    fun renameNote(id: String, newTitle: String) {
        if (id.isBlank() || newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameCloudNote(currentUserId, id, newTitle.trim())
                .onFailure { err ->
                    _actionMessage.value = friendlyFirestoreMessage(err, "Failed to rename note.")
                }
        }
    }

    fun deleteNote(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            repository.deleteCloudNote(currentUserId, id)
                .onFailure { err ->
                    _actionMessage.value = friendlyFirestoreMessage(err, "Failed to delete note.")
                }
        }
    }

    fun deleteAttempt(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            repository.deleteCloudAttempt(currentUserId, id)
                .onFailure { err ->
                    _actionMessage.value = friendlyFirestoreMessage(err, "Failed to delete quiz result.")
                }
        }
    }

    // Local Flashcards & Docs Actions
    fun renameDeck(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { repository.renameFlashcardDeck(id, newTitle.trim()) }
    }

    fun deleteDeck(id: Long) {
        viewModelScope.launch { repository.deleteFlashcardDeck(id) }
    }

    fun renameDoc(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameUploadedDoc(id, newName.trim()) }
    }

    fun deleteDoc(id: Long) {
        viewModelScope.launch { repository.deleteUploadedDoc(id) }
    }

    private companion object {
        const val TAG = "PlannerLibraryVM"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
