package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.DocumentProcessor
import com.example.data.StudyLanguage
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiThinkingBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.LanguagePillButton
import com.example.ui.components.StudyEmptyState
import com.example.ui.components.StudyTopBar
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.FlashcardsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    viewModel: FlashcardsViewModel,
    language: StudyLanguage,
    onOpenLanguagePicker: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalStudyColors.current
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(context, it) }
    }

    // Active 3D Flip Deck Study View
    if (uiState.activeDeck != null) {
        val deck = uiState.activeDeck!!
        val masteredCount = uiState.activeCards.count { it.mastered }
        val totalCount = uiState.activeCards.size.coerceAtLeast(1)
        val currentRealIdx = uiState.visibleIndices.getOrNull(uiState.currentVisiblePos)
        val currentCard = currentRealIdx?.let { uiState.activeCards.getOrNull(it) }

        val rotation by animateFloatAsState(
            targetValue = if (uiState.isFlipped) 180f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "flashcardFlip"
        )
        val density = LocalDensity.current.density

        Column(modifier = Modifier.fillMaxSize()) {
            StudyTopBar(
                title = deck.title,
                subtitle = "$masteredCount / $totalCount mastered",
                onBack = { viewModel.closeActiveDeck() },
                actions = {
                    FilterChip(
                        selected = uiState.revisionModeOnlyMissed,
                        onClick = { viewModel.toggleRevisionMode() },
                        label = { Text("Revision") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { masteredCount.toFloat() / totalCount.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = extended.emeraldGlow,
                    trackColor = extended.glassBorder
                )

                if (currentCard == null) {
                    StudyEmptyState(
                        icon = Icons.Filled.Check,
                        title = "All cards mastered!",
                        subtitle = "You've mastered every card in Revision Mode. Switch off Revision Mode to review the full deck again.",
                        actionLabel = "Review Full Deck",
                        onAction = { viewModel.toggleRevisionMode() }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Card ${uiState.currentVisiblePos + 1} of ${uiState.visibleIndices.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = extended.cyanGlow
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentCard.mastered) {
                                extended.emeraldGlow.copy(alpha = 0.2f)
                            } else {
                                extended.amberWarning.copy(alpha = 0.18f)
                            }
                        ) {
                            Text(
                                text = if (currentCard.mastered) "Mastered" else "Learning",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (currentCard.mastered) extended.emeraldGlow else extended.amberWarning,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 3D Flip Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .clickable { viewModel.toggleFlip() }
                            .testTag("flashcard_flip_surface"),
                        contentAlignment = Alignment.Center
                    ) {
                        val showBack = rotation > 90f
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (showBack) rotationY = 180f
                                },
                            shape = RoundedCornerShape(28.dp),
                            color = if (showBack) extended.cardElevated else extended.glassSurface,
                            border = BorderStroke(
                                1.5.dp,
                                if (showBack) extended.emeraldGlow else extended.cyanGlow.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (showBack) "ANSWER" else "QUESTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showBack) extended.emeraldGlow else extended.cyanGlow
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (showBack) currentCard.back else currentCard.front,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                if (!showBack && currentCard.hint.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = extended.electricBlue.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Hint: ${currentCard.hint}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = extended.cyanGlow,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Tap card to flip",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Know It / Don't Know It Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.markCurrentCard(mastered = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("flashcard_dont_know_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, extended.amberWarning)
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, tint = extended.amberWarning)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Don't Know", color = extended.amberWarning)
                        }
                        Button(
                            onClick = { viewModel.markCurrentCard(mastered = true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("flashcard_know_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extended.emeraldGlow,
                                contentColor = Color(0xFF06101F)
                            )
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Know It", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Prev / Next Nav
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previousCard() },
                            enabled = uiState.currentVisiblePos > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev")
                        }
                        OutlinedButton(
                            onClick = { viewModel.nextCard() },
                            enabled = uiState.currentVisiblePos < uiState.visibleIndices.lastIndex
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        return
    }

    // Decks List View
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.setShowGenerator(true) },
                containerColor = extended.emeraldGlow,
                contentColor = Color(0xFF06101F),
                modifier = Modifier.testTag("flashcards_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_item))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StudyTopBar(
                title = "Flashcards",
                subtitle = "3D active recall & revision decks",
                onBack = onBack,
                actions = {
                    LanguagePillButton(language = language, onClick = onOpenLanguagePicker)
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (decks.isEmpty()) {
                    item {
                        StudyEmptyState(
                            icon = Icons.Filled.Style,
                            title = "Create your first AI Flashcard Deck",
                            subtitle = "Enter any topic or upload a PDF/note to automatically generate interactive Q&A flashcards.",
                            actionLabel = "Generate Flashcards",
                            onAction = { viewModel.setShowGenerator(true) }
                        )
                    }
                } else {
                    items(decks, key = { it.id }) { deck ->
                        val progress = if (deck.totalCount > 0) {
                            deck.masteredCount.toFloat() / deck.totalCount.toFloat()
                        } else 0f

                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.openDeck(deck.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deck.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${deck.subject} • ${deck.masteredCount}/${deck.totalCount} mastered",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteDeck(deck.id) }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_delete_item),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = extended.emeraldGlow,
                                trackColor = extended.glassBorder
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    // AI Flashcard Deck Generator Bottom Sheet
    if (uiState.showGeneratorSheet) {
        ModalBottomSheet(onDismissRequest = { if (!uiState.isGenerating) viewModel.setShowGenerator(false) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Generate AI Flashcard Deck",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = uiState.genSubject,
                    onValueChange = { viewModel.setGenSubject(it) },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = uiState.genTopic,
                    onValueChange = { viewModel.setGenTopic(it) },
                    label = { Text("Topic or pasted notes") },
                    placeholder = { Text("e.g., Human Circulatory System, Periodic Table Trends") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("flashcard_topic_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedButton(
                    onClick = { docPicker.launch(DocumentProcessor.SUPPORTED_DOCUMENT_MIMES) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(uiState.genMaterial?.displayName ?: "Attach PDF / DOCX / TXT / Image (Optional)")
                    if (uiState.genMaterial != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.clearGenMaterial() }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Number of Flashcards", style = MaterialTheme.typography.labelLarge)
                    Text("${uiState.genCount} cards", color = extended.emeraldGlow, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = uiState.genCount.toFloat(),
                    onValueChange = { viewModel.setGenCount(it.roundToInt()) },
                    valueRange = 4f..20f,
                    steps = 15
                )

                if (uiState.isGenerating) {
                    AiThinkingBanner(
                        title = "Creating ${uiState.genCount} flashcards...",
                        subtitle = "Generating active-recall questions and hints in ${language.displayName}"
                    )
                }

                uiState.errorMessage?.let { err ->
                    AiErrorCard(message = err, onRetry = { viewModel.generateDeck(language) })
                }

                GradientActionButton(
                    text = "GENERATE DECK",
                    icon = Icons.Filled.AutoAwesome,
                    isLoading = uiState.isGenerating || uiState.isReadingFile,
                    enabled = !uiState.isGenerating && !uiState.isReadingFile,
                    onClick = { viewModel.generateDeck(language) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "flashcard_generate_submit_button"
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
