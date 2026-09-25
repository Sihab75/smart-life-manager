package com.example.personal_financestudydaily_routine_assistant

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.example.personal_financestudydaily_routine_assistant.ai.AssistantContext
import com.example.personal_financestudydaily_routine_assistant.ai.AiProvider
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
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
        BackgroundNotificationWorker.schedule(this)
        setContent {
            Personal_FinanceStudyDaily_Routine_AssistantTheme {
                SmartLifeManagerApp()
            }
        }
    }
}

private enum class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home), EXPENSES("Finance", Icons.Default.AccountBalanceWallet),
    STUDY("Study", Icons.AutoMirrored.Filled.MenuBook), TASKS("Tasks", Icons.Default.CheckCircle),
    FOCUS("Focus", Icons.Default.Timer), HABITS("Habits", Icons.Default.Repeat),
    CP("CP Tracker", Icons.Default.Code),
    SCHEDULE("Calendar", Icons.Default.CalendarMonth), ACADEMIC("Academic", Icons.Default.School), REPORTS("Reports", Icons.Default.BarChart),
    ANALYTICS("Analytics", Icons.Default.Analytics),
    BATCH("Batch Sync", Icons.Default.Campaign), ASSISTANT("AI Assistant", Icons.Default.AutoAwesome),
    NOTES("Notes", Icons.Default.EditNote), DOCUMENTS("Documents", Icons.Default.Description),
    TRAVEL("Travel", Icons.Default.Train), NOTIFICATIONS("Notifications", Icons.Default.Notifications),
    SETTINGS("Settings", Icons.Default.Settings), ABOUT("About", Icons.Default.Info)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartLifeManagerApp(vm: MainViewModel = viewModel()) {
    var selected by rememberSaveable { mutableStateOf(Destination.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    var showStudyDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val settings by vm.settings.collectAsState(initial = null)
            AppDrawer(
                selected = selected,
                userName = settings?.userName ?: "Md. Korimul Jaman",
                onDestinationSelected = { destination ->
                    selected = destination
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected.label, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.height(76.dp), tonalElevation = 2.dp) {
                listOf(Destination.HOME, Destination.EXPENSES, Destination.STUDY, Destination.SCHEDULE).forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(destination.icon, destination.label, Modifier.size(25.dp)) },
                        label = { Text(destination.label, maxLines = 1, softWrap = false, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected == Destination.HOME || selected == Destination.EXPENSES || selected == Destination.TASKS || selected == Destination.HABITS || selected == Destination.SCHEDULE || selected == Destination.BATCH || selected == Destination.TRAVEL) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fabExpanded) {
                        QuickAction("Add expense", Icons.Default.Payments) { showExpenseDialog = true; fabExpanded = false }
                        QuickAction("Add task", Icons.Default.CheckCircle) { showTaskDialog = true; fabExpanded = false }
                        QuickAction("Add class", Icons.Default.Event) { showClassDialog = true; fabExpanded = false }
                        QuickAction("Add study session", Icons.AutoMirrored.Filled.MenuBook) { showStudyDialog = true; fabExpanded = false }
                        QuickAction("Add habit", Icons.Default.Repeat) { selected = Destination.HABITS; fabExpanded = false }
                        QuickAction("Add note", Icons.Default.EditNote) { selected = Destination.ASSISTANT; fabExpanded = false }
                        QuickAction("Add travel/ticket", Icons.Default.FlightTakeoff) { selected = Destination.TRAVEL; fabExpanded = false }
                        if (selected == Destination.BATCH) {
                            QuickAction("Publish batch update", Icons.Default.Campaign) { showBatchDialog = true; fabExpanded = false }
                        }
                    }
                    FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                        Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.Add, "Quick actions")
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                Destination.HOME -> Dashboard(
                    vm,
                    onAddExpense = { showExpenseDialog = true },
                    onStudy = { selected = Destination.STUDY },
                    onAddTask = { showTaskDialog = true },
                    onAddClass = { showClassDialog = true },
                    onAddStudy = { showStudyDialog = true }
                )
                Destination.EXPENSES -> ExpensesScreen(vm)
                Destination.STUDY -> StudyManagementScreen(vm)
                Destination.FOCUS -> FocusScreen(vm)
                Destination.TASKS -> TasksScreen(vm)
                Destination.HABITS -> HabitTrackerScreen(vm)
                Destination.CP -> CpTrackerScreen(vm)
                Destination.SCHEDULE -> CalendarScreen(vm)
                Destination.ACADEMIC -> AcademicScreen(vm)
                Destination.REPORTS -> ReportsScreen(vm)
                Destination.ANALYTICS -> AnalyticsScreen(vm)
                Destination.BATCH -> BatchSyncScreen(vm)
                Destination.ASSISTANT -> AssistantScreen(vm)
                Destination.NOTES -> DocumentsScreen(vm)
                Destination.DOCUMENTS -> DocumentsScreen(vm)
                Destination.TRAVEL -> TravelScreen(vm)
                Destination.NOTIFICATIONS -> NotificationsScreen(vm)
                Destination.SETTINGS -> SettingsScreen(vm)
                Destination.ABOUT -> AboutScreen()
            }

        }
    }
    if (showExpenseDialog) AddExpenseDialog(vm) { showExpenseDialog = false }
    if (showTaskDialog) AddTaskDialog(vm) { showTaskDialog = false }
    if (showClassDialog) AddClassDialog(vm) { showClassDialog = false }
    if (showStudyDialog) AddStudySessionDialog(vm) { showStudyDialog = false }
    if (showBatchDialog) AddBatchItemDialog(vm) { showBatchDialog = false }
    }
}

