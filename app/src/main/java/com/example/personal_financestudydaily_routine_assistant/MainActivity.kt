package com.example.personal_financestudydaily_routine_assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.example.personal_financestudydaily_routine_assistant.ai.AssistantContext
import com.example.personal_financestudydaily_routine_assistant.ai.LocalAiAssistant
import com.example.personal_financestudydaily_routine_assistant.ai.OnlineAiAssistant
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import com.example.personal_financestudydaily_routine_assistant.domain.calculator.ProductivityCalculator
import com.example.personal_financestudydaily_routine_assistant.ui.theme.Personal_FinanceStudyDaily_Routine_AssistantTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Personal_FinanceStudyDaily_Routine_AssistantTheme {
                SmartLifeManagerApp()
            }
        }
    }
}

private enum class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home), EXPENSES("Expenses", Icons.Default.AccountBalanceWallet),
    STUDY("Study", Icons.Default.MenuBook), TASKS("Tasks", Icons.Default.CheckCircle),
    SCHEDULE("Schedule", Icons.Default.CalendarMonth), REPORTS("Reports", Icons.Default.BarChart),
    ASSISTANT("AI", Icons.Default.AutoAwesome)
}

@Composable
private fun SmartLifeManagerApp(vm: MainViewModel = viewModel()) {
    var selected by rememberSaveable { mutableStateOf(Destination.HOME) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected == Destination.EXPENSES || selected == Destination.TASKS || selected == Destination.SCHEDULE) {
                FloatingActionButton(onClick = {
                    when (selected) {
                        Destination.EXPENSES -> showExpenseDialog = true
                        Destination.TASKS -> showTaskDialog = true
                        Destination.SCHEDULE -> showClassDialog = true
                        else -> Unit
                    }
                }) {
                    Icon(Icons.Default.Add, when (selected) {
                        Destination.EXPENSES -> "Add expense"
                        Destination.TASKS -> "Add task"
                        else -> "Add class"
                    })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Destination.HOME -> Dashboard(vm, onAddExpense = { showExpenseDialog = true }, onStudy = { selected = Destination.STUDY })
                Destination.EXPENSES -> ExpensesScreen(vm)
                Destination.STUDY -> StudyScreen(vm)
                Destination.TASKS -> TasksScreen(vm)
                Destination.SCHEDULE -> ScheduleScreen(vm)
                Destination.REPORTS -> ReportsScreen(vm)
                Destination.ASSISTANT -> AssistantScreen(vm)
            }
        }
    }
    if (showExpenseDialog) AddExpenseDialog(vm) { showExpenseDialog = false }
    if (showTaskDialog) AddTaskDialog(vm) { showTaskDialog = false }
    if (showClassDialog) AddClassDialog(vm) { showClassDialog = false }
}

