package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StudyLanguage
import com.example.ui.components.AnimatedGoalRing
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.LanguagePillButton
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.DashboardUiState

data class QuickToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun HomeDashboardScreen(
    uiState: DashboardUiState,
    onOpenLanguagePicker: () -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onNavigateTool: (String) -> Unit,
    onOpenQuiz: (String) -> Unit
) {
    val extended = LocalStudyColors.current
    val quotes = remember {
        listOf(
            "\"Every practice question you solve today builds confidence for exam day.\"",
            "\"Active recall and spaced flashcards double long-term memory retention.\"",
            "\"Small consistent study sessions beat last-minute cramming every time.\"",
            "\"Turn your toughest PDF chapters into interactive quizzes and conquer them.\""
        )
    }
    var quoteIndex by remember { mutableIntStateOf(0) }

    val quickTools = listOf(
        QuickToolItem("chat", "AI Assistant", "Step-by-step tutor", Icons.AutoMirrored.Filled.Chat, extended.emeraldGlow),
        QuickToolItem("pdf_quiz", "PDF to Quiz", "Docs to exam MCQs", Icons.Filled.Quiz, extended.electricBlue),
        QuickToolItem("image_quiz", "Image to Quiz", "Scan pages & diagrams", Icons.Filled.CameraAlt, extended.cyanGlow),
        QuickToolItem("notes", "AI Notes", "Formulas & revision", Icons.Filled.EditNote, extended.violetGlow),
        QuickToolItem("summarizer", "PDF Summarizer", "Chapter breakdowns", Icons.Filled.Description, extended.emeraldGlow),
        QuickToolItem("mock_test", "Mock Test", "Timed practice exams", Icons.Filled.Timer, extended.amberWarning),
        QuickToolItem("doubt_solver", "Doubt Solver", "Snap & solve math", Icons.Filled.HelpOutline, extended.cyanGlow),
        QuickToolItem("flashcards", "Flashcards", "3D active recall", Icons.Filled.Style, extended.electricBlue),
        QuickToolItem("planner", "Study Planner", "Timetable & exams", Icons.Filled.CalendarMonth, extended.violetGlow),
        QuickToolItem("library", "My Library", "Offline study vault", Icons.Filled.FolderOpen, extended.emeraldGlow)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.img_app_icon_fg),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Study Genius AI",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Welcome back, ${uiState.preferences.studentName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanguagePillButton(
                        language = uiState.preferences.language,
                        onClick = onOpenLanguagePicker
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onToggleTheme(!uiState.preferences.darkTheme) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("home_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.preferences.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.cd_theme_toggle),
                            tint = extended.emeraldGlow
                        )
                    }
                }
            }
        }

        // Hero Banner Card with Tagline & Primary CTA
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_hero_study_banner),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xE6070C18),
                                    Color(0xB80A152C),
                                    Color(0x55070C18)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = extended.emeraldGlow.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, extended.emeraldGlow.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = extended.emeraldGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GEMINI MULTIMODAL ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                    Text(
                        text = "Learn Smarter.\nPractice Better. Achieve More.",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Upload PDFs, DOCX, notes, or photos to generate instant quizzes, summaries, and flashcards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GradientActionButton(
                            text = "PDF to Quiz",
                            icon = Icons.Filled.Quiz,
                            onClick = { onNavigateTool("pdf_quiz") },
                            modifier = Modifier.weight(1f),
                            testTag = "hero_pdf_quiz_button"
                        )
                        Surface(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onNavigateTool("chat") }
                                .testTag("hero_ai_chat_button"),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xCC111C33),
                            border = BorderStroke(1.dp, extended.cyanGlow.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = extended.cyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ask AI",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dual Telemetry Row (Weekly Goal Ring + 3D Study Companion Streak Card)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val goalPct = (uiState.weeklyGoalProgress * 100).toInt()
                val studiedHrs = uiState.preferences.studiedMinutesThisWeek / 60f
                val goalHrs = uiState.preferences.weeklyGoalMinutes / 60f
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTool("planner") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = extended.emeraldGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Weekly Goal",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedGoalRing(
                            progress = uiState.weeklyGoalProgress.coerceAtLeast(0.06f),
                            centerLabel = "$goalPct%"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = String.format("Studied: %.1fh / %.1fh", studiedHrs, goalHrs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTool("chat") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = extended.amberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Study Energy",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_study_companion),
                            contentDescription = stringResource(R.string.cd_study_companion),
                            modifier = Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.preferences.streakDays} Day Streak",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = extended.emeraldGlow,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ((uiState.preferences.streakDays % 7).coerceAtLeast(1)) / 7f },
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

        // Core Learning Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricMiniCard(
                    value = "${uiState.totalQuizzesCompleted}",
                    label = "Quizzes",
                    accent = extended.electricBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    value = "${uiState.averageScore}%",
                    label = "Avg Score",
                    accent = extended.emeraldGlow,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    value = "${uiState.totalNotesSaved + uiState.totalFlashcardDecks}",
                    label = "Saved Items",
                    accent = extended.cyanGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Daily Motivation Carousel
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { quoteIndex = if (quoteIndex == 0) quotes.lastIndex else quoteIndex - 1 },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous quote",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DAILY MOTIVATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = extended.cyanGlow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quotes[quoteIndex],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3
                        )
                    }
                    IconButton(
                        onClick = { quoteIndex = (quoteIndex + 1) % quotes.size },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next quote",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Access 10 Feature Cards Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Study Suite",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "All 10 tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = extended.emeraldGlow
                )
            }
        }

        // 2-Column Feature Grid (All 10 Modules)
        items(quickTools.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { tool ->
                    QuickToolCard(
                        tool = tool,
                        onClick = { onNavigateTool(tool.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent Activity Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.recentActivities.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigateTool("pdf_quiz") }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(extended.emeraldGlow.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = extended.emeraldGlow
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start your first study session",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generate a quiz from any PDF, image, or ask the AI Study Assistant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = extended.cyanGlow
                        )
                    }
                }
            }
        } else {
            items(uiState.recentActivities, key = { it.id }) { item ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (item.quizId != null) {
                            onOpenQuiz(item.quizId)
                        } else {
                            onNavigateTool("library")
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = extended.emeraldGlow.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, extended.emeraldGlow.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = item.badge,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = extended.emeraldGlow,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MetricMiniCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickToolCard(
    tool: QuickToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.testTag("tool_card_${tool.id}"),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tool.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = tool.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tool.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
