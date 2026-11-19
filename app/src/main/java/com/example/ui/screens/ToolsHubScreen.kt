package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.StudyLanguage
import com.example.ui.components.GlassCard
import com.example.ui.components.LanguagePillButton
import com.example.ui.components.StudyTopBar
import com.example.ui.theme.LocalStudyColors

private data class ToolDirectoryEntry(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun ToolsHubScreen(
    language: StudyLanguage,
    onOpenLanguagePicker: () -> Unit,
    onNavigateTool: (String) -> Unit
) {
    val extended = LocalStudyColors.current
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Quiz & Exams", "Notes & Docs", "AI Tutors", "Plan & Vault")

    val allTools = listOf(
        ToolDirectoryEntry(
            id = "pdf_quiz",
            title = "PDF & Document to Quiz",
            description = "Upload PDF, DOCX, TXT, or notes to generate grounded MCQs, True/False & Short Answers",
            category = "Quiz & Exams",
            icon = Icons.Filled.Quiz,
            color = extended.electricBlue
        ),
        ToolDirectoryEntry(
            id = "image_quiz",
            title = "Image to Quiz",
            description = "Capture textbook pages or diagrams with camera/gallery and build instant quizzes",
            category = "Quiz & Exams",
            icon = Icons.Filled.CameraAlt,
            color = extended.cyanGlow
        ),
        ToolDirectoryEntry(
            id = "mock_test",
            title = "Mock Test Generator",
            description = "Configure custom timed exams with subject, topic, difficulty, and automatic grading",
            category = "Quiz & Exams",
            icon = Icons.Filled.Timer,
            color = extended.amberWarning
        ),
        ToolDirectoryEntry(
            id = "chat",
            title = "AI Study Assistant",
            description = "Step-by-step explanations, math solving, examples & multilingual conversation history",
            category = "AI Tutors",
            icon = Icons.AutoMirrored.Filled.Chat,
            color = extended.emeraldGlow
        ),
        ToolDirectoryEntry(
            id = "doubt_solver",
            title = "AI Doubt Solver",
            description = "Snap a tough problem or ask a concept question for instant clear walkthroughs",
            category = "AI Tutors",
            icon = Icons.Filled.HelpOutline,
            color = extended.cyanGlow
        ),
        ToolDirectoryEntry(
            id = "notes",
            title = "AI Notes Generator",
            description = "Create short notes, detailed notes, formula sheets, definitions & exam revision sheets",
            category = "Notes & Docs",
            icon = Icons.Filled.EditNote,
            color = extended.violetGlow
        ),
        ToolDirectoryEntry(
            id = "summarizer",
            title = "AI PDF Summarizer",
            description = "Extract chapter summaries, key concepts, definitions, formulas & section references",
            category = "Notes & Docs",
            icon = Icons.Filled.Description,
            color = extended.emeraldGlow
        ),
        ToolDirectoryEntry(
            id = "flashcards",
            title = "Flashcards",
            description = "AI-generated Q&A decks with 3D flip animations, mastery tracking & revision mode",
            category = "Plan & Vault",
            icon = Icons.Filled.Style,
            color = extended.electricBlue
        ),
        ToolDirectoryEntry(
            id = "planner",
            title = "Study Planner",
            description = "Daily timetable, weekly goals, reminders, checkable tasks & exam countdowns",
            category = "Plan & Vault",
            icon = Icons.Filled.CalendarMonth,
            color = extended.violetGlow
        ),
        ToolDirectoryEntry(
            id = "library",
            title = "My Library",
            description = "Search, filter, rename, replay & export all offline quizzes, notes, cards & docs",
            category = "Plan & Vault",
            icon = Icons.Filled.FolderOpen,
            color = extended.emeraldGlow
        )
    )

    val filtered = remember(selectedCategory, allTools) {
        if (selectedCategory == "All") allTools else allTools.filter { it.category == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudyTopBar(
            title = "AI Study Tools",
            subtitle = "10 intelligent learning workflows",
            actions = {
                LanguagePillButton(language = language, onClick = onOpenLanguagePicker)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val active = cat == selectedCategory
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = cat }
                        .testTag("tools_filter_$cat"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) extended.emeraldGlow else extended.glassSurface,
                    border = BorderStroke(
                        1.dp,
                        if (active) extended.emeraldGlow else extended.glassBorder
                    )
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (active) Color(0xFF06101F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { tool ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_tool_${tool.id}"),
                    onClick = { onNavigateTool(tool.id) },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(tool.color.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = tool.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open ${tool.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
