package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun studyTodayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

@Composable
fun StudyManagementScreen(vm: MainViewModel) {
    val courses by vm.studyCourses.collectAsState(initial = emptyList())
    val exams by vm.academicExams.collectAsState(initial = emptyList())
    val events by vm.academicDaoEvents.collectAsState(initial = emptyList())
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val goal by vm.todayGoal.collectAsState(initial = null)
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var generatedQuiz by remember { mutableStateOf(false) }
    val selected = courses.firstOrNull { it.id == selectedId } ?: courses.firstOrNull()
    LaunchedEffect(courses) { if (selectedId < 0 && courses.isNotEmpty()) selectedId = courses.first().id }
    val topics by selected?.let { vm.studyTopics(it.id) }?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val notes by selected?.let { vm.studyNotes(it.id) }?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val flashcards by selected?.let { vm.studyFlashcards(it.id) }?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val progress = if (topics.isEmpty()) 0 else topics.count { it.isCompleted } * 100 / topics.size

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Study Management", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Courses, plans, revision and learning materials", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { dialog = "course" }) { Icon(Icons.Default.Add, "Add course") }
        }
        if (courses.isEmpty()) {
            EmptyState("Create a course to organize topics, sessions, notes and revision.")
            Button(onClick = { dialog = "course" }, modifier = Modifier.fillMaxWidth()) { Text("Add your first course") }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                courses.forEach { course ->
                    FilterChip(
                        selected = course.id == selected?.id,
                        onClick = { selectedId = course.id },
                        label = { Text(course.code) }
                    )
                }
            }
            selected?.let { course ->
                Text("${course.code} - ${course.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ScrollableTabRow(selectedTabIndex = listOf("Overview", "Plan", "Notes", "Flashcards", "Quiz").indexOf(tab)) {
                    listOf("Overview", "Plan", "Notes", "Flashcards", "Quiz").forEach { name ->
                        Tab(selected = tab == name, onClick = { tab = name }, text = { Text(name) })
                    }
                }
                when (tab) {
                    "Overview" -> {
                        MetricCard("Course progress", Modifier.fillMaxWidth()) {
                            Text("$progress%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth())
                            Text("${topics.count { it.isCompleted }} of ${topics.size} topics complete")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Today's study", Modifier.weight(1f)) {                             Text("${sessions.filter { it.dateString == studyTodayString() }.sumOf { it.durationMinutes }} min") }
                            MetricCard("Daily goal", Modifier.weight(1f)) { Text(goal?.let { "${it.targetHours} h" } ?: "Not set") }
                        }
                        Text("Upcoming deadlines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        exams.filter { it.courseCode == course.code }.take(3).forEach {
                            ListCard { Column { Text("${it.examType} · ${it.examDate}", fontWeight = FontWeight.Bold); Text("Exam · preparation ${it.preparationProgress}%") } }
                        }
                        events.filter { it.type == "Assignment" && it.date >= studyTodayString() }.take(3).forEach {
                            ListCard { Column { Text(it.title, fontWeight = FontWeight.Bold); Text("Assignment deadline · ${it.date}") } }
                        }
                    }
                    "Plan" -> {
                        Text("Check off each topic as you finish revising it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Revision schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (topics.isEmpty()) EmptyState("Add topics to generate a personalized plan.")
                        topics.forEachIndexed { index, topic ->
                            ListCard {
                                Checkbox(checked = topic.isCompleted, onCheckedChange = { vm.toggleStudyTopic(topic) })
                                Text("${index + 1}. ${topic.title}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(if (topic.isCompleted) "Done" else "Next", color = if (topic.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    "Notes" -> {
                        ActionButton("Add note / PDF reference", Icons.AutoMirrored.Filled.NoteAdd, { dialog = "note" }, Modifier.fillMaxWidth())
                        if (notes.isEmpty()) EmptyState("Keep lecture notes and PDF references with this course.")
                        notes.forEach { note ->
                            ListCard { Column { Text(note.title, fontWeight = FontWeight.Bold); Text(note.content, maxLines = 3); if (note.pdfUri.isNotBlank()) Text("PDF: ${note.pdfUri}", color = MaterialTheme.colorScheme.primary) } }
                        }
                    }
                    "Flashcards" -> {
                        ActionButton("Add flashcard", Icons.Default.Style, { dialog = "flashcard" }, Modifier.fillMaxWidth())
                        if (flashcards.isEmpty()) EmptyState("Create flashcards for spaced revision.")
                        flashcards.forEach { card ->
                            var revealed by remember(card.id) { mutableStateOf(false) }
                            ListCard {
                                Column(Modifier.weight(1f)) { Text(card.front, fontWeight = FontWeight.Bold); if (revealed) Text(card.back) }
                                TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "Hide" else "Reveal") }
                                IconButton(onClick = { vm.toggleFlashcard(card) }) { Icon(if (card.isMastered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Mark mastered") }
                            }
                        }
                    }
                    "Quiz" -> {
                        Button(onClick = { generatedQuiz = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Quiz, null); Spacer(Modifier.width(8.dp)); Text("Build practice prompts") }
                        if (generatedQuiz) {
                            Text("Self-check prompts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            topics.take(5).forEachIndexed { index, topic ->
                                ListCard { Column { Text("${index + 1}. Explain ${topic.title}.", fontWeight = FontWeight.Bold); Text("Write a short answer, then review it against your notes.") } }
                            }
                            if (topics.isEmpty()) EmptyState("Add topics before generating a quiz.")
                        }
                    }
                }
                if (tab == "Plan") {
                    Button(onClick = { dialog = "topic" }, modifier = Modifier.fillMaxWidth()) { Text("Add topic") }
                }
            }
        }
    }
    when (dialog) {
        "course" -> StudyFormDialog("New course", listOf("Course code", "Course name", "Exam date (YYYY-MM-DD)"), { values -> vm.addStudyCourse(values[0], values[1], values[2]); dialog = null }, { dialog = null })
        "topic" -> selected?.let { course -> StudyFormDialog("New topic", listOf("Topic name"), { values -> vm.addStudyTopic(course.id, values[0]); dialog = null }, { dialog = null }) }
        "note" -> selected?.let { course -> StudyFormDialog("New note / PDF reference", listOf("Title", "Notes or PDF URI"), { values -> vm.addStudyNote(course.id, values[0], values[1]); dialog = null }, { dialog = null }) }
        "flashcard" -> selected?.let { course -> StudyFormDialog("New flashcard", listOf("Question", "Answer"), { values -> vm.addStudyFlashcard(course.id, values[0], values[1]); dialog = null }, { dialog = null }) }
    }
}

@Composable
private fun StudyFormDialog(title: String, labels: List<String>, save: (List<String>) -> Unit, dismiss: () -> Unit) {
    var values by remember { mutableStateOf(labels.map { "" }) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                OutlinedTextField(values[index], { value -> values = values.toMutableList().also { it[index] = value } },
                    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }, confirmButton = { TextButton(onClick = { save(values) }, enabled = values.firstOrNull()?.isNotBlank() == true) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
