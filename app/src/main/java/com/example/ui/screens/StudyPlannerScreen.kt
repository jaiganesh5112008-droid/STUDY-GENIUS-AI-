package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AnimatedGoalRing
import com.example.ui.components.GlassCard
import com.example.ui.components.StudyEmptyState
import com.example.ui.components.StudyTopBar
import com.example.ui.theme.LocalStudyColors
import com.example.viewmodel.DashboardUiState
import com.example.viewmodel.PlannerLibraryViewModel
import kotlin.math.roundToInt

@Composable
fun StudyPlannerScreen(
    plannerViewModel: PlannerLibraryViewModel,
    dashboardState: DashboardUiState,
    onUpdateWeeklyGoal: (Int) -> Unit,
    onBack: () -> Unit
) {
    val extended = LocalStudyColors.current
    val tasks by plannerViewModel.plannerTasks.collectAsStateWithLifecycle()
    val countdowns by plannerViewModel.examCountdowns.collectAsStateWithLifecycle()

    var selectedDay by remember { mutableStateOf("All") }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val days = listOf("All", "Today", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val filteredTasks = remember(tasks, selectedDay) {
        if (selectedDay == "All") tasks else tasks.filter { it.dayLabel.equals(selectedDay, ignoreCase = true) }
    }
    val todayEpochDay = remember { System.currentTimeMillis() / 86_400_000L }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = extended.emeraldGlow,
                contentColor = Color(0xFF06101F),
                modifier = Modifier.testTag("planner_add_task_fab")
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
                title = "Study Planner",
                subtitle = "Timetable, goals, reminders & exam countdowns",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Weekly Goal Card
                item {
                    val goalPct = (dashboardState.weeklyGoalProgress * 100).toInt()
                    val doneTasks = tasks.count { it.isCompleted }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showGoalDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AnimatedGoalRing(
                                progress = dashboardState.weeklyGoalProgress.coerceAtLeast(0.05f),
                                centerLabel = "$goalPct%",
                                size = 76.dp,
                                strokeWidth = 8.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Weekly Study Goal",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dashboardState.preferences.studiedMinutesThisWeek} / ${dashboardState.preferences.weeklyGoalMinutes} mins studied",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extended.emeraldGlow
                                )
                                Text(
                                    text = "$doneTasks of ${tasks.size} tasks done • Tap to adjust target",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Exam Countdown Header & Carousel
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exam Countdowns",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        OutlinedButton(
                            onClick = { showAddExamDialog = true },
                            modifier = Modifier.testTag("planner_add_exam_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Exam")
                        }
                    }
                }

                if (countdowns.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAddExamDialog = true }
                        ) {
                            Text(
                                text = "No exams scheduled yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap 'Add Exam' to track days remaining to your finals, board exams, or entrance tests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(countdowns, key = { it.id }) { exam ->
                                val daysLeft = (exam.targetEpochDay - todayEpochDay).coerceAtLeast(0)
                                GlassCard(
                                    modifier = Modifier.width(190.dp),
                                    borderColor = if (daysLeft <= 7) extended.amberWarning else extended.cyanGlow.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "$daysLeft d",
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (daysLeft <= 7) extended.amberWarning else extended.emeraldGlow
                                        )
                                        IconButton(
                                            onClick = { plannerViewModel.deleteExamCountdown(exam.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.cd_delete_item),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = exam.examName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = exam.subject,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Timetable & Tasks Section
                item {
                    Text(
                        text = "Daily Timetable & Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        days.forEach { d ->
                            FilterChip(
                                selected = selectedDay == d,
                                onClick = { selectedDay = d },
                                label = { Text(d) }
                            )
                        }
                    }
                }

                if (filteredTasks.isEmpty()) {
                    item {
                        StudyEmptyState(
                            icon = Icons.Filled.CalendarMonth,
                            title = "Your timetable is open",
                            subtitle = "Add study blocks with subject, time slot, and reminder to stay consistent every day.",
                            actionLabel = "Add Study Block",
                            onAction = { showAddTaskDialog = true }
                        )
                    }
                } else {
                    items(filteredTasks, key = { it.id }) { task ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (task.isCompleted) extended.emeraldGlow.copy(alpha = 0.5f) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { plannerViewModel.toggleTaskCompleted(task) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${task.dayLabel} • ${task.subject}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extended.cyanGlow
                                        )
                                        Icon(
                                            Icons.Filled.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = task.timeSlot,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (task.reminderLabel.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.NotificationsActive,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = extended.amberWarning
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Reminder: ${task.reminderLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = extended.amberWarning
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { plannerViewModel.deleteTask(task.id) }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_delete_item),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onAdd = { title, subject, day, slot, reminder, duration ->
                plannerViewModel.addTask(title, subject, day, slot, reminder, duration)
                showAddTaskDialog = false
            }
        )
    }

    if (showAddExamDialog) {
        AddExamDialog(
            onDismiss = { showAddExamDialog = false },
            onAdd = { name, subject, daysOut ->
                plannerViewModel.addExamCountdown(name, subject, daysOut)
                showAddExamDialog = false
            }
        )
    }

    if (showGoalDialog) {
        var goalMinutes by remember { mutableIntStateOf(dashboardState.preferences.weeklyGoalMinutes) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Weekly Study Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${goalMinutes / 60} hrs ${goalMinutes % 60} mins per week",
                        style = MaterialTheme.typography.titleMedium,
                        color = extended.emeraldGlow
                    )
                    Slider(
                        value = goalMinutes.toFloat(),
                        onValueChange = { goalMinutes = (it / 30f).roundToInt() * 30 },
                        valueRange = 60f..900f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateWeeklyGoal(goalMinutes)
                    showGoalDialog = false
                }) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Mathematics") }
    var day by remember { mutableStateOf("Today") }
    var timeSlot by remember { mutableStateOf("06:00 PM - 07:00 PM") }
    var reminder by remember { mutableStateOf("15 min before") }
    var duration by remember { mutableIntStateOf(45) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Timetable Block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Study Task") },
                    placeholder = { Text("e.g., Revise Thermodynamics Formulas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Today", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                        FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d) })
                    }
                }
                OutlinedTextField(
                    value = timeSlot,
                    onValueChange = { timeSlot = it },
                    label = { Text("Time Slot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("Study Reminder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Duration: $duration min", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = (it / 15f).roundToInt() * 15 },
                    valueRange = 15f..180f
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onAdd(title, subject, day, timeSlot, reminder, duration) }
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddExamDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("All Subjects") }
    var daysOut by remember { mutableIntStateOf(14) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exam Countdown") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exam Name") },
                    placeholder = { Text("e.g., Physics Midterm, NEET Mock") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Days until exam: $daysOut days", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = daysOut.toFloat(),
                    onValueChange = { daysOut = it.roundToInt() },
                    valueRange = 1f..120f
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onAdd(name, subject, daysOut) }
            ) {
                Text("Save Exam")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
