package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FocusMode(val label: String, val durationSeconds: Int) {
    FOCUS("Focus", 25 * 60),
    SHORT_BREAK("Short break", 5 * 60),
    LONG_BREAK("Long break", 15 * 60)
}

@Composable
internal fun FocusScreen(vm: MainViewModel) {
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todaySessions = sessions.filter { it.dateString == today }
    val completedFocusMinutes = todaySessions.sumOf { it.durationMinutes }
    var targetSessions by rememberSaveable { mutableIntStateOf(4) }
    var mode by rememberSaveable { mutableStateOf(FocusMode.FOCUS) }
    var remaining by rememberSaveable { mutableIntStateOf(FocusMode.FOCUS.durationSeconds) }
    var running by rememberSaveable { mutableStateOf(false) }
    var sessionNumber by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(running, mode) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining == 0) {
            running = false
            if (mode == FocusMode.FOCUS) {
                vm.addStudySession("Pomodoro focus session", mode.durationSeconds / 60)
                sessionNumber = (sessionNumber % targetSessions.coerceAtLeast(1)) + 1
                mode = if (sessionNumber == 1) FocusMode.LONG_BREAK else FocusMode.SHORT_BREAK
            }
        }
    }

    fun selectMode(next: FocusMode) {
        running = false
        mode = next
        remaining = next.durationSeconds
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Focus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Protect your attention, one session at a time", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FocusMode.values().forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mode == item,
                    onClick = { selectMode(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, FocusMode.values().size)
                ) { Text(item.label, maxLines = 1) }
            }
        }

        MetricCard("Current session", Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { remaining / mode.durationSeconds.toFloat() },
                    modifier = Modifier.size(220.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatTimer(remaining), fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Text(mode.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = { running = !running },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "PAUSE" else "START")
            }
            OutlinedButton(
                onClick = { running = false; remaining = mode.durationSeconds },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }

        MetricCard("Today’s focus target", Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("$completedFocusMinutes / ${targetSessions * 25} min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$targetSessions sessions planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { targetSessions = (targetSessions - 1).coerceAtLeast(1) }) { Text("-") }
                    Text("$targetSessions", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { targetSessions = (targetSessions + 1).coerceAtMost(12) }) { Text("+") }
                }
            }
            LinearProgressIndicator(
                progress = { (completedFocusMinutes / (targetSessions * 25f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Session $sessionNumber / $targetSessions", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }

        Text("Focus history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (sessions.isEmpty()) {
            EmptyState("Completed focus sessions will appear here.")
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions.take(10), key = { it.id }) { session ->
                    ListCard {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(session.subject, fontWeight = FontWeight.Bold)
                            Text("${session.durationMinutes} min · ${session.dateString}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        MetricCard("Statistics", Modifier.fillMaxWidth()) {
            val totalMinutes = sessions.sumOf { it.durationMinutes }
            val average = if (sessions.isEmpty()) 0 else totalMinutes / sessions.size
            Text("Total focus time: ${formatMinutes(totalMinutes)}")
            Text("Sessions completed: ${sessions.size}")
            Text("Average session: $average min")
        }
    }
}

private fun formatTimer(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatMinutes(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
