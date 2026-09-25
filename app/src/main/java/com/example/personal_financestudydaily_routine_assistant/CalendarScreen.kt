@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.personal_financestudydaily_routine_assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personal_financestudydaily_routine_assistant.data.database.ClassEntity
import com.example.personal_financestudydaily_routine_assistant.data.database.DailyRoutineEntity
import com.example.personal_financestudydaily_routine_assistant.data.database.AcademicClassEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun CalendarScreen(vm: MainViewModel) {
    val legacyClasses by vm.classes.collectAsState(initial = emptyList())
    val academicClasses by vm.academicClasses.collectAsState(initial = emptyList())
    val routines by vm.todayRoutines.collectAsState(initial = emptyList())
    val items = remember(legacyClasses, academicClasses, routines) {
        (legacyClasses.map(::toRoutineItem) + academicClasses.map(::toRoutineItem)).let { classes ->
            classes + routines.map(::toRoutineItem)
        }
    }
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1) }
    val weekStart = remember(weekOffset) {
        Calendar.getInstance().apply {
            time = startOfCurrentWeek()
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }.time
    }
    val todayKey = routineDateKey(Date(now.value))
    val weekDays = remember(weekStart) { routineDayNames.mapIndexed { index, day -> day to dateForRoutineDay(weekStart, index) } }
    val dayRequesters = remember { List(7) { BringIntoViewRequester() } }
    val classesThisWeek = weekDays.flatMap { (day, date) ->
        sortRoutineItems(items.filter { it.dayOfWeek.equals(day.second, ignoreCase = true) && it.matchesDate(date) })
            .map { item -> date to item }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    LaunchedEffect(selectedDay, weekOffset) {
        dayRequesters[selectedDay.coerceIn(0, 6)].bringIntoView()
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CalendarHeader(
            weekStart = weekStart,
            weekOffset = weekOffset,
            onPrevious = { weekOffset--; selectedDay = 0 },
            onNext = { weekOffset++; selectedDay = 0 },
            onToday = {
                weekOffset = 0
                selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            }
        )
        DaySelector(
            weekDays = weekDays,
            selectedDay = selectedDay,
            todayKey = todayKey,
            onDaySelected = { selectedDay = it }
        )
        NextClassCard(items, now.value)
        WeekSummary(classesThisWeek.map { it.second })
        AnimatedContent(
            targetState = weekOffset,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "week transition"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                weekDays.forEachIndexed { index, (day, date) ->
                    val dayItems = sortRoutineItems(items.filter { it.dayOfWeek.equals(day.second, ignoreCase = true) && it.matchesDate(date) })
                    DaySection(
                        dayName = day.second,
                        date = date,
                        items = dayItems,
                        isToday = routineDateKey(date) == todayKey,
                        nowMillis = now.value,
                        bringIntoViewRequester = dayRequesters[index],
                        onDelete = { item ->
                            if (item.isDailyRoutine) {
                                vm.deleteRoutine(item.toDailyRoutine())
                            } else if (item.isAcademic) {
                                vm.deleteAcademicClass(item.toAcademicClass())
                            } else {
                                vm.deleteClass(item.toLegacyClass())
                            }
                        }
                    )
                }
            }
        }
        if (items.isEmpty()) {
            EmptyRoutineState()
        }
        Spacer(Modifier.size(82.dp))
    }
}

@Composable
private fun CalendarHeader(
    weekStart: Date,
    weekOffset: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val weekEnd = dateForRoutineDay(weekStart, 6)
    val range = SimpleDateFormat("MMM d", Locale.getDefault())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Class Routine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (weekOffset == 0) "Your weekly academic schedule" else "Weekly academic schedule",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.School, "Class routine", Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous week" }) {
                    Icon(Icons.Default.ChevronLeft, "Previous week")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (weekOffset == 0) "This week" else if (weekOffset < 0) "Previous week" else "Next week",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("${range.format(weekStart)} - ${range.format(weekEnd)}", fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Next week" }) {
                    Icon(Icons.Default.ChevronRight, "Next week")
                }
            }
        }
        if (weekOffset != 0) {
            OutlinedButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Today")
            }
        }
    }
}

