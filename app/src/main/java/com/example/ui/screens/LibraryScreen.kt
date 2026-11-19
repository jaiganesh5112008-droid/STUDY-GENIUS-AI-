package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.formatFileSize
import com.example.data.StudyNoteEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.StudyEmptyState
import com.example.ui.components.StudyTopBar
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareTextContent
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.LibraryFilter
import com.example.viewmodel.PlannerLibraryViewModel

private data class RenameTarget(
    val title: String,
    val onConfirm: (String) -> Unit
)

@Composable
fun LibraryScreen(
    viewModel: PlannerLibraryViewModel,
    onOpenQuiz: (Long) -> Unit,
    onOpenFlashcardDeck: (Long) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val extended = LocalStudyColors.current
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val quizzes by viewModel.quizzes.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val decks by viewModel.flashcardDecks.collectAsStateWithLifecycle()
    val docs by viewModel.uploadedDocs.collectAsStateWithLifecycle()

    var viewingNote by remember { mutableStateOf<StudyNoteEntity?>(null) }
    var renameTarget by remember { mutableStateOf<RenameTarget?>(null) }

    val qLower = query.trim().lowercase()

    val filteredQuizzes = remember(quizzes, qLower, filter) {
        if (filter != LibraryFilter.ALL && filter != LibraryFilter.QUIZZES) emptyList()
        else quizzes.filter { qLower.isEmpty() || it.title.lowercase().contains(qLower) || it.subject.lowercase().contains(qLower) }
    }
    val filteredNotes = remember(notes, qLower, filter) {
        when (filter) {
            LibraryFilter.ALL -> notes
            LibraryFilter.NOTES -> notes.filter { it.category != "AI_ANSWER" }
            LibraryFilter.AI_ANSWERS -> notes.filter { it.category == "AI_ANSWER" }
            else -> emptyList()
        }.filter { qLower.isEmpty() || it.title.lowercase().contains(qLower) || it.subject.lowercase().contains(qLower) || it.content.lowercase().contains(qLower) }
    }
    val filteredDecks = remember(decks, qLower, filter) {
        if (filter != LibraryFilter.ALL && filter != LibraryFilter.FLASHCARDS) emptyList()
        else decks.filter { qLower.isEmpty() || it.title.lowercase().contains(qLower) || it.subject.lowercase().contains(qLower) }
    }
    val filteredDocs = remember(docs, qLower, filter) {
        if (filter != LibraryFilter.ALL && filter != LibraryFilter.DOCUMENTS) emptyList()
        else docs.filter { qLower.isEmpty() || it.fileName.lowercase().contains(qLower) }
    }
    val filteredAttempts = remember(attempts, qLower, filter) {
        if (filter != LibraryFilter.ALL && filter != LibraryFilter.RESULTS) emptyList()
        else attempts.filter { qLower.isEmpty() || it.quizTitle.lowercase().contains(qLower) || it.subject.lowercase().contains(qLower) }
    }

    val totalMatches = filteredQuizzes.size + filteredNotes.size + filteredDecks.size + filteredDocs.size + filteredAttempts.size

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = "My Library",
            subtitle = "Offline quizzes, notes, flashcards & documents",
            onBack = onBack
        )

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search quizzes, notes, flashcards, documents...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("library_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (totalMatches == 0) {
                item {
                    StudyEmptyState(
                        icon = Icons.Filled.FolderOpen,
                        title = "Nothing saved here yet",
                        subtitle = "Generated quizzes, saved notes, flashcards, AI answers, and uploaded documents appear here automatically for offline access."
                    )
                }
            }

            if (filteredQuizzes.isNotEmpty()) {
                item { SectionHeader("Saved Quizzes (${filteredQuizzes.size})") }
                items(filteredQuizzes, key = { "quiz_${it.id}" }) { quiz ->
                    LibraryItemCard(
                        icon = Icons.Filled.Quiz,
                        accent = extended.electricBlue,
                        title = quiz.title,
                        subtitle = "${quiz.subject} • ${quiz.questionCount} Qs • ${quiz.difficulty} • ${quiz.sourceType}",
                        badge = "Play",
                        onClick = { onOpenQuiz(quiz.id) },
                        onRename = {
                            renameTarget = RenameTarget(quiz.title) { newTitle ->
                                viewModel.renameQuiz(quiz.id, newTitle)
                            }
                        },
                        onDelete = { viewModel.deleteQuiz(quiz.id) }
                    )
                }
            }

            if (filteredNotes.isNotEmpty()) {
                item { SectionHeader("Notes, Summaries & AI Answers (${filteredNotes.size})") }
                items(filteredNotes, key = { "note_${it.id}" }) { note ->
                    val badgeLabel = when (note.category) {
                        "SUMMARY" -> "Summary"
                        "AI_ANSWER" -> "AI Answer"
                        else -> "Notes"
                    }
                    LibraryItemCard(
                        icon = Icons.Filled.Description,
                        accent = extended.emeraldGlow,
                        title = note.title,
                        subtitle = "${note.subject} • ${note.style}",
                        badge = badgeLabel,
                        onClick = { viewingNote = note },
                        onRename = {
                            renameTarget = RenameTarget(note.title) { newTitle ->
                                viewModel.renameNote(note.id, newTitle)
                            }
                        },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
            }

            if (filteredDecks.isNotEmpty()) {
                item { SectionHeader("Flashcard Decks (${filteredDecks.size})") }
                items(filteredDecks, key = { "deck_${it.id}" }) { deck ->
                    LibraryItemCard(
                        icon = Icons.Filled.Style,
                        accent = extended.cyanGlow,
                        title = deck.title,
                        subtitle = "${deck.subject} • ${deck.masteredCount}/${deck.totalCount} mastered",
                        badge = "Study",
                        onClick = { onOpenFlashcardDeck(deck.id) },
                        onRename = {
                            renameTarget = RenameTarget(deck.title) { newTitle ->
                                viewModel.renameDeck(deck.id, newTitle)
                            }
                        },
                        onDelete = { viewModel.deleteDeck(deck.id) }
                    )
                }
            }

            if (filteredAttempts.isNotEmpty()) {
                item { SectionHeader("Quiz & Mock Test Results (${filteredAttempts.size})") }
                items(filteredAttempts, key = { "attempt_${it.id}" }) { attempt ->
                    LibraryItemCard(
                        icon = Icons.Filled.PlayArrow,
                        accent = extended.amberWarning,
                        title = attempt.quizTitle,
                        subtitle = "${attempt.subject} • ${attempt.score}/${attempt.totalQuestions} marks",
                        badge = "${attempt.percentage}%",
                        onClick = { onOpenQuiz(attempt.quizId) },
                        onRename = null,
                        onDelete = { viewModel.deleteAttempt(attempt.id) }
                    )
                }
            }

            if (filteredDocs.isNotEmpty()) {
                item { SectionHeader("Uploaded Documents (${filteredDocs.size})") }
                items(filteredDocs, key = { "doc_${it.id}" }) { doc ->
                    LibraryItemCard(
                        icon = Icons.Filled.UploadFile,
                        accent = extended.violetGlow,
                        title = doc.fileName,
                        subtitle = "${formatFileSize(doc.sizeBytes)} • ${doc.preview.take(60)}",
                        badge = "File",
                        onClick = {},
                        onRename = {
                            renameTarget = RenameTarget(doc.fileName) { newName ->
                                viewModel.renameDoc(doc.id, newName)
                            }
                        },
                        onDelete = { viewModel.deleteDoc(doc.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Full Note / Summary / AI Answer Reader Dialog
    viewingNote?.let { note ->
        AlertDialog(
            onDismissRequest = { viewingNote = null },
            title = { Text(note.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${note.subject} • ${note.style}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extended.emeraldGlow
                    )
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        copyTextToClipboard(context, note.title, note.content)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_copy_text))
                    }
                    IconButton(onClick = { shareTextContent(context, note.title, note.content) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_content))
                    }
                    TextButton(onClick = { viewingNote = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Rename Dialog
    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Item") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("New Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        target.onConfirm(text)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LibraryItemCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_rename_item),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_item),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
