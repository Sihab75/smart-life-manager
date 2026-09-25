package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class AnalyticsDay(val label: String, val date: String)

@Composable
internal fun AnalyticsScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val habits by vm.habits.collectAsState(initial = emptyList())
    val completions by vm.habitCompletions.collectAsState(initial = emptyList())
    val problems by vm.cpProblems.collectAsState(initial = emptyList())
    val expenses by vm.expenses.collectAsState(initial = emptyList())
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsState(initial = null)

    val days = rememberAnalyticsDays()
    val dayValues = days.map { day ->
        val studyMinutes = sessions.filter { it.dateString == day.date }.sumOf { it.durationMinutes }
        val habitDone = completions.count { it.dateString == day.date }
        val expense = expenses.filter { it.dateString == day.date }.sumOf { it.amount }
        AnalyticsDayValue(day, studyMinutes / 60f + habitDone * 0.25f, expense)
    }
    val weeklyStudyMinutes = sessions.filter { it.dateString in days.map { day -> day.date } }.sumOf { it.durationMinutes }
    val weeklyFocusMinutes = sessions.filter { it.dateString in days.map { day -> day.date } && it.subject == "Pomodoro focus session" }.sumOf { it.durationMinutes }
    val weeklyCp = problems.count { it.solvedDate in days.map { day -> day.date } }
    val habitPossible = habits.size * days.size
    val weeklyHabitDone = completions.count { it.dateString in days.map { day -> day.date } }
    val habitRate = percent(weeklyHabitDone, habitPossible)
    val monthlyExpense = expenses.filter { it.dateString.startsWith(monthPrefix()) }.sumOf { it.amount }
    val monthlyBudget = budgets.firstOrNull { it.category == "Monthly" }?.amount
        ?: settings?.monthlyBudgetAmount
        ?: 0.0
    val budgetRate = if (monthlyBudget > 0) (monthlyExpense / monthlyBudget).toFloat().coerceAtLeast(0f) else 0f

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("A clear view of your week and your momentum", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary)
        }

        MetricCard("This week", Modifier.fillMaxWidth()) {
            Text("PRODUCTIVITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Study and habit momentum by day", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            WeeklyProductivityChart(dayValues)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsMetric("Tasks completed", tasks.count { it.isCompleted }.toString(), Icons.Default.CheckCircle, Modifier.weight(1f))
            AnalyticsMetric("Tasks pending", tasks.count { !it.isCompleted }.toString(), Icons.Default.PendingActions, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsMetric("Study hours", formatHours(weeklyStudyMinutes), Icons.Default.Schedule, Modifier.weight(1f))
            AnalyticsMetric("Focus hours", formatHours(weeklyFocusMinutes), Icons.Default.Timer, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsMetric("Habit completion", "$habitRate%", Icons.Default.CheckCircle, Modifier.weight(1f))
            AnalyticsMetric("CP solved", weeklyCp.toString(), Icons.Default.Code, Modifier.weight(1f))
        }

        MetricCard("Habit completion", Modifier.fillMaxWidth()) {
            Text("$weeklyHabitDone of $habitPossible check-ins completed this week", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (habitRate / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { day ->
                    val done = completions.count { it.dateString == day.date }
                    Text("${day.label} $done", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        MetricCard("Expense trends", Modifier.fillMaxWidth()) {
            Text("Daily spending · ${monthName()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ExpenseTrendChart(dayValues)
        }

        MetricCard("Budget usage", Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("৳${"%.0f".format(monthlyExpense)} spent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("of ৳${"%.0f".format(monthlyBudget)} monthly budget", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(budgetRate * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (budgetRate > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { budgetRate.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (budgetRate > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(if (budgetRate > 1f) "You are over budget this month." else "You have ৳${"%.0f".format((monthlyBudget - monthlyExpense).coerceAtLeast(0.0))} remaining.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class AnalyticsDayValue(val day: AnalyticsDay, val productivity: Float, val expense: Double)

@Composable
private fun WeeklyProductivityChart(values: List<AnalyticsDayValue>) {
    val max = values.maxOfOrNull { it.productivity }?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value.day.label, modifier = Modifier.width(76.dp), fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { (value.productivity / max).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(10.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpenseTrendChart(values: List<AnalyticsDayValue>) {
    val max = values.maxOfOrNull { it.expense }?.coerceAtLeast(1.0) ?: 1.0
    Row(Modifier.fillMaxWidth().height(105.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (value.expense > 0) "৳${"%.0f".format(value.expense)}" else "—", fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (value.expense / max).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(value.day.label.take(3), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AnalyticsMetric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    MetricCard(title, modifier) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun rememberAnalyticsDays(): List<AnalyticsDay> {
    val today = Calendar.getInstance()
    return (6 downTo 0).map { offset ->
        val date = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        AnalyticsDay(SimpleDateFormat("EEEE", Locale.getDefault()).format(date.time), dateString(date))
    }
}

private fun dateString(calendar: Calendar) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
private fun monthPrefix() = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Calendar.getInstance().time)
private fun monthName() = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
private fun percent(done: Int, total: Int) = if (total == 0) 0 else (done * 100 / total).coerceIn(0, 100)
private fun formatHours(minutes: Int) = "${minutes / 60}.${(minutes % 60) / 6}h"
