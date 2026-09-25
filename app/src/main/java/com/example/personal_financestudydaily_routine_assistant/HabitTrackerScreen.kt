package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personal_financestudydaily_routine_assistant.data.database.HabitEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun HabitTrackerScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsState(initial = emptyList())
    val completions by vm.habitCompletions.collectAsState(initial = emptyList())
    var selectedDate by remember { mutableStateOf(todayString()) }
    var showAdd by remember { mutableStateOf(false) }
    val selected = parseDate(selectedDate)
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selected.time)
    val monthDays = remember(selected.get(Calendar.MONTH), selected.get(Calendar.YEAR)) { calendarDays(selected) }
    val completedForSelected = habits.count { habit -> completions.any { it.habitId == habit.id && it.dateString == selectedDate } }
    val weekDates = (0..6).map { offset -> dateOffset(selected, offset - 6) }
    val weekDone = weekDates.sumOf { date -> habits.count { habit -> completions.any { it.habitId == habit.id && it.dateString == date } } }
    val weekTotal = habits.size * weekDates.size
    val streak = habits.maxOfOrNull { habit -> currentStreak(habit.id, completions.map { it.dateString to it.habitId }.toSet()) } ?: 0
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Habit Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Build consistency, one day at a time", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Repeat, null, tint = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Daily streak", Modifier.weight(1f)) { Text("$streak days", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            MetricCard("Today", Modifier.weight(1f)) { Text("${percent(completedForSelected, habits.size)}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            MetricCard("This week", Modifier.weight(1f)) { Text("${percent(weekDone, weekTotal)}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add habit")
        }
        MetricCard("Monthly calendar · $monthLabel", Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            monthDays.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    week.forEach { day ->
                        if (day == null) Spacer(Modifier.size(36.dp))
                        else {
                            val date = formatDate(day)
                            val done = habits.isNotEmpty() && habits.all { habit -> completions.any { it.habitId == habit.id && it.dateString == date } }
                            FilterChip(
                                selected = date == selectedDate,
                                onClick = { selectedDate = date },
                                label = { Text(day.get(Calendar.DAY_OF_MONTH).toString()) },
                                leadingIcon = if (done) ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null,
                                modifier = Modifier.sizeIn(minWidth = 36.dp)
                            )
                        }
                    }
                }
            }
        }
        Text("${SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(selected.time)} habits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (habits.isEmpty()) EmptyState("Add your first habit to start tracking.")
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habits, key = { it.id }) { habit ->
                val done = completions.any { it.habitId == habit.id && it.dateString == selectedDate }
                ListCard {
                    Checkbox(done, { vm.toggleHabit(habit, selectedDate) })
                    Column(Modifier.weight(1f)) {
                        Text(habit.name, fontWeight = FontWeight.Bold)
                        Text("${habit.category} · Reminder ${if (habit.reminderEnabled) habit.reminderTime else "off"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteHabit(habit) }) { Icon(Icons.Default.Delete, "Delete habit") }
                }
            }
        }
    }
    if (showAdd) AddHabitDialog(vm) { showAdd = false }
}

@Composable
private fun AddHabitDialog(vm: MainViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var reminderTime by remember { mutableStateOf("08:00 AM") }
    var reminderEnabled by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Habit name") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(reminderEnabled, { reminderEnabled = it })
                    Text("Reminder")
                }
                if (reminderEnabled) OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Reminder time (e.g. 08:00 AM)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) { vm.addHabit(name, category, reminderEnabled, reminderTime); dismiss() } }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

private fun percent(done: Int, total: Int): Int = if (total == 0) 0 else (done * 100 / total).coerceIn(0, 100)
private fun todayString() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
private fun formatDate(calendar: Calendar) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
private fun parseDate(value: String) = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value) ?: time }
private fun dateOffset(base: Calendar, offset: Int): String = Calendar.getInstance().apply { timeInMillis = base.timeInMillis; add(Calendar.DAY_OF_YEAR, offset) }.let(::formatDate)
private fun calendarDays(month: Calendar): List<Calendar?> {
    val first = month.clone() as Calendar
    first.set(Calendar.DAY_OF_MONTH, 1)
    val result = MutableList<Calendar?>(first.get(Calendar.DAY_OF_WEEK) - 1) { null }
    repeat(first.getActualMaximum(Calendar.DAY_OF_MONTH)) { index ->
        result += (first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, index + 1) }
    }
    while (result.size % 7 != 0) result += null
    return result
}
private fun currentStreak(habitId: Long, completed: Set<Pair<String, Long>>): Int {
    val cursor = Calendar.getInstance()
    var streak = 0
    while (completed.contains(formatDate(cursor) to habitId)) { streak++; cursor.add(Calendar.DAY_OF_YEAR, -1) }
    return streak
}
