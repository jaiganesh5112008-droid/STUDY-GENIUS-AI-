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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.DocumentProcessor
import com.example.data.ChatMessageEntity
import com.example.data.StudyLanguage
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiThinkingBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.LanguagePillButton
import com.example.ui.components.StudyTopBar
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.shareTextContent
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.ChatViewModel

@Composable
fun ChatAssistantScreen(
    viewModel: ChatViewModel,
    language: StudyLanguage,
    initialDoubtMode: Boolean = false,
    onOpenLanguagePicker: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val extended = LocalStudyColors.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()

    val subjects = listOf("General", "Mathematics", "Physics", "Chemistry", "Biology", "Computer Science", "History", "Tamil", "English")
    val modes = listOf("Step-by-Step", "Simple Explain", "Math Solver", "Doubt Solver", "Exam Prep")

    LaunchedEffect(initialDoubtMode) {
        if (initialDoubtMode) {
            viewModel.setMode("Doubt Solver")
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(messages.size, uiState.streamingText.length) {
        if (messages.isNotEmpty() || uiState.isStreaming) {
            listState.animateScrollToItem((messages.size + if (uiState.isStreaming) 1 else 0).coerceAtLeast(0))
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachImageFromUri(context, it) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { viewModel.attachImageFromUri(context, it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = DocumentProcessor.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to snap study problems.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = DocumentProcessor.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        StudyTopBar(
            title = if (uiState.selectedMode == "Doubt Solver") "AI Doubt Solver" else "AI Study Assistant",
            subtitle = "${uiState.selectedSubject} • ${language.displayName}",
            onBack = onBack,
            actions = {
                LanguagePillButton(language = language, onClick = onOpenLanguagePicker)
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.cd_clear_chat),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        // Subject Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subjects.forEach { subj ->
                FilterChip(
                    selected = uiState.selectedSubject == subj,
                    onClick = { viewModel.setSubject(subj) },
                    label = { Text(subj, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Mode Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { mode ->
                val active = uiState.selectedMode == mode
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setMode(mode) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) extended.cyanGlow.copy(alpha = 0.2f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (active) extended.cyanGlow else extended.glassBorder)
                ) {
                    Text(
                        text = mode,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) extended.emeraldGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Conversation Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && !uiState.isStreaming) {
                item {
                    ChatWelcomeCard(
                        language = language,
                        onPromptClick = { starter ->
                            viewModel.sendMessage(starter, language)
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isLastAssistant = msg.role == "assistant" && msg.id == messages.lastOrNull()?.id
                ChatBubbleItem(
                    message = msg,
                    showRegenerate = isLastAssistant && !uiState.isStreaming,
                    onCopy = {
                        copyTextToClipboard(context, "Study Genius Answer", msg.text)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onSave = { viewModel.saveAnswerToLibrary(msg) },
                    onShare = { shareTextContent(context, "Study Genius AI Explanation", msg.text) },
                    onRegenerate = { viewModel.regenerateLastAnswer(language) }
                )
            }

            if (uiState.isStreaming) {
                item {
                    if (uiState.streamingText.isBlank()) {
                        AiThinkingBanner(
                            title = "Thinking through your question...",
                            subtitle = "Preparing a ${uiState.selectedMode} explanation in ${language.displayName}"
                        )
                    } else {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = extended.emeraldGlow.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "Study Genius AI • Streaming",
                                style = MaterialTheme.typography.labelSmall,
                                color = extended.emeraldGlow
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.streamingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    AiErrorCard(
                        message = err,
                        onRetry = {
                            val prompt = uiState.lastUserPrompt
                            if (!prompt.isNullOrBlank()) {
                                viewModel.regenerateLastAnswer(language)
                            } else {
                                viewModel.clearError()
                            }
                        },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }
        }

        // Attached Image Preview Chip
        uiState.attachedImage?.let { bmp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extended.cardElevated,
                    border = BorderStroke(1.dp, extended.cyanGlow.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached study image",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.attachedImageName ?: "Study Photo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ready for visual doubt solving",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.emeraldGlow
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearAttachedImage() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Input Composer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { launchCamera() },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("chat_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.cd_attach_image),
                        tint = extended.cyanGlow
                    )
                }
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("chat_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "Choose photo from gallery",
                        tint = extended.electricBlue
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    placeholder = {
                        Text(
                            text = "Ask any question or math problem...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp)
                )
                val canSend = !uiState.isStreaming && (inputText.isNotBlank() || uiState.attachedImage != null)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) extended.emeraldGlow else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = canSend) {
                            val toSend = inputText
                            inputText = ""
                            viewModel.sendMessage(toSend, language)
                        }
                        .testTag("chat_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_message),
                        tint = if (canSend) Color(0xFF06101F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear conversation?") },
            text = { Text("This removes your chat history from this device. Saved AI answers remain in My Library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatWelcomeCard(
    language: StudyLanguage,
    onPromptClick: (String) -> Unit
) {
    val extended = LocalStudyColors.current
    val starters = listOf(
        "Explain Newton's Three Laws of Motion with simple real-life examples",
        "Solve step by step: 2x² - 5x - 12 = 0",
        "Explain Photosynthesis clearly for exam revision",
        "Compare Mitosis and Meiosis in a concise table"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_study_companion),
                    contentDescription = stringResource(R.string.cd_study_companion),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your AI Study Companion",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ask any question, solve math step-by-step, or snap a textbook photo. Currently responding in ${language.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = "Try asking:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        starters.forEach { prompt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPromptClick(prompt) },
                shape = RoundedCornerShape(16.dp),
                color = extended.glassSurface,
                border = BorderStroke(1.dp, extended.glassBorder)
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessageEntity,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val extended = LocalStudyColors.current
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 1f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            color = if (isUser) extended.electricBlue.copy(alpha = 0.22f) else extended.glassSurface,
            border = BorderStroke(
                1.dp,
                if (isUser) extended.electricBlue.copy(alpha = 0.6f) else extended.glassBorder
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You • ${message.subject}" else "Study Genius AI • ${message.mode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) extended.cyanGlow else extended.emeraldGlow
                    )
                    if (message.hasImage) {
                        Text(
                            text = "Photo attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = extended.amberWarning
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_text),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onSave, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (message.savedToLibrary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = stringResource(R.string.cd_save_library),
                                modifier = Modifier.size(18.dp),
                                tint = if (message.savedToLibrary) extended.emeraldGlow else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_content),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showRegenerate) {
                            IconButton(onClick = onRegenerate, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.cd_regenerate),
                                    modifier = Modifier.size(18.dp),
                                    tint = extended.cyanGlow
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