@Composable
private fun Dashboard(vm: MainViewModel, onAddExpense: () -> Unit, onStudy: () -> Unit) {
    val settings by vm.settings.collectAsState(initial = null)
    val todayExpense by vm.todayExpenseTotal.collectAsState(initial = 0.0)
    val monthExpense by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    val studyMinutes by vm.todayStudyMinutes.collectAsState(initial = 0)
    val goal by vm.todayGoal.collectAsState(initial = null)
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val routines by vm.todayRoutines.collectAsState(initial = emptyList())
    val classes by vm.classes.collectAsState(initial = emptyList())
    val completedTasks = tasks.count { it.isCompleted }
    val productivity = ProductivityCalculator.calculate(
        goal?.targetHours?.times(60)?.toInt() ?: 240, studyMinutes ?: 0,
        tasks.size, completedTasks, routines.size, routines.count { it.isCompleted }
    )
    val nextClass = classes.firstOrNull()
    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.smart_life_manager_logo),
            contentDescription = "Smart Life Manager logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(96.dp)
        )
        Text("Good morning, ${settings?.userName ?: "Md. Korimul Jaman"}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Here is your day at a glance", color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricCard("Today's overview", Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Expense", "৳${"%.0f".format(todayExpense ?: 0.0)}", Icons.Default.Payments)
                Metric("Study", formatMinutes(studyMinutes ?: 0), Icons.Default.MenuBook)
                Metric("Productivity", "${productivity.overallScore}%", Icons.Default.TrendingUp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Monthly spend  ৳${"%.0f".format(monthExpense ?: 0.0)} / ৳${"%.0f".format(settings?.monthlyBudgetAmount ?: 15000.0)}")
            LinearProgressIndicator(
                progress = { ((monthExpense ?: 0.0) / (settings?.monthlyBudgetAmount ?: 15000.0)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Add expense", Icons.Default.Add, onAddExpense, Modifier.weight(1f))
            ActionButton("Start study", Icons.Default.PlayArrow, onStudy, Modifier.weight(1f))
        }
        MetricCard("Next class", Modifier.fillMaxWidth()) {
            if (nextClass == null) Text("No classes added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("${nextClass.courseCode} · ${nextClass.courseName}", fontWeight = FontWeight.Bold)
                Text("${nextClass.dayOfWeek}, ${nextClass.startTime} - ${nextClass.endTime} · ${nextClass.room}")
            }
        }
        MetricCard("Today's progress", Modifier.fillMaxWidth()) {
            ProgressRow("Study goal", productivity.studyGoalPercent)
            ProgressRow("Tasks", productivity.taskCompletionPercent)
            ProgressRow("Routine", productivity.routineCompletionPercent)
        }
        Text(productivity.summaryMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpensesScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val monthTotal by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    ScreenColumn {
        Text("Expenses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("৳${"%.0f".format(monthTotal ?: 0.0)} spent this month", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
        if (expenses.isEmpty()) EmptyState("No expenses recorded")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(expenses, key = { it.id }) { expense ->
                ListCard {
                    Column(Modifier.weight(1f)) {
                        Text(expense.category, fontWeight = FontWeight.Bold)
                        Text(if (expense.note.isBlank()) expense.paymentMethod else expense.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(expense.dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("৳${"%.0f".format(expense.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { vm.deleteExpense(expense) }) { Icon(Icons.Default.Delete, "Delete expense") }
                }
            }
        }
    }
}

@Composable
private fun StudyScreen(vm: MainViewModel) {
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val goal by vm.todayGoal.collectAsState(initial = null)
    var showGoalDialog by remember { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableLongStateOf(0L) }
    var subject by rememberSaveable { mutableStateOf("Focused study") }
    LaunchedEffect(running) {
        while (running) { delay(1000); elapsed++ }
    }
    ScreenColumn {
        Text("Study", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        MetricCard("Today's target", Modifier.fillMaxWidth()) {
            Text(
                if (goal == null) "No target set" else "${goal!!.targetHours} hours planned",
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { showGoalDialog = true }) { Text("Set study target") }
        }
        MetricCard("Study timer", Modifier.fillMaxWidth()) {
            OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(formatSeconds(elapsed), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = { running = !running }) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Start") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (elapsed > 0) vm.addStudySession(subject, (elapsed / 60).toInt().coerceAtLeast(1)); elapsed = 0; running = false }) { Text("Save session") }
            }
        }
        Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        sessions.take(10).forEach { session ->
            ListCard {
                Column(Modifier.weight(1f)) { Text(session.subject, fontWeight = FontWeight.Bold); Text(session.dateString) }
                Text(formatMinutes(session.durationMinutes), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showGoalDialog) SetGoalDialog(vm) { showGoalDialog = false }
}

@Composable
private fun TasksScreen(vm: MainViewModel) {
        val tasks by vm.tasks.collectAsState(initial = emptyList())
        ScreenColumn {
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${tasks.count { !it.isCompleted }} remaining", color = MaterialTheme.colorScheme.primary)
            if (tasks.isEmpty()) EmptyState("No tasks yet")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    ListCard {
                        Checkbox(task.isCompleted, { vm.toggleTask(task) })
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.Bold)
                            if (task.description.isNotBlank()) Text(task.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${task.priority} priority · due tomorrow", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteTask(task) }) { Icon(Icons.Default.Delete, "Delete task") }
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(vm: MainViewModel) {
    val classes by vm.classes.collectAsState(initial = emptyList())
    val routines by vm.todayRoutines.collectAsState(initial = emptyList())
    ScreenColumn {
        Text("Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (classes.isEmpty()) EmptyState("No classes yet. Tap + to add this semester's courses.")
        classes.forEach { item -> ListCard { Column { Text("${item.dayOfWeek} · ${item.courseCode}", fontWeight = FontWeight.Bold); Text("${item.startTime} - ${item.endTime} · ${item.room}"); Text(item.courseName) } } }
        Text("Today's routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        routines.forEach { routine ->
            ListCard {
                Checkbox(routine.isCompleted, { vm.toggleRoutine(routine) })
                Column { Text(routine.title, fontWeight = FontWeight.Bold); Text("${routine.startTime} - ${routine.endTime} · ${routine.category}") }
            }
        }
    }
}

@Composable
private fun ReportsScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val goals by vm.weeklyGoals.collectAsState(initial = emptyList())
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val totalExpense = expenses.sumOf { it.amount }
    val totalStudy = sessions.sumOf { it.durationMinutes }
    val taskScore = if (tasks.isEmpty()) 100 else tasks.count { it.isCompleted } * 100 / tasks.size
    ScreenColumn {
        Text("Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Live insights from your stored data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricCard("All-time snapshot", Modifier.fillMaxWidth()) {
            ReportRow("Total expenses", "৳${"%.0f".format(totalExpense)}")
            ReportRow("Study time", formatMinutes(totalStudy))
            ReportRow("Task completion", "$taskScore%")
        }
        Text("Weekly goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        goals.forEach { goal ->
            val progress = (goal.completedValue / goal.targetValue * 100).toInt().coerceIn(0, 100)
            MetricCard(goal.title, Modifier.fillMaxWidth()) { ProgressRow("${goal.completedValue} / ${goal.targetValue} ${goal.unit}", progress) }
        }
    }
}

@Composable
private fun AssistantScreen(vm: MainViewModel) {
    val todayExpense by vm.todayExpenseTotal.collectAsState(initial = 0.0)
    val monthExpense by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    val studyMinutes by vm.todayStudyMinutes.collectAsState(initial = 0)
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val classes by vm.classes.collectAsState(initial = emptyList())
    val assistant = remember { LocalAiAssistant() }
    val onlineAssistant = remember { OnlineAiAssistant(fallback = assistant) }
    val scope = rememberCoroutineScope()
    val platformContext = LocalContext.current
    val savedMessages by vm.assistantMessages.collectAsState(initial = emptyList())
    var input by rememberSaveable { mutableStateOf("") }
    val assistantContext = AssistantContext(
        todayExpense = todayExpense ?: 0.0,
        monthExpense = monthExpense ?: 0.0,
        todayStudyMinutes = studyMinutes ?: 0,
        openTasks = tasks.count { !it.isCompleted },
        totalTasks = tasks.size,
        classCount = classes.size
    )
    var listening by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(platformContext) {
        TextToSpeech(platformContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            } else {
                ttsReady = false
            }
        }
    }
    val speechRecognizer = remember(platformContext) { SpeechRecognizer.createSpeechRecognizer(platformContext) }
    fun sendMessage(message: String = input) {
        val question = message.trim()
        if (question.isBlank()) return
        vm.saveAssistantMessage("ME", question)
        input = ""
        scope.launch {
            val answer = onlineAssistant.reply(question, assistantContext)
            vm.saveAssistantMessage("AI", answer)
            if (ttsReady) {
                val languageResult = tts.setLanguage(Locale("bn", "BD"))
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts.language = Locale.US
                }
                tts.setSpeechRate(0.58f)
                tts.setPitch(0.95f)
                tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "assistant-response")
            }
        }
    }
    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false }
            override fun onResults(results: Bundle?) {
                listening = false
                val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) sendMessage(spokenText)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val spokenText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) input = spokenText
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose {
            speechRecognizer.destroy()
            tts.stop()
            tts.shutdown()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("Smart Life Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Offline-first · আপনার data থেকেই উত্তর দেয়", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (savedMessages.isEmpty()) {
                item {
                    AssistantMessageBubble(
                        "হ্যালো! আমি আপনার Smart Life Assistant। আপনার expenses, study, tasks এবং class routine নিয়ে কথা বলতে পারেন।",
                        false
                    )
                }
            }
            items(savedMessages, key = { it.id }) { message ->
                AssistantMessageBubble(message.content, message.role == "ME")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("যেমন: আজ কত খরচ?") },
                maxLines = 3,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (listening) {
                        speechRecognizer.stopListening()
                        listening = false
                    } else if (contextCompatCheckSelfPermission(platformContext, Manifest.permission.RECORD_AUDIO)) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        }
                        speechRecognizer.startListening(intent)
                    } else {
                        (platformContext as? android.app.Activity)?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
                    }
                }
            ) { Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, if (listening) "Stop listening" else "Speak") }
            IconButton(enabled = input.isNotBlank(), onClick = { sendMessage() }) {
                Icon(Icons.Default.Send, "Send message")
            }
        }
    }
}

private fun contextCompatCheckSelfPermission(context: android.content.Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AssistantMessageBubble(text: String, isUser: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
    }
}

@Composable
private fun AddExpenseDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    val todayLabel = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: $todayLabel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (BDT)") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
            }

        },
        confirmButton = {
            Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { vm.addExpense(amount.toDouble(), category.ifBlank { "Others" }, note, "Cash"); dismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddClassDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var courseName by remember { mutableStateOf("") }
    var courseCode by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("Sunday") }
    var start by remember { mutableStateOf("10:00 AM") }
    var end by remember { mutableStateOf("11:30 AM") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add semester class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(courseName, { courseName = it }, label = { Text("Course name") }, singleLine = true)
                OutlinedTextField(courseCode, { courseCode = it }, label = { Text("Course code") }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text("Teacher") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("Room") }, singleLine = true)
                OutlinedTextField(day, { day = it }, label = { Text("Day: Sunday, Monday...") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Start") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("End") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(enabled = courseName.isNotBlank() && courseCode.isNotBlank(), onClick = {
                vm.addClass(courseName.trim(), courseCode.trim(), teacher.trim(), room.trim(), day.trim(), start.trim(), end.trim())
                dismiss()
            }) { Text("Save class") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddTaskDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(priority, { priority = it }, label = { Text("Priority: High, Medium, Low") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                vm.addTask(title.trim(), description.trim(), priority.ifBlank { "Medium" })
                dismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SetGoalDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var hours by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Set today's study target") },
        text = { OutlinedTextField(hours, { hours = it }, label = { Text("Hours") }, singleLine = true) },
        confirmButton = {
            Button(enabled = hours.toDoubleOrNull()?.let { it > 0 } == true, onClick = {
                vm.setTodayStudyGoal(hours.toDouble())
                dismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
@Composable private fun MetricCard(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); content() } }
}
@Composable private fun ListCard(content: @Composable RowScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content) } }
@Composable private fun Metric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, fontWeight = FontWeight.Bold); Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, modifier: Modifier) { Button(onClick = action, modifier = modifier) { Icon(icon, null); Spacer(Modifier.width(5.dp)); Text(label) } }
@Composable private fun ProgressRow(label: String, percent: Int) { Text("$label  $percent%"); LinearProgressIndicator({ percent / 100f }, Modifier.fillMaxWidth()) }
@Composable private fun ReportRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun formatMinutes(minutes: Int) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
private fun formatSeconds(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
