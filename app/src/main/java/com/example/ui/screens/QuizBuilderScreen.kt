package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.DocumentProcessor
import com.example.ai.PreparedMaterial
import com.example.ai.formatFileSize
import com.example.data.QuestionType
import com.example.data.QuizDifficulty
import com.example.data.StudyLanguage
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiThinkingBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.LanguagePillButton
import com.example.ui.components.StudyTopBar
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.QuizBuilderMode
import com.example.viewmodel.QuizHubViewModel
import kotlin.math.roundToInt

@Composable
fun QuizBuilderScreen(
    viewModel: QuizHubViewModel,
    initialMode: QuizBuilderMode,
    language: StudyLanguage,
    onOpenLanguagePicker: () -> Unit,
    onQuizGenerated: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalStudyColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val subjects = listOf(
        "Science", "Mathematics", "Physics", "Chemistry", "Biology",
        "Computer Science", "History", "Geography", "Economics", "English", "Tamil"
    )

    LaunchedEffect(initialMode) {
        viewModel.setMode(initialMode)
    }

    LaunchedEffect(uiState.generatedQuizId) {
        uiState.generatedQuizId?.let { quizId ->
            viewModel.consumeGeneratedQuizNavigation()
            onQuizGenerated(quizId)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(context, it) }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(context, it) }
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { viewModel.onFilePicked(context, it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = DocumentProcessor.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is needed to capture study pages.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = DocumentProcessor.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = uiState.mode.title,
            subtitle = uiState.mode.subtitle,
            onBack = onBack,
            actions = {
                LanguagePillButton(language = language, onClick = onOpenLanguagePicker)
            }
        )

        // Mode Switcher Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuizBuilderMode.entries.forEach { mode ->
                val active = uiState.mode == mode
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setMode(mode) }
                        .testTag("quiz_mode_${mode.name}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) extended.emeraldGlow else extended.glassSurface,
                    border = BorderStroke(1.dp, if (active) extended.emeraldGlow else extended.glassBorder)
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (active) Color(0xFF06101F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Source Input Section (Document / Image / Mock Topic)
            if (uiState.mode != QuizBuilderMode.MOCK_TEST) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (uiState.mode == QuizBuilderMode.IMAGE_TO_QUIZ) {
                                "1. Capture or Upload Study Image"
                            } else {
                                "1. Upload Study Material (PDF, DOCX, TXT, Image)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Questions are generated strictly from your uploaded source material. Max size: 15 MB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.mode == QuizBuilderMode.PDF_TO_QUIZ) {
                                OutlinedButton(
                                    onClick = {
                                        documentPicker.launch(DocumentProcessor.SUPPORTED_DOCUMENT_MIMES)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("upload_document_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File")
                                }
                            }
                            OutlinedButton(
                                onClick = { launchCamera() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("capture_camera_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera")
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pick_gallery_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery")
                            }
                        }

                        if (uiState.isReadingFile) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AiThinkingBanner(
                                title = "Validating & reading file...",
                                subtitle = "Checking file format, size, and readability"
                            )
                        }

                        uiState.material?.let { mat ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = extended.emeraldGlow.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, extended.emeraldGlow.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (mat is PreparedMaterial.Image) {
                                        Image(
                                            bitmap = mat.bitmap.asImageBitmap(),
                                            contentDescription = mat.displayName,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(extended.electricBlue.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                tint = extended.emeraldGlow
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mat.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatFileSize(mat.sizeBytes)} • Ready for AI extraction",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extended.emeraldGlow
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeMaterial() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove file",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.mode == QuizBuilderMode.PDF_TO_QUIZ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.pastedNotes,
                                onValueChange = { viewModel.setPastedNotes(it) },
                                label = { Text("Or paste study notes / chapter excerpt") },
                                placeholder = { Text("Paste paragraphs, lecture notes, or textbook content here...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("paste_notes_input"),
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            } else {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "1. Mock Exam Topic",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.topic,
                            onValueChange = { viewModel.setTopic(it) },
                            label = { Text("Chapter or Exam Topic") },
                            placeholder = { Text("e.g., Electrostatics, Organic Chemistry, World War II, Trigonometry") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mock_topic_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Quiz Configuration Section
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "2. Quiz Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

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

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Difficulty",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuizDifficulty.entries.forEach { diff ->
                            FilterChip(
                                selected = uiState.difficulty == diff,
                                onClick = { viewModel.setDifficulty(diff) },
                                label = { Text(diff.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Question Type",
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
                        QuestionType.entries.forEach { type ->
                            FilterChip(
                                selected = uiState.questionType == type,
                                onClick = { viewModel.setQuestionType(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Number of Questions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${uiState.questionCount} questions",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = extended.emeraldGlow
                        )
                    }
                    Slider(
                        value = uiState.questionCount.toFloat(),
                        onValueChange = { viewModel.setQuestionCount(it.roundToInt()) },
                        valueRange = 3f..20f,
                        steps = 16
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Exam Timer Duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${uiState.durationMinutes} min",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = extended.cyanGlow
                        )
                    }
                    Slider(
                        value = uiState.durationMinutes.toFloat(),
                        onValueChange = { viewModel.setDurationMinutes(it.roundToInt()) },
                        valueRange = 3f..60f,
                        steps = 18
                    )
                }
            }

            if (uiState.isGenerating) {
                item {
                    AiThinkingBanner(
                        title = "Generating your ${uiState.questionCount}-question ${uiState.questionType.shortLabel} quiz...",
                        subtitle = "Reading source material and crafting grounded questions with explanations in ${language.displayName}"
                    )
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    AiErrorCard(
                        message = err,
                        onRetry = { viewModel.generateQuiz(language) },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }

            item {
                GradientActionButton(
                    text = if (uiState.mode == QuizBuilderMode.MOCK_TEST) "GENERATE MOCK TEST" else "GENERATE QUIZ",
                    icon = Icons.Filled.AutoAwesome,
                    isLoading = uiState.isGenerating,
                    enabled = !uiState.isGenerating && !uiState.isReadingFile,
                    onClick = { viewModel.generateQuiz(language) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "generate_quiz_submit_button"
                )
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}
