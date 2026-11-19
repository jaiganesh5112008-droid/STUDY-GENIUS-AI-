package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExamCountdownEntity
import com.example.data.FlashcardDeckEntity
import com.example.data.PlannerTaskEntity
import com.example.data.QuizAttemptEntity
import com.example.data.QuizEntity
import com.example.data.StudyNoteEntity
import com.example.data.StudyRepository
import com.example.data.UploadedDocEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val repository: StudyRepository
) : ViewModel() {

    val plannerTasks: StateFlow<List<PlannerTaskEntity>> = repository.plannerTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val examCountdowns: StateFlow<List<ExamCountdownEntity>> = repository.examCountdowns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quizzes: StateFlow<List<QuizEntity>> = repository.quizzes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val attempts: StateFlow<List<QuizAttemptEntity>> = repository.attempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<StudyNoteEntity>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val flashcardDecks: StateFlow<List<FlashcardDeckEntity>> = repository.flashcardDecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uploadedDocs: StateFlow<List<UploadedDocEntity>> = repository.uploadedDocs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    // Planner Actions
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

    // Library Rename & Delete Actions
    fun renameQuiz(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { repository.renameQuiz(id, newTitle.trim()) }
    }

    fun deleteQuiz(id: Long) {
        viewModelScope.launch { repository.deleteQuiz(id) }
    }

    fun renameNote(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { repository.renameNote(id, newTitle.trim()) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
    }

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

    fun deleteAttempt(id: Long) {
        viewModelScope.launch { repository.deleteAttempt(id) }
    }
}
