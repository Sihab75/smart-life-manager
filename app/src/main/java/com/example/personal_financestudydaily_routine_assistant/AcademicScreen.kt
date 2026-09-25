package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AcademicScreen(vm: MainViewModel) {
    val semesters by vm.academicSemesters.collectAsState(initial = emptyList())
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var showSemester by remember { mutableStateOf(false) }
    var showClass by remember { mutableStateOf(false) }
    var showExam by remember { mutableStateOf(false) }
    var showEvent by remember { mutableStateOf(false) }
    val selected = semesters.firstOrNull { it.id == selectedId } ?: semesters.firstOrNull()
    LaunchedEffect(semesters) { if (selectedId < 0 && semesters.isNotEmpty()) selectedId = semesters.first().id }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Semester Manager", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Classes, exams, deadlines and academic records", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSemester = true }) { Icon(Icons.Default.Add, "Add semester") }
        }
        if (semesters.isEmpty()) {
            EmptyState("Create your first semester to start managing academic life.")
            Button(onClick = { showSemester = true }, modifier = Modifier.fillMaxWidth()) { Text("Create semester") }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                semesters.take(3).forEachIndexed { index, semester ->
                    SegmentedButton(
                        selected = semester.id == selected?.id,
                        onClick = { selectedId = semester.id },
                        shape = SegmentedButtonDefaults.itemShape(index, semesters.take(3).size)
                    ) { Text(semester.name, maxLines = 1) }
                }
            }
            selected?.let { semester ->
                AcademicSemesterContent(vm, semester, onAddClass = { showClass = true }, onAddExam = { showExam = true }, onAddEvent = { showEvent = true })
                if (!semester.isArchived) {
                    OutlinedButton(onClick = { vm.archiveSemester(semester) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Archive, null); Spacer(Modifier.width(6.dp)); Text("Archive semester")
                    }
                }
            }
        }
    }
    if (showSemester) AcademicSemesterDialog(vm) { showSemester = false }
    if (showClass && selected != null) AcademicClassDialog(vm, selected.id) { showClass = false }
    if (showExam && selected != null) AcademicExamDialog(vm, selected.id) { showExam = false }
    if (showEvent && selected != null) AcademicEventDialog(vm, selected.id) { showEvent = false }
}

@Composable
private fun AcademicSemesterContent(vm: MainViewModel, semester: AcademicSemesterEntity, onAddClass: () -> Unit, onAddExam: () -> Unit, onAddEvent: () -> Unit) {
    val classes by remember(semester.id) { vm.academicClasses(semester.id) }.collectAsState(initial = emptyList())
    val exams by remember(semester.id) { vm.academicExams(semester.id) }.collectAsState(initial = emptyList())
    val events by remember(semester.id) { vm.academicEvents(semester.id) }.collectAsState(initial = emptyList())
    var section by rememberSaveable(semester.id) { mutableStateOf("Overview") }
    Text("${semester.startDate} - ${semester.endDate}${if (semester.isArchived) " · Archived" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    ScrollableTabRow(selectedTabIndex = listOf("Overview", "Routine", "Exams", "Calendar").indexOf(section)) {
        listOf("Overview", "Routine", "Exams", "Calendar").forEach { tab ->
            Tab(selected = section == tab, onClick = { section = tab }, text = { Text(tab) })
        }
    }
    when (section) {
        "Overview" -> {
            AcademicMetric("Today's classes", classes.count { it.dayOfWeek.equals(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), true) }.toString())
            AcademicMetric("Upcoming exams", exams.count { it.examDate >= todayString() }.toString())
            AcademicMetric("Academic events", events.size.toString())
            Text("Next exam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            exams.firstOrNull { it.examDate >= todayString() }?.let { ExamCard(it, {}, null) } ?: Text("No upcoming exams", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "Routine" -> {
            ActionButton("Add class", Icons.Default.Add, onAddClass, Modifier.fillMaxWidth())
            if (classes.isEmpty()) EmptyState("No classes in this semester")
            classes.forEach { item ->
                ListCard {
                    Column(Modifier.weight(1f)) {
                        Text("${item.courseCode} - ${item.courseName}", fontWeight = FontWeight.Bold)
                        Text("${item.dayOfWeek} · ${item.startTime} - ${item.endTime}")
                        Text(listOf(item.room, item.building, item.teacher).filter { it.isNotBlank() }.joinToString(" · "))
                        Text("${item.kind} · reminder ${item.reminderMinutes} min before", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    IconButton(onClick = { vm.deleteAcademicClass(item) }) { Icon(Icons.Default.Delete, "Delete class") }
                }
            }
        }
        "Exams" -> {
            ActionButton("Add exam", Icons.Default.Add, onAddExam, Modifier.fillMaxWidth())
            exams.forEach { item -> ExamCard(item, { vm.deleteAcademicExam(item) }, item.preparationProgress) }
        }
        "Calendar" -> {
            ActionButton("Add academic event", Icons.Default.Add, onAddEvent, Modifier.fillMaxWidth())
            events.forEach { item ->
                ListCard {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text("${item.type} · ${item.date}${item.time.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
                        if (item.location.isNotBlank()) Text(item.location)
                    }
                    IconButton(onClick = { vm.deleteAcademicEvent(item) }) { Icon(Icons.Default.Delete, "Delete event") }
                }
            }
        }
    }
}

@Composable private fun AcademicMetric(label: String, value: String) {
    MetricCard(label, Modifier.fillMaxWidth()) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
}

@Composable private fun ExamCard(item: AcademicExamEntity, onDelete: () -> Unit, progress: Int?) {
    ListCard {
        Column(Modifier.weight(1f)) {
            Text("${item.courseCode} - ${item.courseName}", fontWeight = FontWeight.Bold)
            Text("${item.examType} · ${item.examDate} · ${item.startTime}")
            Text(listOf(item.room.takeIf { it.isNotBlank() }?.let { "Room $it" }, item.seatNumber.takeIf { it.isNotBlank() }?.let { "Seat $it" }).filterNotNull().joinToString(" · "))
            Text("Exam in ${daysUntil(item.examDate)} days", color = MaterialTheme.colorScheme.primary)
            if (progress != null) { LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth()); Text("Preparation $progress%", fontSize = MaterialTheme.typography.bodySmall.fontSize) }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete exam") }
    }
}

private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
private fun daysUntil(date: String) = ((runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time }.getOrNull() ?: System.currentTimeMillis()) - System.currentTimeMillis()).let { (it / 86_400_000L).toInt().coerceAtLeast(0) }

@Composable private fun AcademicSemesterDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var start by rememberSaveable { mutableStateOf(todayString()) }; var end by rememberSaveable { mutableStateOf(todayString()) }
    SimpleFormDialog("New semester", dismiss, listOf("Name", "Start date (YYYY-MM-DD)", "End date (YYYY-MM-DD)")) { values -> vm.addSemester(values[0], values[1], values[2]); dismiss() }
}