@Composable
private fun AppDrawer(
    selected: Destination,
    userName: String,
    onDestinationSelected: (Destination) -> Unit
) {
    var assistantExpanded by rememberSaveable { mutableStateOf(selected == Destination.ASSISTANT) }
    var academicExpanded by rememberSaveable { mutableStateOf(selected == Destination.ACADEMIC) }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 2.dp
    ) {
        DrawerProfileHeader(
            userName = userName,
            onSettingsClick = { onDestinationSelected(Destination.SETTINGS) }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp)
        ) {
            drawerSectionLabel("MAIN")
            drawerItem(Destination.HOME, onDestinationSelected, selected)
            drawerExpandableItem(
                destination = Destination.ASSISTANT,
                selected = selected,
                expanded = assistantExpanded,
                onExpandChange = { assistantExpanded = !assistantExpanded },
                onDestinationSelected = onDestinationSelected
            )
            if (assistantExpanded) {
                drawerSubitem("New Chat", Icons.Default.AddComment) { onDestinationSelected(Destination.ASSISTANT) }
                drawerSubitem("Chat History", Icons.Default.History) { onDestinationSelected(Destination.ASSISTANT) }
                drawerSubitem("Saved Chats", Icons.Default.Bookmark) { onDestinationSelected(Destination.ASSISTANT) }
            }
            drawerItem(Destination.TASKS, onDestinationSelected, selected)
            drawerItem(Destination.SCHEDULE, onDestinationSelected, selected)

            drawerSectionLabel("STUDY & ACADEMIC")
            drawerItem(Destination.STUDY, onDestinationSelected, selected)
            drawerExpandableItem(
                destination = Destination.ACADEMIC,
                selected = selected,
                expanded = academicExpanded,
                onExpandChange = { academicExpanded = !academicExpanded },
                onDestinationSelected = onDestinationSelected
            )
            if (academicExpanded) {
                drawerSubitem("Semester", Icons.Default.School) { onDestinationSelected(Destination.ACADEMIC) }
                drawerSubitem("Courses", Icons.AutoMirrored.Filled.MenuBook) { onDestinationSelected(Destination.ACADEMIC) }
                drawerSubitem("Routine", Icons.Default.Schedule) { onDestinationSelected(Destination.ACADEMIC) }
                drawerSubitem("Attendance", Icons.Default.HowToReg) { onDestinationSelected(Destination.ACADEMIC) }
                drawerSubitem("Results", Icons.Default.Assessment) { onDestinationSelected(Destination.ACADEMIC) }
            }
            drawerItem(Destination.CP, onDestinationSelected, selected)

            drawerSectionLabel("PERSONAL")
            drawerItem(Destination.EXPENSES, onDestinationSelected, selected)
            drawerItem(Destination.HABITS, onDestinationSelected, selected)
            drawerItem(Destination.FOCUS, onDestinationSelected, selected)
            drawerItem(Destination.TRAVEL, onDestinationSelected, selected)

            drawerSectionLabel("ORGANIZE")
            drawerItem(Destination.NOTES, onDestinationSelected, selected)
            drawerItem(Destination.DOCUMENTS, onDestinationSelected, selected)

            drawerSectionLabel("MORE")
            drawerExpandableItem(
                destination = null,
                label = "More",
                icon = Icons.Default.MoreHoriz,
                selected = selected,
                expanded = moreExpanded,
                onExpandChange = { moreExpanded = !moreExpanded },
                onDestinationSelected = onDestinationSelected
            )
            if (moreExpanded) {
                drawerItem(Destination.BATCH, onDestinationSelected, selected, indented = true)
                drawerItem(Destination.ANALYTICS, onDestinationSelected, selected, indented = true)
                drawerItem(Destination.REPORTS, onDestinationSelected, selected, indented = true)
                drawerItem(Destination.NOTIFICATIONS, onDestinationSelected, selected, indented = true)
                drawerItem(Destination.ABOUT, onDestinationSelected, selected, indented = true)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
        NavigationDrawerItem(
            label = { Text(Destination.SETTINGS.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
            icon = { Icon(Destination.SETTINGS.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
            selected = selected == Destination.SETTINGS,
            onClick = { onDestinationSelected(Destination.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun DrawerProfileHeader(userName: String, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profile",
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                userName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "Smart Life Manager",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                "CSE • Batch 251",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Open settings")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerSectionLabel(title: String) {
    item {
        Text(
            title,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerExpandableItem(
    destination: Destination?,
    label: String = destination?.label.orEmpty(),
    icon: androidx.compose.ui.graphics.vector.ImageVector = destination?.icon ?: Icons.Default.MoreHoriz,
    selected: Destination,
    expanded: Boolean,
    onExpandChange: () -> Unit,
    onDestinationSelected: (Destination) -> Unit
) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationDrawerItem(
                label = { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                selected = destination == selected,
                onClick = {
                    if (destination != null) onDestinationSelected(destination) else onExpandChange()
                },
                modifier = Modifier.weight(1f),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = onExpandChange,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerItem(
    destination: Destination,
    onDestinationSelected: (Destination) -> Unit,
    selected: Destination,
    indented: Boolean = false,
    footer: Boolean = false
) {
    item {
        NavigationDrawerItem(
            label = { Text(destination.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
            icon = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
            selected = selected == destination,
            onClick = { onDestinationSelected(destination) },
            modifier = Modifier.padding(
                start = if (indented) 28.dp else 12.dp,
                end = 12.dp,
                top = if (footer) 8.dp else 2.dp,
                bottom = if (footer) 8.dp else 2.dp
            ),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerSubitem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    item {
        NavigationDrawerItem(
            label = { Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp)) },
            selected = false,
            onClick = onClick,
            modifier = Modifier.padding(start = 28.dp, end = 12.dp, top = 1.dp, bottom = 1.dp),
            colors = NavigationDrawerItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun NotificationsScreen(vm: MainViewModel) {
    val notifications by vm.notifications.collectAsState(initial = emptyList())
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val classes by vm.classes.collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsState(initial = null)
    val monthExpense by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    val pendingTasks = tasks.filterNot { it.isCompleted }.take(10)
    val budgetWarning = (monthExpense ?: 0.0) > (settings?.monthlyBudgetAmount ?: 15000.0) * .8
    ScreenColumn {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (notifications.any { !it.isRead }) {
            OutlinedButton(
                onClick = vm::markAllNotificationsAsRead,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mark all as read")
            }
        }
        if (notifications.isEmpty() && pendingTasks.isEmpty() && classes.isEmpty() && !budgetWarning) {
            EmptyState("You're all caught up.")
        } else {
            notifications.take(20).forEach { notification ->
                ListCard {
                    Column(Modifier.weight(1f)) {
                        Text(notification.title, fontWeight = FontWeight.Bold)
                        Text(notification.message)
                        Text(notification.type.replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            pendingTasks.forEach { ListCard { Text("Task reminder", fontWeight = FontWeight.Bold); Text(it.title) } }
            if (budgetWarning) ListCard { Text("Budget warning", fontWeight = FontWeight.Bold); Text("80% of your monthly budget has been used.") }
            classes.firstOrNull()?.let { ListCard { Text("Upcoming class", fontWeight = FontWeight.Bold); Text("${it.courseCode} at ${it.startTime}") } }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState(initial = null)
    ScreenColumn {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        MetricCard("Profile", Modifier.fillMaxWidth()) {
            Text(settings?.userName ?: "Md. Korimul Jaman", fontWeight = FontWeight.SemiBold)
            Text("Monthly budget: ৳${"%.0f".format(settings?.monthlyBudgetAmount ?: 15000.0)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutScreen() {
    ScreenColumn {
        Text("About", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        MetricCard("Smart Life Manager", Modifier.fillMaxWidth()) {
            Text("Personal finance, study, tasks, and schedule management in one place.")
            Text("Version 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CpTrackerScreen(vm: MainViewModel) {
    val problems by vm.cpProblems.collectAsState(initial = emptyList())
    val goals by vm.cpGoals.collectAsState(initial = emptyList())
    var showProblemDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    val platforms = listOf("Codeforces", "VJudge", "LeetCode", "CodeChef")
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val weekStart = dateOffset(-6)
    val monthStart = today.substring(0, 8) + "01"
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("CP Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Competitive programming progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showProblemDialog = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Log problem") }
            OutlinedButton(onClick = { showGoalDialog = true }, modifier = Modifier.weight(1f)) { Text("Set targets") }
        }
        platforms.forEach { platform ->
            val solved = problems.count { it.platform == platform }
            val goal = goals.firstOrNull { it.platform == platform }
            MetricCard(platform, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Solved", solved.toString(), Icons.Default.CheckCircle)
                    Metric("Target", (goal?.monthlyTarget ?: 0).toString(), Icons.Default.Flag)
                    Metric("This week", problems.count { it.platform == platform && it.solvedDate >= weekStart }.toString(), Icons.Default.DateRange)
                }
                if ((goal?.monthlyTarget ?: 0) > 0) {
                    val target = goal?.monthlyTarget ?: 1
                    LinearProgressIndicator({ (solved.toFloat() / target).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text("$solved / $target monthly problems", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Recent problems", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (problems.isEmpty()) EmptyState("No CP problems logged yet")
        problems.take(20).forEach { problem ->
            ListCard {
                Column(Modifier.weight(1f)) {
                    Text(problem.problemName, fontWeight = FontWeight.Bold)
                    Text("${problem.platform} · ${problem.topic.ifBlank { "Topic not set" }} · ${problem.solvedDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${problem.difficulty.ifBlank { "Difficulty not set" }} · ${problem.attempts} attempt(s) · ${if (problem.isUpsolved) "Upsolved" else "Solved"}", fontSize = 12.sp)
                }
                IconButton(onClick = { vm.deleteCpProblem(problem) }) { Icon(Icons.Default.Delete, "Delete problem") }
            }
        }
        Text("Weekly: ${problems.count { it.solvedDate >= weekStart }} · Monthly: ${problems.count { it.solvedDate >= monthStart }}", color = MaterialTheme.colorScheme.primary)
    }
    if (showProblemDialog) AddCpProblemDialog(vm) { showProblemDialog = false }
    if (showGoalDialog) AddCpGoalDialog(vm) { showGoalDialog = false }
}

@Composable
private fun Dashboard(
    vm: MainViewModel,
    onAddExpense: () -> Unit,
    onStudy: () -> Unit,
    onAddTask: () -> Unit,
    onAddClass: () -> Unit,
    onAddStudy: () -> Unit
) {
    val settings by vm.settings.collectAsState(initial = null)
    val unreadNotifications by vm.unreadNotifications.collectAsState(initial = 0)
    val todayExpense by vm.todayExpenseTotal.collectAsState(initial = 0.0)
    val monthExpense by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    val studyMinutes by vm.todayStudyMinutes.collectAsState(initial = 0)
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val studySessions by vm.studySessions.collectAsState(initial = emptyList())
    val goal by vm.todayGoal.collectAsState(initial = null)
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val routines by vm.todayRoutines.collectAsState(initial = emptyList())
    val classes by vm.classes.collectAsState(initial = emptyList())
    val academicClasses by vm.academicClasses.collectAsState(initial = emptyList())
    val academicExams by vm.academicExams.collectAsState(initial = emptyList())
    var showNotifications by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val completedTasks = tasks.count { it.isCompleted }
    val pendingTasks = tasks.filterNot { it.isCompleted }.take(3)
    val productivity = ProductivityCalculator.calculate(
        goal?.targetHours?.times(60)?.toInt() ?: 240, studyMinutes ?: 0,
        tasks.size, completedTasks, routines.size, routines.count { it.isCompleted }
    )
    val categoryTotals = expenses
        .filter { it.dateString.endsWith(SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())) }
        .groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.amount } }
    val nextClass = upcomingClass(classes, nowMillis)
    val todayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    val todayAcademicClasses = academicClasses.filter { it.dayOfWeek.equals(todayName, ignoreCase = true) }
    val nextExam = academicExams.firstOrNull { it.examDate >= SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val notificationCount = unreadNotifications + pendingTasks.size + if (monthExpense ?: 0.0 > (settings?.monthlyBudgetAmount ?: 15000.0) * .8) 1 else 0
    val dateLabel = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("$greeting,", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(settings?.userName ?: "Md. Korimul Jaman", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { showNotifications = true }) {
                    Icon(Icons.Default.Notifications, "Notifications")
                }
                if (notificationCount > 0) {
                    Badge(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)) { Text(notificationCount.coerceAtMost(9).toString()) }
                }
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Person, "Profile", Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Small progress every day leads to big results.", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  28°C  ·  Weather unavailable? Your day still matters.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Expense", "৳${"%.0f".format(monthExpense ?: 0.0)}", "this month", Icons.Default.Payments, Modifier.weight(1f))
            SummaryCard("Study", formatMinutes(studyMinutes ?: 0), "today", Icons.AutoMirrored.Filled.MenuBook, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Tasks", "$completedTasks / ${tasks.size}", "completed", Icons.Default.TaskAlt, Modifier.weight(1f))
            SummaryCard("Upcoming", "${classes.size}", "classes", Icons.Default.Event, Modifier.weight(1f))
        }
        MetricCard("Next class", Modifier.fillMaxWidth()) {
            if (nextClass == null) Text("No classes added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("${nextClass.courseCode} · ${nextClass.courseName}", fontWeight = FontWeight.Bold)
                Text("${nextClass.dayOfWeek}, ${nextClass.startTime} - ${nextClass.endTime} · ${nextClass.room}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Starts in ${countdownLabel(nextClass, nowMillis)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (todayAcademicClasses.isNotEmpty()) {
                MetricCard("Today's classes", Modifier.fillMaxWidth()) {
                    todayAcademicClasses.forEach { item ->
                        Text("${item.startTime}  ${item.courseCode} - ${item.courseName}", fontWeight = FontWeight.SemiBold)
                        Text(listOf(item.room.takeIf { it.isNotBlank() }?.let { "Room $it" }, item.building).filterNotNull().joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            nextExam?.let { exam ->
                MetricCard("Upcoming exam", Modifier.fillMaxWidth()) {
                    Text("${exam.courseCode} - ${exam.courseName}", fontWeight = FontWeight.Bold)
                    Text("${daysUntilAcademic(exam.examDate)} days left · ${exam.examDate} · ${exam.startTime}")
                    if (exam.room.isNotBlank()) Text("Room ${exam.room}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MetricCard("Today's progress", Modifier.fillMaxWidth()) {
            pendingTasks.forEach { task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.isCompleted, { vm.toggleTask(task) })
                    Text(task.title, Modifier.weight(1f), maxLines = 1)
                }
            }
            if (tasks.isEmpty()) Text("No tasks yet. Use the + button to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProgressRow("Overall completion", productivity.taskCompletionPercent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Monthly expenses", Modifier.weight(1f)) {
                if (categoryTotals.isEmpty()) Text("No spending yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else ExpensePieChart(categoryTotals, Modifier.size(112.dp).align(Alignment.CenterHorizontally))
                categoryTotals.entries.sortedByDescending { it.value }.take(3).forEach { (name, amount) ->
                    Text("$name  ৳${"%.0f".format(amount)}", fontSize = 12.sp, maxLines = 1)
                }
            }
            MetricCard("Study trend", Modifier.weight(1f)) {
                StudySparkline(studySessions, Modifier.fillMaxWidth().height(92.dp))
                Text("Last 7 days", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Expense", Icons.Default.Add, onAddExpense, Modifier.weight(1f))
            ActionButton("Task", Icons.Default.CheckCircle, onAddTask, Modifier.weight(1f))
            ActionButton("Study", Icons.AutoMirrored.Filled.MenuBook, onAddStudy, Modifier.weight(1f))
        }
        Text(productivity.summaryMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pendingTasks.isNotEmpty()) pendingTasks.forEach { Text("Task reminder · ${it.title}") }
                    if (monthExpense ?: 0.0 > (settings?.monthlyBudgetAmount ?: 15000.0) * .8) Text("Budget warning · 80% of monthly budget used")
                    if (nextClass != null) Text("Upcoming class · ${nextClass.courseCode} at ${nextClass.startTime}")
                    if (notificationCount == 0) Text("You're all caught up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ExpensesScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val monthTotal by vm.monthExpenseTotal.collectAsState(initial = 0.0)
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val goals by vm.savingsGoals.collectAsState(initial = emptyList())
    val recurring by vm.recurringExpenses.collectAsState(initial = emptyList())
    var ledger by rememberSaveable { mutableStateOf("All") }
    var period by rememberSaveable { mutableStateOf("Month") }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val periodStartMillis = System.currentTimeMillis() - when (period) {
        "Day" -> 86_400_000L
        "Week" -> 7 * 86_400_000L
        "Year" -> 365 * 86_400_000L
        else -> 30 * 86_400_000L
    }
    val visibleExpenses = expenses.filter {
        (ledger == "All" || it.ledger == ledger) && it.dateMillis >= periodStartMillis
    }
    val categoryTotals = visibleExpenses.groupBy { it.category }.mapValues { (_, values) -> values.sumOf { it.amount } }
    val maxCategory = categoryTotals.values.maxOrNull() ?: 1.0
    val budget = budgets.firstOrNull { it.category == "Monthly" }?.amount ?: 0.0
    val budgetProgress = if (budget > 0) (monthTotal ?: 0.0) / budget else 0.0
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Finance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Track every taka with clarity", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("All", "Personal", "Family", "Children", "Academic").forEach { option ->
                SegmentedButton(selected = ledger == option, onClick = { ledger = option }, shape = SegmentedButtonDefaults.itemShape(0, 5)) { Text(option) }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Day", "Week", "Month", "Year").forEach { option ->
                SegmentedButton(selected = period == option, onClick = { period = option }, shape = SegmentedButtonDefaults.itemShape(0, 4)) { Text(option) }
            }
        }
        MetricCard("${period}ly report", Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Total", "৳${"%.0f".format(visibleExpenses.sumOf { it.amount })}", Icons.Default.Payments)
                Metric("Transactions", visibleExpenses.size.toString(), Icons.AutoMirrored.Filled.ReceiptLong)
                Metric("Categories", categoryTotals.size.toString(), Icons.Default.Category)
            }
        }
        if (categoryTotals.isNotEmpty()) {
            MetricCard("Category breakdown", Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ExpensePieChart(categoryTotals, Modifier.size(150.dp))
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        categoryTotals.entries.sortedByDescending { it.value }.take(5).forEach { (name, amount) ->
                            Text("$name  ৳${"%.0f".format(amount)}", fontSize = 12.sp)
                        }
                    }
                }
            }
            MetricCard("Spending by category", Modifier.fillMaxWidth()) {
                categoryTotals.entries.sortedByDescending { it.value }.take(6).forEach { (name, amount) ->
                    Text("$name  ৳${"%.0f".format(amount)}", fontSize = 12.sp)
                    LinearProgressIndicator({ (amount / maxCategory).toFloat() }, Modifier.fillMaxWidth())
                }
            }
        }
        MetricCard("Monthly budget", Modifier.fillMaxWidth()) {
            Text("৳${"%.0f".format(monthTotal ?: 0.0)} of ৳${"%.0f".format(budget)}")
            LinearProgressIndicator({ budgetProgress.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            if (budgetProgress >= 0.8) Text("Budget alert: you have used ${(budgetProgress * 100).toInt()}% of your limit.", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCategoryDialog = true }) { Text("Custom category") }
                OutlinedButton(onClick = { showGoalDialog = true }) { Text("Savings goal") }
            }
        }
        if (goals.isNotEmpty()) {
            Text("Savings goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            goals.forEach { goal ->
                MetricCard(goal.name, Modifier.fillMaxWidth()) {
                    Text("৳${"%.0f".format(goal.savedAmount)} / ৳${"%.0f".format(goal.targetAmount)}")
                    LinearProgressIndicator({ (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (recurring.isNotEmpty()) {
            Text("Recurring expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            recurring.forEach { item -> ListCard {
                Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Bold); Text("${item.frequency} · ${item.category} · ${item.ledger}") }
                Text("৳${"%.0f".format(item.amount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } }
        }
        Text("Expense history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (visibleExpenses.isEmpty()) EmptyState("No expenses recorded")
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(visibleExpenses, key = { it.id }) { expense ->
                ListCard {
                    Column(Modifier.weight(1f)) {
                        Text(if (expense.subcategory.isBlank()) expense.category else "${expense.category} · ${expense.subcategory}", fontWeight = FontWeight.Bold)
                        Text("${expense.ledger} · ${if (expense.note.isBlank()) expense.paymentMethod else expense.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(expense.dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("৳${"%.0f".format(expense.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { vm.deleteExpense(expense) }) { Icon(Icons.Default.Delete, "Delete expense") }
                }
            }
        }
        if (showGoalDialog) AddSavingsGoalDialog(vm) { showGoalDialog = false }
        if (showCategoryDialog) AddCategoryDialog(vm) { showCategoryDialog = false }
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
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val legacyClasses by vm.classes.collectAsState(initial = emptyList())
    val academicClasses by vm.academicClasses.collectAsState(initial = emptyList())
    val routines by vm.todayRoutines.collectAsState(initial = emptyList())
    val todayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())

    val academicRows = academicClasses.map { item ->
        Triple(item.dayOfWeek, item.courseCode, "${item.startTime} - ${item.endTime} · ${item.room.ifBlank { item.building }} · ${item.courseName}")
    }
    val legacyRows = legacyClasses.map { item ->
        Triple(item.dayOfWeek, item.courseCode, "${item.startTime} - ${item.endTime} · ${item.room} · ${item.courseName}")
    }
    val allRows = academicRows + legacyRows
    val todayRoutine = (academicClasses.filter { it.dayOfWeek.equals(todayName, ignoreCase = true) }.map { "${it.startTime} - ${it.endTime} · ${it.courseCode} - ${it.courseName}" } +
        legacyClasses.filter { it.dayOfWeek.equals(todayName, ignoreCase = true) }.map { "${it.startTime} - ${it.endTime} · ${it.courseCode} - ${it.courseName}" })

    ScreenColumn {
        Text("Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (allRows.isEmpty()) {
            EmptyState("No classes yet. Tap + to add this semester's courses.")
        } else {
            allRows.forEach { (day, code, details) ->
                ListCard {
                    Column {
                        Text("$day · $code", fontWeight = FontWeight.Bold)
                        Text(details)
                    }
                }
            }
        }

        Text("Today's routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (todayRoutine.isNotEmpty()) {
            todayRoutine.forEach { line ->
                ListCard {
                    Text(line, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (routines.isEmpty()) {
            EmptyState("No class routine scheduled for today.")
        } else {
            routines.forEach { routine ->
                ListCard {
                    Checkbox(routine.isCompleted, { vm.toggleRoutine(routine) })
                    Column { Text(routine.title, fontWeight = FontWeight.Bold); Text("${routine.startTime} - ${routine.endTime} · ${routine.category}") }
                }
            }
        }
    }
}

@Composable
private fun TravelScreen(vm: MainViewModel) {
    val trips by vm.travelTrips.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var ticketUri by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val ticketPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            ticketUri = uri.toString()
        }
    }
    val now = System.currentTimeMillis()
    val upcoming = trips.filter { it.departureMillis >= now }
    val history = trips.filter { it.departureMillis < now }.sortedByDescending { it.departureMillis }
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Travel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Trips, tickets and journey reminders", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Train, null, tint = MaterialTheme.colorScheme.primary)
        }
        Button(onClick = { ticketUri = ""; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Add trip or ticket")
        }
        Text("Upcoming journeys", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (upcoming.isEmpty()) EmptyState("No upcoming trips. Add your next journey to get a reminder.")
        upcoming.forEach { trip ->
            TravelTripCard(trip, onDelete = { vm.deleteTravelTrip(trip) })
        }
        Text("Trip history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (history.isEmpty()) Text("Completed journeys will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.forEach { trip ->
            TravelTripCard(trip, onDelete = { vm.deleteTravelTrip(trip) })
        }
    }
    if (showDialog) {
        AddTravelTripDialog(
            vm = vm,
            ticketUri = ticketUri,
            onPickTicket = { ticketPicker.launch(arrayOf("application/pdf", "image/*")) },
            dismiss = { showDialog = false }
        )
    }
}

@Composable
private fun TravelTripCard(trip: TravelTripEntity, onDelete: () -> Unit) {
    ListCard {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(trip.title, fontWeight = FontWeight.Bold)
            Text("${trip.departureStation} → ${trip.arrivalStation}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text("${trip.travelDate} · ${trip.transportType}${trip.serviceName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
            Text("${trip.departureTime} → ${trip.arrivalTime}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val details = listOf(
                trip.seatNumber.takeIf { it.isNotBlank() }?.let { "Seat $it" },
                trip.coachNumber.takeIf { it.isNotBlank() }?.let { "Coach $it" },
                trip.bookingReference.takeIf { it.isNotBlank() }?.let { "Ref $it" }
            ).filterNotNull().joinToString(" · ")
            if (details.isNotBlank()) Text(details, fontSize = 12.sp)
            if (trip.ticketUri.isNotBlank()) Text("Ticket attached", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete trip") }
    }
}

@Composable
private fun AddTravelTripDialog(
    vm: MainViewModel,
    ticketUri: String,
    onPickTicket: () -> Unit,
    dismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var transport by rememberSaveable { mutableStateOf("Train") }
    var service by rememberSaveable { mutableStateOf("") }
    var departure by rememberSaveable { mutableStateOf("16:40") }
    var arrival by rememberSaveable { mutableStateOf("22:15") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var seat by rememberSaveable { mutableStateOf("") }
    var coach by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var information by rememberSaveable { mutableStateOf("") }
    var reminder by rememberSaveable { mutableStateOf(true) }
    val departureMillis = parseTravelMillis(date, departure)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add travel ticket") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Trip name (e.g. Dhaka → Jamalpur)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(transport, { transport = it }, label = { Text("Train / Bus / Flight") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(service, { service = it }, label = { Text("Train / bus / flight name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(departure, { departure = it }, label = { Text("Departure (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(arrival, { arrival = it }, label = { Text("Arrival (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text("Departure station") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it }, label = { Text("Arrival station") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(seat, { seat = it }, label = { Text("Seat") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(coach, { coach = it }, label = { Text("Coach") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(reference, { reference = it }, label = { Text("Booking reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(information, { information = it }, label = { Text("Ticket information") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onPickTicket, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (ticketUri.isBlank()) "Attach ticket PDF or image" else "Ticket attached")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reminder, { reminder = it })
                    Text("Remind me 24 hours before departure")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && from.isNotBlank() && to.isNotBlank() && departureMillis != null,
                onClick = {
                    vm.addTravelTrip(
                        title, date, transport, service, departure, arrival, from, to, seat, coach,
                        reference, information, ticketUri, departureMillis!!, reminder
                    )
                    dismiss()
                }
            ) { Text("Save trip") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

private fun parseTravelMillis(date: String, time: String): Long? =
    try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
            .parse("$date $time")?.time
    } catch (_: java.text.ParseException) {
        null
    }

@Composable
private fun BatchSyncScreen(vm: MainViewModel) {
                val items by vm.batchItems.collectAsState(initial = emptyList())
                val settings by vm.settings.collectAsState(initial = null)
                var selectedType by rememberSaveable { mutableStateOf("All") }
                var showDialog by remember { mutableStateOf(false) }
                val types = listOf("All", "Class routine", "CT/Exam", "Assignment", "Teacher notice", "Room change", "Presentation", "Announcement", "CR announcement")
                val visibleItems = items.filter { selectedType == "All" || it.type == selectedType }
                ScreenColumn {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Batch Sync", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Shared updates for your university batch", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (settings?.isClassRepresentative == true) {
                        Text("CR mode enabled · your updates are published to the batch", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    } else {
                        Text("Read-only mode · enable CR mode to publish announcements", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("I am the Class Representative", Modifier.weight(1f))
                        Switch(
                            checked = settings?.isClassRepresentative == true,
                            onCheckedChange = vm::setClassRepresentative
                        )
                    }
                    SmartBatchBroadcastPanel(vm, settings?.isClassRepresentative == true)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        types.take(3).forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) { Text(type, maxLines = 1) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.drop(3).take(3).forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.drop(6).forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) })
                        }
                    }
                    if (visibleItems.isEmpty()) {
                        EmptyState("No batch updates yet. CRs can publish the first announcement.")
                    } else {
                        visibleItems.forEach { item ->
                            ListCard {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.type.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        if (item.isImportant) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("IMPORTANT", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    if (item.courseCode.isNotBlank()) Text(item.courseCode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (item.details.isNotBlank()) Text(item.details)
                                    val metadata = listOf(item.date, item.time, item.location).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (metadata.isNotBlank()) Text(metadata, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (item.postedBy.isNotBlank()) Text("Posted by: ${item.postedBy}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (settings?.isClassRepresentative == true) {
                                    IconButton(onClick = { vm.deleteBatchItem(item) }) { Icon(Icons.Default.Delete, "Delete update") }
                                }
                            }
                        }
                    }
                    if (settings?.isClassRepresentative == true) {
                        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Campaign, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create announcement")
                        }
                    }
                }
                if (showDialog) AddBatchItemDialog(vm) { showDialog = false }
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
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val cpProblems by vm.cpProblems.collectAsState(initial = emptyList())
    val cpGoals by vm.cpGoals.collectAsState(initial = emptyList())
    val academicClasses by vm.academicClasses.collectAsState(initial = emptyList())
    val academicExams by vm.academicExams.collectAsState(initial = emptyList())
    val academicEvents by vm.academicDaoEvents.collectAsState(initial = emptyList())
    val todayRoutines by vm.todayRoutines.collectAsState(initial = emptyList())
    val assistant = remember { LocalAiAssistant() }
    val onlineAssistant = remember { OnlineAiAssistant(fallback = assistant) }
    val scope = rememberCoroutineScope()
    val platformContext = LocalContext.current
    val providerPreferences = remember {
        platformContext.getSharedPreferences("assistant_preferences", android.content.Context.MODE_PRIVATE)
    }
    var selectedProviderId by rememberSaveable {
        mutableStateOf(providerPreferences.getString("provider", AiProvider.GEMINI.id) ?: AiProvider.GEMINI.id)
    }
    val selectedProvider = AiProvider.fromId(selectedProviderId)
    val conversations by vm.assistantConversations.collectAsState(initial = emptyList())
    val archivedConversations by vm.archivedAssistantConversations.collectAsState(initial = emptyList())
    var selectedConversationId by rememberSaveable { mutableStateOf(1L) }
    val allConversations = (conversations + archivedConversations).distinctBy { it.id }
    val selectedConversation = allConversations.firstOrNull { it.id == selectedConversationId }
        ?: conversations.firstOrNull()
    val savedMessages by vm.assistantMessages(selectedConversation?.id ?: selectedConversationId).collectAsState(initial = emptyList())
    var input by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(true) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var attachedFile by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) attachedFile = uri.toString().substringAfterLast('/').substringAfterLast(':')
    }
    val assistantContext = AssistantContext(
        todayExpense = todayExpense ?: 0.0,
        monthExpense = monthExpense ?: 0.0,
        todayStudyMinutes = studyMinutes ?: 0,
        openTasks = tasks.count { !it.isCompleted },
        totalTasks = tasks.size,
        classCount = classes.size,
        categorySpend = expenses.groupBy { it.category }.mapValues { (_, items) -> items.sumOf { it.amount } },
        monthCategorySpend = expenses.filter { it.dateString.startsWith(SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())) }
            .groupBy { it.category }.mapValues { (_, items) -> items.sumOf { it.amount } },
        cpSolvedByPlatform = cpProblems.groupingBy { it.platform }.eachCount(),
        cpTargetsByPlatform = cpGoals.associate { it.platform to it.monthlyTarget },
        cpTopicsThisWeek = cpProblems.filter { it.solvedDate >= dateOffset(-6) }
            .groupingBy { it.topic.ifBlank { "Uncategorized" } }.eachCount(),
        cpTopicsPreviousWeek = cpProblems.filter { it.solvedDate >= dateOffset(-13) && it.solvedDate < dateOffset(-6) }
            .groupingBy { it.topic.ifBlank { "Uncategorized" } }.eachCount(),
        academicClasses = academicClasses.filter {
            val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            it.dayOfWeek.equals(SimpleDateFormat("EEEE", Locale.getDefault()).format(tomorrow.time), true)
        }.map { "${it.courseCode} ${it.courseName} at ${it.startTime}" },
        upcomingExams = academicExams.filter { it.examDate >= SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
            .map { "${it.courseCode} ${it.courseName} on ${it.examDate} at ${it.startTime}${it.room.takeIf { room -> room.isNotBlank() }?.let { " in Room $it" } ?: ""}" }
        ,todayPlan = buildList {
            todayRoutines.forEach { add("${it.startTime} → ${it.title}") }
            classes.filter { it.dayOfWeek.equals(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), true) }
                .forEach { add("${it.startTime} → ${it.courseCode} ${it.courseName}") }
            academicClasses.filter { it.dayOfWeek.equals(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), true) }
                .forEach { add("${it.startTime} → ${it.courseCode} ${it.courseName}") }
            academicEvents.filter { it.date == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                .forEach { add("${it.time.ifBlank { "All day" }} → ${it.title}") }
            tasks.filter { !it.isCompleted && SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.deadlineMillis)) == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                .forEach { add("${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.deadlineMillis))} → ${it.title}") }
        }.sortedBy { it.substringBefore(" →") }
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
        val conversationId = selectedConversation?.id ?: selectedConversationId
        vm.saveAssistantMessage("ME", if (attachedFile != null) "$question\n\n[Attached: $attachedFile]" else question, conversationId)
        input = ""
        attachedFile = null
        scope.launch {
            val answer = if (selectedProvider == AiProvider.LOCAL) {
                assistant.reply(question, assistantContext)
            } else {
                onlineAssistant.reply(question, assistantContext, selectedProvider)
            }
            vm.saveAssistantMessage("AI", answer, conversationId)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactLayout = maxWidth < 600.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedVisibility(
            visible = showHistory,
            modifier = if (compactLayout) Modifier.fillMaxWidth() else Modifier
        ) {
            Card(
                Modifier
                    .fillMaxHeight()
                    .then(if (compactLayout) Modifier.fillMaxWidth() else Modifier.width(292.dp)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Chat history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            vm.createAssistantConversation {
                                selectedConversationId = it
                                showHistory = false
                            }
                        }) {
                            Icon(Icons.Default.AddComment, "New chat")
                        }
                    }
                    OutlinedTextField(
                        searchQuery, { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Search chats") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    val visible = allConversations.filter { it.title.contains(searchQuery, true) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        visible.groupBy { conversationDateLabel(it.updatedAtMillis) }.forEach { (dateLabel, datedConversations) ->
                            item {
                                Text(
                                    dateLabel,
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(datedConversations, key = { it.id }) { conversation ->
                                Row(
                                    Modifier.fillMaxWidth().background(
                                        if (conversation.id == selectedConversation?.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ).padding(start = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (conversation.isArchived) Icons.Default.Archive else Icons.Default.ChatBubbleOutline,
                                        null,
                                        Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(conversation.title, Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp), maxLines = 1)
                                    if (conversation.isPinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.size(16.dp))
                                    IconButton(onClick = {
                                        if (conversation.isArchived) vm.restoreAssistantConversation(conversation)
                                        selectedConversationId = conversation.id
                                        showHistory = false
                                    }) { Icon(Icons.Default.ChatBubbleOutline, "Open chat") }
                                    IconButton(onClick = { selectedConversationId = conversation.id; renameText = conversation.title; showRename = true }) {
                                        Icon(Icons.Default.Edit, "Rename")
                                    }
                                    IconButton(onClick = {
                                        if (conversation.isArchived) vm.restoreAssistantConversation(conversation)
                                        else vm.archiveAssistantConversation(conversation)
                                    }) {
                                        Icon(if (conversation.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, "Archive chat")
                                    }
                                    IconButton(onClick = { vm.toggleAssistantPinned(conversation) }) {
                                        Icon(Icons.Default.PushPin, "Pin chat")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !compactLayout || !showHistory,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Smart AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(selectedConversation?.title ?: "New chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (selectedProvider == AiProvider.LOCAL) "Local/offline · আপনার data থেকেই উত্তর দেয়"
                        else "Secure backend · ${selectedProvider.displayName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var providerMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { providerMenuExpanded = true }) {
                        Text(selectedProvider.displayName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false }
                    ) {
                        AiProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    selectedProviderId = provider.id
                                    providerPreferences.edit().putString("provider", provider.id).apply()
                                    providerMenuExpanded = false
                                },
                                leadingIcon = {
                                    if (provider == selectedProvider) Icon(Icons.Default.Check, null)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    vm.createAssistantConversation {
                        selectedConversationId = it
                        showHistory = false
                    }
                }) {
                    Icon(Icons.Default.AddComment, "New chat")
                }
                IconButton(onClick = { showHistory = !showHistory }) {
                    Icon(if (showHistory) Icons.AutoMirrored.Filled.ViewSidebar else Icons.Default.History, "Chat history")
                }
            }
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
                AssistantMessageBubble(
                    text = message.content,
                    isUser = message.role == "ME",
                    onCopy = {
                        val clipboard = platformContext.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Assistant response", message.content))
                    },
                    onRegenerate = if (message.role == "AI") {
                        { savedMessages.lastOrNull { it.role == "ME" }?.let { sendMessage(it.content) } }
                    } else null
                )
            }
        }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (attachedFile != null) {
                AssistChip(onClick = { attachedFile = null }, label = { Text(attachedFile!!, maxLines = 1) }, leadingIcon = { Icon(Icons.Default.AttachFile, null) })
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("যেমন: আজ কত খরচ?") },
                maxLines = 3,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/pdf", "image/*")) }) {
                Icon(Icons.Default.AttachFile, "Upload file")
            }
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
                Icon(Icons.AutoMirrored.Filled.Send, "Send message")
            }
            }
        }
        }
        }
        if (showRename && selectedConversation != null) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("Rename chat") },
                text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true, label = { Text("Chat name") }) },
                confirmButton = { Button(onClick = { vm.renameAssistantConversation(selectedConversation, renameText); showRename = false }) { Text("Rename") } },
                dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
            )
        }
        if (showDelete && selectedConversation != null) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Delete chat?") },
                text = { Text("This permanently removes the conversation and its messages.") },
                confirmButton = { Button(onClick = {
                    vm.deleteAssistantConversation(selectedConversation)
                    showDelete = false
                    vm.createAssistantConversation { selectedConversationId = it }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
            )
        }
    }
}

private fun contextCompatCheckSelfPermission(context: android.content.Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun conversationDateLabel(timestampMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val date = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return when {
        now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == date.get(java.util.Calendar.DAY_OF_YEAR) -> "Today"
        now.get(java.util.Calendar.YEAR) == date.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) - date.get(java.util.Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMillis))
    }
}

@Composable
private fun AssistantMessageBubble(
    text: String,
    isUser: Boolean,
    onCopy: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 340.dp)) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(18.dp)
            ) { MarkdownMessage(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
            if (!isUser && (onCopy != null || onRegenerate != null)) {
                Row {
                    onCopy?.let { IconButton(onClick = it) { Icon(Icons.Default.ContentCopy, "Copy response", Modifier.size(18.dp)) } }
                    onRegenerate?.let { IconButton(onClick = it) { Icon(Icons.Default.Refresh, "Regenerate response", Modifier.size(18.dp)) } }
                }
            }
        }
    }
}

@Composable
private fun MarkdownMessage(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.split("```").forEachIndexed { index, block ->
            if (index % 2 == 1) {
                Text(
                    block.trim(),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            } else {
                block.trim().takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ExpensePieChart(values: Map<String, Double>, modifier: Modifier) {
    val palette = listOf(Color(0xFF6750A4), Color(0xFF006A6A), Color(0xFF9C4146), Color(0xFF7A5900), Color(0xFF386A20))
    val total = values.values.sum().coerceAtLeast(1.0)
    Canvas(modifier) {
        var start = -90f
        values.entries.forEachIndexed { index, entry ->
            val sweep = (entry.value / total * 360f).toFloat()
            drawArc(palette[index % palette.size], start, sweep, true)
            start += sweep
        }
        drawCircle(Color.White, radius = size.minDimension * .18f)
    }
}

@Composable
private fun AddCategoryDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Create custom category") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true)
            OutlinedTextField(parent, { parent = it }, label = { Text("Sub-category of (optional)") }, singleLine = true)
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank(), onClick = { vm.addCategory(name, parent); dismiss() }) { Text("Create") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AddSavingsGoalDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("New savings goal") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("Target amount (BDT)") }, singleLine = true)
        }
    }, confirmButton = {
        Button(enabled = name.isNotBlank() && amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { vm.addSavingsGoal(name, amount.toDouble()); dismiss() }) { Text("Save goal") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AddExpenseDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var subcategory by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var ledger by remember { mutableStateOf("Personal") }
    var recurring by remember { mutableStateOf(false) }
    val todayLabel = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date: $todayLabel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (BDT)") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(subcategory, { subcategory = it }, label = { Text("Sub-category (optional)") }, singleLine = true)
                OutlinedTextField(ledger, { ledger = it }, label = { Text("Ledger: Personal, Family, Children, Academic") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(recurring, { recurring = it })
                    Text("Save as recurring expense")
                }
            }

        },
        confirmButton = {
            Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = {
                vm.addExpense(amount.toDouble(), category.ifBlank { "Others" }, note, "Cash", subcategory, ledger.ifBlank { "Personal" }, recurring, if (recurring) "Monthly" else "")
                dismiss()
            }) { Text("Save") }
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
    val startMinutes = parseRoutineTime(start)
    val endMinutes = parseRoutineTime(end)
    val timeError = when {
        startMinutes == null || endMinutes == null -> "Use a valid time such as 10:00 AM"
        endMinutes <= startMinutes -> "End time must be after start time"
        else -> null
    }
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
                timeError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = courseName.isNotBlank() && courseCode.isNotBlank() && timeError == null, onClick = {
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
private fun AddCpProblemDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var platform by remember { mutableStateOf("Codeforces") }
    var name by remember { mutableStateOf("") }
    var contest by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf("1") }
    var editorial by remember { mutableStateOf("") }
    var upsolved by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Log CP problem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(platform, { platform = it }, label = { Text("Platform") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Problem name") }, singleLine = true)
                OutlinedTextField(contest, { contest = it }, label = { Text("Contest name (optional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(rating, { rating = it }, label = { Text("Rating") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(difficulty, { difficulty = it }, label = { Text("Difficulty") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(topic, { topic = it }, label = { Text("Topic (e.g. Graph)") }, singleLine = true)
                OutlinedTextField(attempts, { attempts = it }, label = { Text("Attempts") }, singleLine = true)
                OutlinedTextField(editorial, { editorial = it }, label = { Text("Editorial link (optional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(upsolved, { upsolved = it })
                    Text("Upsolved")
                }
                Text("Solved date: $date", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && platform.isNotBlank() && (attempts.toIntOrNull() ?: 0) > 0,
                onClick = {
                    vm.addCpProblem(platform, name, contest, rating.toIntOrNull(), difficulty, topic, date,
                        attempts.toInt(), editorial, upsolved)
                    dismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddCpGoalDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var platform by remember { mutableStateOf("Codeforces") }
    var weekly by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Set CP targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(platform, { platform = it }, label = { Text("Platform") }, singleLine = true)
                OutlinedTextField(weekly, { weekly = it }, label = { Text("Weekly target") }, singleLine = true)
                OutlinedTextField(monthly, { monthly = it }, label = { Text("Monthly target") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = platform.isNotBlank() && (weekly.toIntOrNull() ?: -1) >= 0 && (monthly.toIntOrNull() ?: -1) >= 0,
                onClick = { vm.saveCpGoal(platform.trim(), weekly.toInt(), monthly.toInt()); dismiss() }
            ) { Text("Save targets") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddStudySessionDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add study session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true)
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Duration (minutes)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = subject.isNotBlank() && minutes.toIntOrNull()?.let { it > 0 } == true,
                onClick = { vm.addStudySession(subject.trim(), minutes.toInt()); dismiss() }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddBatchItemDialog(vm: MainViewModel, dismiss: () -> Unit) {
    val settings by vm.settings.collectAsState(initial = null)
    var type by rememberSaveable { mutableStateOf("CR announcement") }
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var courseCode by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var important by rememberSaveable { mutableStateOf(false) }
    val types = listOf("Class routine", "CT/Exam", "Assignment", "Teacher notice", "Room change", "Presentation", "Announcement", "CR announcement")
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Publish batch update") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Update type", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.take(2).forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.drop(2).take(2).forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.drop(4).take(2).forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.drop(6).forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it }, label = { Text("Details / topic") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(courseCode, { courseCode = it }, label = { Text("Course code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(time, { time = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(location, { location = it }, label = { Text("Room / location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(important, { important = it })
                    Text("Mark as important")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && settings?.isClassRepresentative == true,
                onClick = {
                    vm.addBatchItem(
                        type = type,
                        title = title,
                        details = details,
                        courseCode = courseCode,
                        date = date,
                        time = time,
                        location = location,
                        postedBy = "${settings?.userName ?: "Class Representative"} (CR)",
                        isImportant = important
                    )
                    dismiss()
                }
            ) { Text("Publish") }
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

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    SmallFloatingActionButton(onClick = action, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Icon(icon, label)
    }
    Text(label, Modifier.padding(end = 48.dp), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$label · $detail", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudySparkline(sessions: List<StudySessionEntity>, modifier: Modifier) {
    val days = (0..6).map { offset ->
        val date = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -6 + offset)
        }.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.time) }
        sessions.filter { it.dateString == date }.sumOf { it.durationMinutes }.toFloat()
    }
    val max = days.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val step = if (days.size > 1) size.width / (days.size - 1) else size.width
        val points = days.mapIndexed { index, value ->
            androidx.compose.ui.geometry.Offset(index * step, size.height - (value / max * size.height))
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 5f)
        }
        points.forEach { drawCircle(lineColor, 5f, it) }
    }
}

private fun upcomingClass(classes: List<ClassEntity>, nowMillis: Long): ClassEntity? {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    val today = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
    val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    fun startMinutes(value: String): Int? = try {
        val parsed = SimpleDateFormat("hh:mm a", Locale.US).parse(value) ?: return null
        java.util.Calendar.getInstance().apply { time = parsed }.let {
            it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
        }
    } catch (_: java.text.ParseException) { null }
    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val todayIndex = dayNames.indexOfFirst { it.equals(today, ignoreCase = true) }.coerceAtLeast(0)
    fun minutesAway(item: ClassEntity): Int {
        val classDay = dayNames.indexOfFirst { it.equals(item.dayOfWeek, ignoreCase = true) }
        val dayDelta = (classDay - todayIndex + 7) % 7
        val start = startMinutes(item.startTime) ?: return Int.MAX_VALUE
        return dayDelta * 1440 + start - currentMinutes
    }
    return classes.minByOrNull { minutesAway(it).let { value -> if (value < 0) value + 7 * 1440 else value } }
}

private fun dateOffset(days: Int): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() + days * 86_400_000L))

private fun countdownLabel(classEntity: ClassEntity, nowMillis: Long): String {
    val start = try {
        SimpleDateFormat("hh:mm a", Locale.US).parse(classEntity.startTime)
    } catch (_: java.text.ParseException) { null }
    if (start == null) return "soon"
    val startCalendar = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMillis
        val parsed = java.util.Calendar.getInstance().apply { time = start }
        set(java.util.Calendar.HOUR_OF_DAY, parsed.get(java.util.Calendar.HOUR_OF_DAY))
        set(java.util.Calendar.MINUTE, parsed.get(java.util.Calendar.MINUTE))
        set(java.util.Calendar.SECOND, 0)
    }
    val todayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(nowMillis))
    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val todayIndex = dayNames.indexOfFirst { it.equals(todayName, ignoreCase = true) }.coerceAtLeast(0)
    val classIndex = dayNames.indexOfFirst { it.equals(classEntity.dayOfWeek, ignoreCase = true) }
    val dayDelta = (classIndex - todayIndex + 7) % 7
    if (dayDelta > 0) startCalendar.add(java.util.Calendar.DAY_OF_YEAR, dayDelta)
    val minutes = ((startCalendar.timeInMillis - nowMillis) / 60_000L).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}

@Composable internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}
@Composable internal fun MetricCard(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); content() } }
}
@Composable internal fun ListCard(content: @Composable RowScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content) } }
@Composable private fun Metric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, fontWeight = FontWeight.Bold); Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable internal fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, modifier: Modifier) { Button(onClick = action, modifier = modifier) { Icon(icon, null); Spacer(Modifier.width(5.dp)); Text(label) } }
@Composable private fun ProgressRow(label: String, percent: Int) { Text("$label  $percent%"); LinearProgressIndicator({ percent / 100f }, Modifier.fillMaxWidth()) }
@Composable private fun ReportRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) } }
@Composable internal fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun formatMinutes(minutes: Int) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
private fun formatSeconds(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun daysUntilAcademic(date: String): Int =
    ((runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time }.getOrNull()
        ?: System.currentTimeMillis()) - System.currentTimeMillis()).let {
        (it / 86_400_000L).toInt().coerceAtLeast(0)
    }
