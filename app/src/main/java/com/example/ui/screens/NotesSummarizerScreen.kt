package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.DocumentProcessor
import com.example.ai.formatFileSize
import com.example.data.StudyLanguage
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiThinkingBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.LanguagePillButton
import com.example.ui.components.StudyTopBar
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareTextContent
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.NotesSummarizerViewModel

@Composable
fun NotesSummarizerScreen(
    viewModel: NotesSummarizerViewModel,
    initialSummarizer: Boolean,
    language: StudyLanguage,
    onOpenLanguagePicker: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalStudyColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val noteStyles = listOf(
        "Short Notes",
        "Detailed Notes",
        "Important Points",
        "Definitions",
        "Formula Sheet",
        "Revision Notes",
        "Exam Prep Notes"
    )
    val subjects = listOf(
        "Physics", "Chemistry", "Biology", "Mathematics",
        "Computer Science", "History", "Economics", "English", "Tamil"
    )

    LaunchedEffect(initialSummarizer) {
        viewModel.setTab(initialSummarizer)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(context, it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = if (uiState.isSummarizerTab) "AI PDF Summarizer" else "AI Notes Generator",
            subtitle = if (uiState.isSummarizerTab) {
                "Chapters, concepts, definitions & formulas"
            } else {
                "Structured revision sheets in ${language.displayName}"
            },
            onBack = onBack,
            actions = {
                LanguagePillButton(language = language, onClick = onOpenLanguagePicker)
            }
        )

        // Toggle between Notes Generator & PDF Summarizer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tabs = listOf(false to "Notes Generator", true to "PDF Summarizer")
            tabs.forEach { (isSum, label) ->
                val active = uiState.isSummarizerTab == isSum
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setTab(isSum) }
                        .testTag(if (isSum) "tab_pdf_summarizer" else "tab_notes_generator"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) extended.emeraldGlow else extended.glassSurface,
                    border = BorderStroke(1.dp, if (active) extended.emeraldGlow else extended.glassBorder)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 11.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (active) Color(0xFF06101F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subjects.forEach { subj ->
                            FilterChip(
                                selected = uiState.subject == subj,
                                onClick = { viewModel.setSubject(subj) },
                                label = { Text(subj) }
                            )
                        }
                    }

                    if (!uiState.isSummarizerTab) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Note Format",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            noteStyles.forEach { style ->
                                FilterChip(
                                    selected = uiState.selectedNoteStyle == style,
                                    onClick = { viewModel.setNoteStyle(style) },
                                    label = { Text(style) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { filePicker.launch(DocumentProcessor.SUPPORTED_DOCUMENT_MIMES) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("notes_upload_file_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.isSummarizerTab) {
                                "Upload PDF, DOCX, TXT or Photo"
                            } else {
                                "Attach Reference Document (Optional)"
                            }
                        )
                    }

                    if (uiState.isReadingFile) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AiThinkingBanner(
                            title = "Reading uploaded document...",
                            subtitle = "Extracting text and verifying file format"
                        )
                    }

                    uiState.material?.let { mat ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = extended.emeraldGlow.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, extended.emeraldGlow.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = extended.emeraldGlow)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mat.displayName, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        formatFileSize(mat.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.clearMaterial() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove file")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.topicOrInput,
                        onValueChange = { viewModel.setTopicOrInput(it) },
                        label = {
                            Text(
                                if (uiState.isSummarizerTab) {
                                    "Or paste chapter text to summarize"
                                } else {
                                    "Topic or source study notes"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                if (uiState.isSummarizerTab) {
                                    "Paste textbook paragraphs or upload a PDF above..."
                                } else {
                                    "e.g., Laws of Thermodynamics, Cell Division, Indian Constitution"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notes_topic_input"),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    GradientActionButton(
                        text = if (uiState.isSummarizerTab) "SUMMARIZE DOCUMENT" else "GENERATE ${uiState.selectedNoteStyle.uppercase()}",
                        icon = Icons.Filled.AutoAwesome,
                        isLoading = uiState.isGenerating || uiState.isReadingFile,
                        enabled = !uiState.isGenerating && !uiState.isReadingFile,
                        onClick = { viewModel.generate(language) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "notes_generate_button"
                    )
                }
            }

            if (uiState.isGenerating && uiState.generatedContent.isBlank()) {
                item {
                    AiThinkingBanner(
                        title = if (uiState.isSummarizerTab) "Summarizing study material..." else "Crafting ${uiState.selectedNoteStyle}...",
                        subtitle = "Extracting key definitions, formulas, and structured points"
                    )
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    AiErrorCard(
                        message = err,
                        onRetry = { viewModel.generate(language) },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }

            if (uiState.generatedContent.isNotBlank()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = extended.cyanGlow.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isSummarizerTab) "AI Chapter Summary" else uiState.selectedNoteStyle,
                                style = MaterialTheme.typography.titleMedium,
                                color = extended.emeraldGlow
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        copyTextToClipboard(context, "Study Genius Notes", uiState.generatedContent)
                                        Toast.makeText(context, "Copied notes", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_copy_text),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.saveToLibrary() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("notes_save_button")
                                ) {
                                    Icon(
                                        if (uiState.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = stringResource(R.string.cd_save_library),
                                        tint = if (uiState.isSaved) extended.emeraldGlow else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        shareTextContent(
                                            context,
                                            "${uiState.subject} - ${uiState.selectedNoteStyle}",
                                            uiState.generatedContent
                                        )
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = stringResource(R.string.cd_share_content),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.generatedContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