@Composable
private fun DaySelector(
    weekDays: List<Pair<Pair<Int, String>, Date>>,
    selectedDay: Int,
    todayKey: String,
    onDaySelected: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekDays.forEachIndexed { index, (day, date) ->
            val isToday = routineDateKey(date) == todayKey
            FilterChip(
                selected = index == selectedDay,
                onClick = { onDaySelected(index) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.second.take(3).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("d", Locale.getDefault()).format(date), fontSize = 14.sp)
                        if (isToday) Text("TODAY", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                leadingIcon = if (isToday) {
                    { Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
                } else null
            )
        }
    }
}

@Composable
private fun NextClassCard(items: List<RoutineItem>, nowMillis: Long) {
    val next = findNextRoutine(items, nowMillis)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (next?.isNow == true) "CURRENT CLASS" else "NEXT CLASS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (next == null) {
                Text("No upcoming classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Your schedule is clear for now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${next.item.courseCode} · ${next.item.courseName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatRoutineTime(next.item.startTime)} - ${formatRoutineTime(next.item.endTime)}${next.item.roomLabel()?.let { " · $it" } ?: ""}")
                Text(if (next.isNow) "Ends in ${formatRelativeMinutes(next.remainingMinutes)}" else "Starts in ${formatRelativeMinutes(next.minutesAway)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WeekSummary(items: List<RoutineItem>) {
    val days = items.map { it.dayOfWeek.lowercase() }.distinct().size
    val labs = items.count { it.kind.contains("lab", ignoreCase = true) }
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryValue(items.size.toString(), "Classes")
            SummaryValue(days.toString(), "Days")
            SummaryValue(labs.toString(), "Labs")
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaySection(
    dayName: String,
    date: Date,
    items: List<RoutineItem>,
    isToday: Boolean,
    nowMillis: Long,
    bringIntoViewRequester: BringIntoViewRequester,
    onDelete: (RoutineItem) -> Unit
) {
    Column(
        Modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 36.dp).clip(RoundedCornerShape(4.dp)).background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isToday) "TODAY · ${dayName.uppercase()}" else dayName.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("MMMM d", Locale.getDefault()).format(date), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${items.size} ${if (items.size == 1) "class" else "classes"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (items.isEmpty()) {
            EmptyDayState()
        } else {
            items.forEachIndexed { index, item ->
                ClassCard(index + 1, item, classStatus(item, date, nowMillis), onDelete)
            }
        }
    }
}

@Composable
private fun EmptyDayState() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Default.School, "No classes", Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("No classes today", fontWeight = FontWeight.SemiBold)
                Text("Enjoy your free day!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ClassCard(number: Int, item: RoutineItem, status: RoutineStatus, onDelete: (RoutineItem) -> Unit) {
    val statusColor = when (status) {
        RoutineStatus.NOW -> MaterialTheme.colorScheme.primary
        RoutineStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        RoutineStatus.UPCOMING -> MaterialTheme.colorScheme.secondary
    }
    Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Text("%02d".format(number), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.courseCode.ifBlank { "Class" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(status.label, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text(item.courseName.ifBlank { "Untitled class" }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, "Time", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(5.dp))
                    Text("${formatRoutineTime(item.startTime)} - ${formatRoutineTime(item.endTime)}")
                    formatDuration(item.startTime, item.endTime)?.let {
                        Text(" · $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val room = item.roomLabel()
                if (room != null || item.teacher.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (room != null) {
                            Icon(Icons.Default.LocationOn, "Room", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(5.dp))
                            Text(room, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.teacher.isNotBlank()) Text("${if (room != null) " · " else ""}${item.teacher}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Default.Delete, "Delete ${item.courseCode}")
            }
        }
    }
}

private enum class RoutineStatus(val label: String) {
    UPCOMING("UPCOMING"), NOW("NOW"), COMPLETED("COMPLETED")
}

private fun classStatus(item: RoutineItem, date: Date, nowMillis: Long): RoutineStatus {
    val today = routineDateKey(Date(nowMillis))
    val dateKey = routineDateKey(date)
    if (dateKey < today) return RoutineStatus.COMPLETED
    if (dateKey > today) return RoutineStatus.UPCOMING
    val start = parseRoutineTime(item.startTime) ?: return RoutineStatus.UPCOMING
    val end = parseRoutineTime(item.endTime) ?: return if (start > Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE)) RoutineStatus.UPCOMING else RoutineStatus.COMPLETED
    val minute = Calendar.getInstance().apply { timeInMillis = nowMillis }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    return when {
        minute in start until end -> RoutineStatus.NOW
        minute >= end -> RoutineStatus.COMPLETED
        else -> RoutineStatus.UPCOMING
    }
}

private data class NextRoutine(val item: RoutineItem, val minutesAway: Int, val remainingMinutes: Int, val isNow: Boolean)

private fun findNextRoutine(items: List<RoutineItem>, nowMillis: Long): NextRoutine? {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val todayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
    val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    return (0..6).asSequence().flatMap { offset ->
        val dayIndex = (todayIndex + offset) % 7
        val dayName = routineDayNames[dayIndex].second
        sortRoutineItems(items.filter { it.dayOfWeek.equals(dayName, ignoreCase = true) }).asSequence().mapNotNull { item ->
            val start = parseRoutineTime(item.startTime) ?: return@mapNotNull null
            val end = parseRoutineTime(item.endTime) ?: return@mapNotNull null
            val startDelta = offset * 1440 + start - nowMinutes
            val endDelta = offset * 1440 + (if (end >= start) end else end + 1440) - nowMinutes
            if (offset == 0 && endDelta > 0 && startDelta <= 0) NextRoutine(item, 0, endDelta, true)
            else if (startDelta >= 0) NextRoutine(item, startDelta, 0, false)
            else null
        }
    }.minWithOrNull(compareBy<NextRoutine> { it.isNow.not() }.thenBy { if (it.isNow) 0 else it.minutesAway })
}

private fun formatRelativeMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"

private fun RoutineItem.roomLabel(): String? =
    listOf(room, building).firstOrNull { it.isNotBlank() }?.trim()

private fun toRoutineItem(item: ClassEntity) = RoutineItem(item.id, item.courseCode, item.courseName, item.teacher, item.room, "", item.dayOfWeek, item.startTime, item.endTime, "Class", false)
private fun toRoutineItem(item: AcademicClassEntity) = RoutineItem(item.id, item.courseCode, item.courseName, item.teacher, item.room, item.building, item.dayOfWeek, item.startTime, item.endTime, item.kind, true)
private fun toRoutineItem(item: DailyRoutineEntity): RoutineItem {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.dateString) }.getOrNull()
    val day = date?.let { SimpleDateFormat("EEEE", Locale.getDefault()).format(it) } ?: item.dateString
    return RoutineItem(item.id, "", item.title, "", "", "", day, item.startTime, item.endTime, item.category, false, true, item.dateString)
}

private fun RoutineItem.matchesDate(date: Date): Boolean =
    specificDate == null || specificDate == routineDateKey(date)

private fun RoutineItem.toLegacyClass() = ClassEntity(id, courseName, courseCode, teacher, room, dayOfWeek, startTime, endTime)
private fun RoutineItem.toAcademicClass() = AcademicClassEntity(0, id, courseCode, courseName, teacher, "", dayOfWeek, startTime, endTime, room, building, kind)
private fun RoutineItem.toDailyRoutine() = DailyRoutineEntity(id, courseName, startTime, endTime, kind, dateString = specificDate ?: routineDateKey(Date()))

@Composable
private fun EmptyRoutineState() {
    Text(
        "No classes in your routine yet. Use the + button to add one.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
