@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import kotlinx.coroutines.launch
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AcademicScreen(vm: MainViewModel) {
    val semesters by vm.academicSemesters.collectAsState(initial = emptyList())
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    var showSemester by remember { mutableStateOf(false) }
    val selected = semesters.firstOrNull { it.id == selectedId } ?: semesters.firstOrNull()
    LaunchedEffect(semesters) { if (selectedId < 0 && semesters.isNotEmpty()) selectedId = semesters.first().id }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Academic", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("A clear view of your semester routine", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSemester = true }) { Icon(Icons.Default.Add, "Add semester") }
        }
        if (semesters.isEmpty()) {
            EmptyState("No semester yet. Create one to start your academic routine.")
            Button(onClick = { showSemester = true }, Modifier.fillMaxWidth()) { Text("Create semester") }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                semesters.take(3).forEachIndexed { index, semester ->
                    SegmentedButton(semester.id == selected?.id, { selectedId = semester.id },
                        SegmentedButtonDefaults.itemShape(index, semesters.take(3).size)) { Text(semester.name, maxLines = 1) }
                }
            }
            selected?.let { AcademicSemesterContent(vm, it) }
        }
    }
    if (showSemester) AcademicSemesterDialog(vm) { showSemester = false }
}

@Composable
private fun AcademicSemesterContent(vm: MainViewModel, semester: AcademicSemesterEntity) {
    val classes by remember(semester.id) { vm.academicClasses(semester.id) }.collectAsState(initial = emptyList())
    val exams by remember(semester.id) { vm.academicExams(semester.id) }.collectAsState(initial = emptyList())
    val events by remember(semester.id) { vm.academicEvents(semester.id) }.collectAsState(initial = emptyList())
    var mode by rememberSaveable(semester.id) { mutableStateOf("TODAY") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<AcademicClassEntity?>(null) }
    var details by remember { mutableStateOf<AcademicClassEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showExam by remember { mutableStateOf(false) }
    var showEvent by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<List<AcademicClassEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    val todayCalendar = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis } }
    val todayName = remember(nowMillis) { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(nowMillis)) }
    val todayDate = remember(nowMillis) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(nowMillis)) }

    Text("${semester.startDate} - ${semester.endDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("TODAY", "ROUTINE").forEachIndexed { index, label ->
            SegmentedButton(mode == label, { mode = label }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
        }
    }
    if (mode == "TODAY") {
        Text("TODAY · ${todayName.uppercase()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(todayDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("TODAY'S CLASSES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val todayClasses = sortAcademicClasses(classes.filter { it.dayOfWeek.equals(todayName, true) })
        if (todayClasses.isEmpty()) {
            Text("No classes today 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            todayClasses.forEach { item ->
                ClassList(
                    listOf(item),
                    status = academicRoutineItem(item).let { routineItemStatus(it, todayCalendar.timeInMillis, nowMillis) },
                    onClick = { details = item }
                )
            }
            FreePeriodList(calculateFreePeriods(todayClasses))
        }
    } else {
        routineDayNames.forEach { (_, day) ->
            val dayClasses = sortAcademicClasses(classes.filter { it.dayOfWeek.equals(day, true) })
            if (dayClasses.isNotEmpty()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Surface(
                        color = if (day.equals(todayName, true)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(day.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = if (day.equals(todayName, true)) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    }
                    if (day.equals(todayName, true)) {
                        Spacer(Modifier.width(8.dp))
                        Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                ClassList(dayClasses, null, onClick = { details = it })
                FreePeriodList(calculateFreePeriods(dayClasses))
            }
        }
        if (classes.isEmpty()) Text("No classes in this semester yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add class") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showExam = true }, Modifier.weight(1f)) { Text("Add exam") }
        OutlinedButton(onClick = { showEvent = true }, Modifier.weight(1f)) { Text("Add event") }
    }
    Text("${exams.size} exams · ${events.size} academic events", color = MaterialTheme.colorScheme.onSurfaceVariant)
    exams.forEach { exam ->
        ListCard {
            Column(Modifier.weight(1f)) {
                Text("${exam.courseCode} · ${exam.courseName}", fontWeight = FontWeight.Bold)
                Text("${exam.examType} · ${exam.examDate} · ${exam.startTime}")
                if (exam.room.isNotBlank()) Text("📍 ${exam.room}")
            }
            IconButton(onClick = { vm.deleteAcademicExam(exam) }) { Icon(Icons.Default.Delete, "Delete exam") }
        }
    }
    events.forEach { event ->
        ListCard {
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Text("${event.type} · ${event.date}${event.time.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
            }
            IconButton(onClick = { vm.deleteAcademicEvent(event) }) { Icon(Icons.Default.Delete, "Delete event") }
        }
    }

    if (showAdd) AcademicClassEditor(vm, semester.id, null, { showAdd = false }) { item ->
        vm.addAcademicClass(item, onConflict = { conflict = it }, onSaved = { showAdd = false })
    }
    editing?.let { item -> AcademicClassEditor(vm, semester.id, item, { editing = null }) { updated ->
        vm.updateAcademicClass(updated) { conflict = it }; editing = null
    } }
    details?.let { item ->
        ClassDetails(item, onDismiss = { details = null }, onEdit = { details = null; editing = item },
            onDelete = { vm.deleteAcademicClass(item); details = null })
    }
    if (conflict.isNotEmpty()) {
        AlertDialog(onDismissRequest = { conflict = emptyList() }, title = { Text("Schedule conflict") },
            text = { Text("${conflict.first().courseCode} overlaps with ${conflict.first().courseName} on ${conflict.first().dayOfWeek}.") },
            confirmButton = { TextButton(onClick = { conflict = emptyList() }) { Text("Cancel") } })
    }
    if (showExam) AcademicExamDialog(vm, semester.id) { showExam = false }
    if (showEvent) AcademicEventDialog(vm, semester.id) { showEvent = false }
}

@Composable
private fun ClassList(items: List<AcademicClassEntity>, status: ScheduleState?, onClick: (AcademicClassEntity) -> Unit) {
    items.forEach { item ->
        ListCard(Modifier.clickable { onClick(item) }) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(item.courseCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    status?.let {
                        Text(
                            when (it) {
                                ScheduleState.CURRENT -> "NOW / LIVE"
                                ScheduleState.NEXT -> "UPCOMING"
                                ScheduleState.COMPLETED -> "COMPLETED"
                                ScheduleState.NONE -> "TIME UNAVAILABLE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (it) {
                                ScheduleState.CURRENT -> MaterialTheme.colorScheme.primary
                                ScheduleState.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(item.courseName)
                Text("${formatRoutineTime(item.startTime)} - ${formatRoutineTime(item.endTime)}")
                if (item.teacher.isNotBlank()) Text("Teacher: ${item.teacher}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                locationText(item)?.let { Text("📍 $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Reminder: ${if (item.notificationsEnabled && item.reminderMinutes > 0) "${item.reminderMinutes} minutes before" else "None"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onClick(item) }) { Icon(Icons.Default.Edit, "Open class details") }
        }
    }
}

@Composable private fun FreePeriodList(periods: List<FreePeriod>) {
    periods.forEach { period ->
        Text("FREE · ${period.durationMinutes / 60}h ${period.durationMinutes % 60}m",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

private fun locationText(item: AcademicClassEntity): String? =
    listOf(item.room.trim(), item.building.trim()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

private fun sortAcademicClasses(items: List<AcademicClassEntity>): List<AcademicClassEntity> =
    sortRoutineItems(items.map { item ->
        RoutineItem(item.id, item.courseCode, item.courseName, item.teacher, item.room, item.building,
            item.dayOfWeek, item.startTime, item.endTime, item.kind, true, academicClass = item)
    }).mapNotNull { it.academicClass }

private fun academicRoutineItem(item: AcademicClassEntity) =
    RoutineItem(item.id, item.courseCode, item.courseName, item.teacher, item.room, item.building,
        item.dayOfWeek, item.startTime, item.endTime, item.kind, true, academicClass = item)

@Composable
private fun ClassDetails(item: AcademicClassEntity, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Class Details") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${item.courseCode} · ${item.courseName}", fontWeight = FontWeight.Bold)
            listOf("Teacher" to item.teacher, "Day" to item.dayOfWeek, "Start time" to item.startTime, "End time" to item.endTime,
                "Room" to item.room, "Building" to item.building, "Kind" to item.kind,
                "Reminder" to if (item.notificationsEnabled && item.reminderMinutes > 0) "${item.reminderMinutes} minutes" else "None",
                "Notes" to item.notes).filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value") }
        }
    }, confirmButton = { TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Text(" Edit") } },
        dismissButton = { TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null); Text(" Delete") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete class?") },
        text = { Text("This removes ${item.courseCode} from the semester routine.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun AcademicClassEditor(vm: MainViewModel, semesterId: Long, existing: AcademicClassEntity?, dismiss: () -> Unit,
                                save: (AcademicClassEntity) -> Unit) {
    val initial = existing ?: AcademicClassEntity(semesterId = semesterId, courseCode = "", courseName = "", dayOfWeek = todayDay(), startTime = "09:00 AM", endTime = "10:00 AM")
    var code by rememberSaveable(existing?.id) { mutableStateOf(initial.courseCode) }
    var name by rememberSaveable(existing?.id) { mutableStateOf(initial.courseName) }
    var teacher by rememberSaveable(existing?.id) { mutableStateOf(initial.teacher) }
    var day by rememberSaveable(existing?.id) { mutableStateOf(initial.dayOfWeek) }
    var start by rememberSaveable(existing?.id) { mutableStateOf(initial.startTime) }
    var end by rememberSaveable(existing?.id) { mutableStateOf(initial.endTime) }
    var room by rememberSaveable(existing?.id) { mutableStateOf(initial.room) }
    var building by rememberSaveable(existing?.id) { mutableStateOf(initial.building) }
    var kind by rememberSaveable(existing?.id) { mutableStateOf(initial.kind) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(initial.notes) }
    var reminder by rememberSaveable(existing?.id) { mutableIntStateOf(initial.reminderMinutes.coerceAtLeast(0)) }
    var enabled by rememberSaveable(existing?.id) { mutableStateOf(initial.notificationsEnabled) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val reminderScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val fieldRequesters = remember { List(10) { BringIntoViewRequester() } }
    val fields = listOf("Course code" to code, "Course name" to name, "Teacher" to teacher, "Day" to day, "Start time" to start,
        "End time" to end, "Room" to room, "Building" to building, "Kind" to kind, "Notes" to notes)
    Dialog(onDismissRequest = dismiss) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(scrollState).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (existing == null) "Add class" else "Edit class", style = MaterialTheme.typography.headlineSmall)
                fields.forEachIndexed { index, (label, value) ->
                    OutlinedTextField(value, { next ->
                        when (index) { 0 -> code = next; 1 -> name = next; 2 -> teacher = next; 3 -> day = next; 4 -> start = next
                            5 -> end = next; 6 -> room = next; 7 -> building = next; 8 -> kind = next; 9 -> notes = next }
                        validationError = null
                    }, label = { Text(label) }, singleLine = true, modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(fieldRequesters[index])
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { fieldRequesters[index].bringIntoView() }
                            }
                        })
                }
                Text("Reminder", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(reminderScrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0, 5, 10, 15, 30, 60).forEach { option ->
                        FilterChip(reminder == option, { reminder = option }, label = { Text(if (option == 0) "None" else if (option == 60) "1h" else "${option}m") })
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Notifications enabled"); Switch(enabled, { enabled = it })
                }
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val error = when {
                            code.isBlank() -> "Enter a course code."
                            name.isBlank() -> "Enter a course name."
                            parseRoutineTime(start) == null -> "Enter a valid start time."
                            parseRoutineTime(end) == null -> "Enter a valid end time."
                            else -> null
                        }
                        validationError = error
                        if (error == null) {
                            save(initial.copy(courseCode = code.trim(), courseName = name.trim(), teacher = teacher.trim(), dayOfWeek = day.trim(),
                                startTime = start.trim(), endTime = end.trim(), room = room.trim(), building = building.trim(), kind = kind.trim(),
                                notes = notes.trim(), reminderMinutes = reminder, notificationsEnabled = enabled))
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable private fun AcademicSemesterDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("New semester") }, text = {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.addSemester(name, todayString(), todayString()); dismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
private fun todayDay() = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())

@Composable
private fun AcademicExamDialog(vm: MainViewModel, semesterId: Long, dismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayString()) }
    var time by rememberSaveable { mutableStateOf("10:00 AM") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add exam") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Course code" to code, "Course name" to name, "Date (YYYY-MM-DD)" to date, "Start time" to time).forEachIndexed { index, (label, value) ->
                OutlinedTextField(value, { next -> when (index) { 0 -> code = next; 1 -> name = next; 2 -> date = next; else -> time = next } },
                    label = { Text(label) }, singleLine = true)
            }
        }
    }, confirmButton = { TextButton(enabled = code.isNotBlank() && name.isNotBlank(), onClick = {
        vm.addAcademicExam(AcademicExamEntity(semesterId = semesterId, courseCode = code.trim(), courseName = name.trim(), examDate = date.trim(), startTime = time.trim())); dismiss()
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AcademicEventDialog(vm: MainViewModel, semesterId: Long, dismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayString()) }
    var time by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add academic event") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Time (optional)") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
        vm.addAcademicEvent(AcademicEventEntity(semesterId = semesterId, title = title.trim(), date = date.trim(), time = time.trim())); dismiss()
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
