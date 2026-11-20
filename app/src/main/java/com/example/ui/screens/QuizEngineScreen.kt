package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.QuestionType
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AnimatedGoalRing
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.StudyTopBar
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.QuizEngineUiState
import com.example.viewmodel.QuizEngineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizEngineScreen(
    quizId: String,
    viewModel: QuizEngineViewModel,
    onBack: () -> Unit
) {
    val extended = LocalStudyColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showNavigatorSheet by remember { mutableStateOf(false) }

    LaunchedEffect(quizId) {
        viewModel.loadQuiz(quizId)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = extended.emeraldGlow)
        }
        return
    }

    if (uiState.quiz == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            StudyTopBar(title = "Quiz Engine", onBack = onBack)
            Spacer(modifier = Modifier.height(16.dp))
            AiErrorCard(message = uiState.errorMessage ?: "Unable to open quiz.")
        }
        return
    }

    if (uiState.isSubmitted) {
        QuizResultsView(
            uiState = uiState,
            onRetry = { viewModel.retryQuiz() },
            onDone = onBack
        )
        return
    }

    val quiz = uiState.quiz!!
    val currentQuestion = uiState.questions[uiState.currentIndex]
    val selectedAnswer = uiState.userAnswers[uiState.currentIndex].orEmpty()
    val isMarked = uiState.currentIndex in uiState.markedForReview
    val lowTime = uiState.remainingSeconds <= 60

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = quiz.title,
            subtitle = "${quiz.subject} • ${quiz.difficulty}",
            onBack = onBack,
            actions = {
                // Countdown Timer Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (lowTime) MaterialTheme.colorScheme.errorContainer else extended.cardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (lowTime) MaterialTheme.colorScheme.error else extended.cyanGlow.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Countdown timer",
                            modifier = Modifier.size(16.dp),
                            tint = if (lowTime) MaterialTheme.colorScheme.error else extended.emeraldGlow
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimer(uiState.remainingSeconds),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (lowTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = { showNavigatorSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("quiz_navigator_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.cd_question_navigator),
                        tint = extended.cyanGlow
                    )
                }
            }
        )

        // Progress Indicator & Counter
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${uiState.currentIndex + 1} of ${uiState.questions.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = extended.emeraldGlow
                )
                Text(
                    text = "${uiState.userAnswers.size} answered • ${uiState.markedForReview.size} review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (uiState.currentIndex + 1).toFloat() / uiState.questions.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = extended.emeraldGlow,
                trackColor = extended.glassBorder
            )
        }

        // Question & Answer Choices
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = extended.electricBlue.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = currentQuestion.type.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = extended.cyanGlow,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleMarkForReview(uiState.currentIndex) }
                                .testTag("quiz_mark_review_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMarked) extended.amberWarning.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isMarked) extended.amberWarning else extended.glassBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isMarked) Icons.Filled.Flag else Icons.Filled.OutlinedFlag,
                                    contentDescription = stringResource(R.string.cd_mark_review),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isMarked) extended.amberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMarked) "Marked" else "Review",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMarked) extended.amberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (currentQuestion.options.isNotEmpty()) {
                itemsIndexed(currentQuestion.options) { optIdx, optionText ->
                    val letter = ('A' + optIdx).toString()
                    val isSelected = selectedAnswer.equals(optionText, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.selectAnswer(uiState.currentIndex, optionText) }
                            .testTag("quiz_option_$optIdx"),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) extended.emeraldGlow.copy(alpha = 0.18f) else extended.glassSurface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) extended.emeraldGlow else extended.glassBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) extended.emeraldGlow else extended.cardElevated
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color(0xFF06101F) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = selectedAnswer,
                        onValueChange = { viewModel.selectAnswer(uiState.currentIndex, it) },
                        label = {
                            Text(
                                if (currentQuestion.type == QuestionType.FILL_BLANKS) {
                                    "Type the missing word or phrase"
                                } else {
                                    "Write your short answer"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_text_answer_input"),
                        minLines = if (currentQuestion.type == QuestionType.SHORT_ANSWER) 3 else 1,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.previousQuestion() },
                    enabled = uiState.currentIndex > 0,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("quiz_prev_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev")
                }

                if (uiState.currentIndex < uiState.questions.lastIndex) {
                    Button(
                        onClick = { viewModel.nextQuestion() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("quiz_next_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Next Question")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = { showSubmitConfirm = true },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("quiz_submit_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Submit")
                    }
                } else {
                    GradientActionButton(
                        text = "Submit Test",
                        onClick = { showSubmitConfirm = true },
                        modifier = Modifier.weight(1f),
                        testTag = "quiz_submit_button"
                    )
                }
            }
        }
    }

    // Submit Confirmation Dialog
    if (showSubmitConfirm) {
        val unanswered = uiState.questions.size - uiState.userAnswers.count { it.value.isNotBlank() }
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit Test?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Answered: ${uiState.questions.size - unanswered} / ${uiState.questions.size}")
                    if (unanswered > 0) {
                        Text(
                            text = "Unanswered questions: $unanswered",
                            color = extended.amberWarning
                        )
                    }
                    if (uiState.markedForReview.isNotEmpty()) {
                        Text(
                            text = "Marked for review: ${uiState.markedForReview.size}",
                            color = extended.amberWarning
                        )
                    }
                    Text("Once submitted, your score and question-wise explanations will be calculated immediately.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        viewModel.submitQuiz()
                    },
                    modifier = Modifier.testTag("confirm_submit_quiz_button")
                ) {
                    Text("Submit Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text("Continue Quiz")
                }
            }
        )
    }

    // Question Navigator Sheet
    if (showNavigatorSheet) {
        ModalBottomSheet(onDismissRequest = { showNavigatorSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Question Navigator",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap any number to jump directly to that question.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Current" to extended.electricBlue,
                        "Answered" to extended.emeraldGlow,
                        "Review" to extended.amberWarning
                    ).forEach { (label, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(uiState.questions.size) { index ->
                        val isCurrent = index == uiState.currentIndex
                        val isAnswered = !uiState.userAnswers[index].isNullOrBlank()
                        val isReview = index in uiState.markedForReview
                        val bg = when {
                            isCurrent -> extended.electricBlue
                            isReview -> extended.amberWarning.copy(alpha = 0.25f)
                            isAnswered -> extended.emeraldGlow.copy(alpha = 0.22f)
                            else -> extended.glassSurface
                        }
                        val border = when {
                            isCurrent -> extended.electricBlue
                            isReview -> extended.amberWarning
                            isAnswered -> extended.emeraldGlow
                            else -> extended.glassBorder
                        }
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.goToQuestion(index)
                                    showNavigatorSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = bg,
                            border = BorderStroke(1.5.dp, border)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuizResultsView(
    uiState: QuizEngineUiState,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    val extended = LocalStudyColors.current
    val total = uiState.gradedResults.size
    val correct = uiState.score
    val wrong = total - correct

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = "Quiz Results",
            subtitle = uiState.quiz?.title,
            onBack = onDone
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = extended.emeraldGlow.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        AnimatedGoalRing(
                            progress = uiState.percentage / 100f,
                            centerLabel = "${uiState.percentage}%",
                            size = 96.dp,
                            strokeWidth = 10.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    uiState.percentage >= 85 -> "Outstanding Mastery!"
                                    uiState.percentage >= 65 -> "Solid Performance!"
                                    else -> "Keep Practicing!"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Score: $correct / $total marks",
                                style = MaterialTheme.typography.titleMedium,
                                color = extended.emeraldGlow
                            )
                            Text(
                                text = "$correct Correct • $wrong Incorrect • Saved to History",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GradientActionButton(
                            text = "Retry Quiz",
                            icon = Icons.Filled.Refresh,
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            testTag = "quiz_retry_button"
                        )
                        OutlinedButton(
                            onClick = onDone,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("quiz_results_done_button"),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Question-wise Breakdown & Explanations",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            itemsIndexed(uiState.gradedResults) { idx, result ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (result.isCorrect) {
                        extended.emeraldGlow.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (result.isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = if (result.isCorrect) "Correct" else "Incorrect",
                            tint = if (result.isCorrect) extended.emeraldGlow else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Question ${idx + 1} • ${if (result.isCorrect) "Correct" else "Incorrect"}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (result.isCorrect) extended.emeraldGlow else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.question.question,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Answer: ${result.userAnswer.ifBlank { "Not answered" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.isCorrect) extended.emeraldGlow else MaterialTheme.colorScheme.error
                    )
                    if (!result.isCorrect) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Correct Answer: ${result.question.correctAnswer}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = extended.emeraldGlow
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extended.cardElevated
                    ) {
                        Text(
                            text = "Explanation: ${result.question.explanation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d", mins, secs)
}