@Composable private fun AcademicClassDialog(vm: MainViewModel, semesterId: Long, dismiss: () -> Unit) {
    var values by rememberSaveable { mutableStateOf(listOf("", "", "", "Sunday", "09:00 AM", "10:30 AM", "", "", "30")) }
    SimpleFormDialog("Add class", dismiss, listOf("Course code", "Course name", "Teacher", "Day", "Start time", "End time", "Room", "Building", "Reminder minutes")) { values ->
        vm.addAcademicClass(AcademicClassEntity(semesterId = semesterId, courseCode = values[0], courseName = values[1], teacher = values[2], dayOfWeek = values[3], startTime = values[4], endTime = values[5], room = values[6], building = values[7], reminderMinutes = values[8].toIntOrNull() ?: 30)); dismiss()
    }
}

@Composable private fun AcademicExamDialog(vm: MainViewModel, semesterId: Long, dismiss: () -> Unit) {
    var values by rememberSaveable { mutableStateOf(listOf("", "", "Final", todayString(), "10:00 AM", "", "", "", "0")) }
    SimpleFormDialog("Add exam", dismiss, listOf("Course code", "Course name", "Exam type", "Date (YYYY-MM-DD)", "Start time", "End time", "Room", "Seat number", "Preparation %")) { values ->
        vm.addAcademicExam(AcademicExamEntity(semesterId = semesterId, courseCode = values[0], courseName = values[1], examType = values[2], examDate = values[3], startTime = values[4], endTime = values[5], room = values[6], seatNumber = values[7], preparationProgress = values[8].toIntOrNull()?.coerceIn(0, 100) ?: 0)); dismiss()
    }
}

@Composable private fun AcademicEventDialog(vm: MainViewModel, semesterId: Long, dismiss: () -> Unit) {
    var values by rememberSaveable { mutableStateOf(listOf("", "Assignment", todayString(), "", "")) }
    SimpleFormDialog("Add academic event", dismiss, listOf("Title", "Type", "Date (YYYY-MM-DD)", "Time", "Location")) { values ->
        vm.addAcademicEvent(AcademicEventEntity(semesterId = semesterId, title = values[0], type = values[1], date = values[2], time = values[3], location = values[4])); dismiss()
    }
}

@Composable private fun SimpleFormDialog(title: String, dismiss: () -> Unit, labels: List<String>, save: (List<String>) -> Unit) {
    var values by remember { mutableStateOf(labels.map { "" }) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEachIndexed { index, label -> OutlinedTextField(values[index], { next -> values = values.toMutableList().also { it[index] = next } }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
    }, confirmButton = { TextButton(onClick = { save(values) }, enabled = values.firstOrNull()?.isNotBlank() == true) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
