package com.example.personal_financestudydaily_routine_assistant

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.personal_financestudydaily_routine_assistant.data.database.AcademicClassEntity

internal val routineDayNames = listOf(
    Calendar.SUNDAY to "Sunday",
    Calendar.MONDAY to "Monday",
    Calendar.TUESDAY to "Tuesday",
    Calendar.WEDNESDAY to "Wednesday",
    Calendar.THURSDAY to "Thursday",
    Calendar.FRIDAY to "Friday",
    Calendar.SATURDAY to "Saturday"
)

internal fun parseRoutineTime(value: String): Int? {
    val input = value.trim()
    if (input.isBlank()) return null
    val patterns = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm", "h a", "hh a")
    patterns.forEach { pattern ->
        val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = formatter.parse(input, position)
            ?.takeIf { position.index == input.length } ?: return@forEach
        val calendar = Calendar.getInstance().apply { time = parsed }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    return null
}

internal fun sortRoutineItems(items: List<RoutineItem>): List<RoutineItem> =
    items.sortedWith(compareBy<RoutineItem>(
        { dayOrder(it.dayOfWeek) },
        { parseRoutineTime(it.startTime) == null },
        { parseRoutineTime(it.startTime) ?: Int.MAX_VALUE },
        { it.courseCode }
    ))

internal fun dayOrder(day: String): Int =
    routineDayNames.indexOfFirst { it.second.equals(day.trim(), ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it }

internal data class ScheduleConflict(
    val first: AcademicClassEntity,
    val second: AcademicClassEntity,
    val overlapStart: Int,
    val overlapEnd: Int
)

internal fun detectScheduleConflicts(classes: List<AcademicClassEntity>): List<ScheduleConflict> {
    val conflicts = mutableListOf<ScheduleConflict>()
    classes.filter { parseRoutineTime(it.startTime) != null && parseRoutineTime(it.endTime) != null }
        .groupBy { it.dayOfWeek.trim().lowercase(Locale.getDefault()) }
        .values.forEach { sameDay ->
            val ordered = sameDay.sortedBy { parseRoutineTime(it.startTime) }
            ordered.forEachIndexed { index, first ->
                val firstStart = parseRoutineTime(first.startTime) ?: return@forEachIndexed
                val firstEnd = normalizedEnd(firstStart, parseRoutineTime(first.endTime) ?: return@forEachIndexed)
                ordered.drop(index + 1).forEach { second ->
                    val secondStart = parseRoutineTime(second.startTime) ?: return@forEach
                    val secondEnd = normalizedEnd(secondStart, parseRoutineTime(second.endTime) ?: return@forEach)
                    val overlapStart = maxOf(firstStart, secondStart)
                    val overlapEnd = minOf(firstEnd, secondEnd)
                    if (overlapStart < overlapEnd) conflicts += ScheduleConflict(first, second, overlapStart, overlapEnd)
                }
            }
        }
    return conflicts
}

private fun normalizedEnd(start: Int, end: Int): Int = if (end <= start) end + 24 * 60 else end

internal data class FreePeriod(val dayOfWeek: String, val startMinutes: Int, val endMinutes: Int) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}

internal fun calculateFreePeriods(
    classes: List<AcademicClassEntity>,
    minimumMinutes: Int = 30
): List<FreePeriod> {
    return classes.groupBy { dayOrder(it.dayOfWeek) }
        .toSortedMap()
        .flatMap { (_, dayClasses) ->
            val ordered = dayClasses.mapNotNull { item ->
                val start = parseRoutineTime(item.startTime)
                val end = start?.let { normalizedEnd(it, parseRoutineTime(item.endTime) ?: return@mapNotNull null) }
                if (start != null && end != null) item to (start to end) else null
            }.sortedBy { it.second.first }
        if (ordered.size < 2) emptyList()
        else {
            val merged = mutableListOf<Pair<Int, Int>>()
            ordered.forEach { (_, interval) ->
                val previous = merged.lastOrNull()
                if (previous != null && interval.first <= previous.second) {
                    merged[merged.lastIndex] = previous.first to maxOf(previous.second, interval.second)
                } else {
                    merged += interval
                }
            }
            merged.zipWithNext().mapNotNull { (current, next) ->
                val start = current.second
                val end = next.first
                if (end - start >= minimumMinutes) FreePeriod(ordered.first().first.dayOfWeek, start, end) else null
            }
        }
    }
}

internal enum class ScheduleState { CURRENT, NEXT, COMPLETED, NONE }

internal data class ScheduleStatus(
    val item: RoutineItem?,
    val state: ScheduleState,
    val minutes: Int = 0
)

internal fun scheduleStatus(items: List<RoutineItem>, nowMillis: Long): ScheduleStatus {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val candidates = (-1..7).flatMap { offset ->
        val classDate = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        val dayName = routineDayNames[classDate.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY].second
        val dateKey = routineDateKey(classDate.time)
        items.filter { it.dayOfWeek.equals(dayName, true) && (it.specificDate == null || it.specificDate == dateKey) }
            .mapNotNull { item ->
                val startMinutes = parseRoutineTime(item.startTime) ?: return@mapNotNull null
                val endMinutes = parseRoutineTime(item.endTime) ?: return@mapNotNull null
                val start = (classDate.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, startMinutes / 60)
                    set(Calendar.MINUTE, startMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val end = (classDate.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, endMinutes / 60)
                    set(Calendar.MINUTE, endMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (endMinutes <= startMinutes) add(Calendar.DAY_OF_YEAR, 1)
                }
                when {
                    start.timeInMillis <= nowMillis && end.timeInMillis > nowMillis ->
                        ScheduleStatus(item, ScheduleState.CURRENT, ((end.timeInMillis - nowMillis) / 60_000L).toInt())
                    start.timeInMillis >= nowMillis ->
                        ScheduleStatus(item, ScheduleState.NEXT, ((start.timeInMillis - nowMillis) / 60_000L).toInt())
                    else -> null
                }
            }
    }
    candidates.firstOrNull { it.state == ScheduleState.CURRENT }?.let { return it }
    candidates.filter { it.state == ScheduleState.NEXT }.minByOrNull { it.minutes }?.let { return it }
    val todayName = routineDayNames[now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY].second
    val todayKey = routineDateKey(now.time)
    val hadClassesToday = items.any { item ->
        item.dayOfWeek.equals(todayName, true) && (item.specificDate == null || item.specificDate == todayKey) &&
            parseRoutineTime(item.startTime) != null && parseRoutineTime(item.endTime) != null
    }
    return ScheduleStatus(null, if (hadClassesToday) ScheduleState.COMPLETED else ScheduleState.NONE)
}

internal fun routineItemStatus(item: RoutineItem, classDateMillis: Long, nowMillis: Long): ScheduleState {
    val startMinutes = parseRoutineTime(item.startTime) ?: return ScheduleState.NONE
    val endMinutes = parseRoutineTime(item.endTime) ?: return ScheduleState.NONE
    val date = Calendar.getInstance().apply { timeInMillis = classDateMillis }
    val start = (date.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, startMinutes / 60)
        set(Calendar.MINUTE, startMinutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (date.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, endMinutes / 60)
        set(Calendar.MINUTE, endMinutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (endMinutes <= startMinutes) add(Calendar.DAY_OF_YEAR, 1)
    }
    return when {
        nowMillis < start.timeInMillis -> ScheduleState.NEXT
        nowMillis < end.timeInMillis -> ScheduleState.CURRENT
        else -> ScheduleState.COMPLETED
    }
}

internal fun formatRoutineTime(value: String): String {
    val minutes = parseRoutineTime(value) ?: return value.trim().ifBlank { "Time unavailable" }
    val hour = minutes / 60
    val minute = minutes % 60
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%02d:%02d %s".format(displayHour, minute, suffix)
}

internal fun formatDuration(startTime: String, endTime: String): String? {
    val start = parseRoutineTime(startTime) ?: return null
    val end = parseRoutineTime(endTime) ?: return null
    val duration = if (end >= start) end - start else end + 24 * 60 - start
    if (duration <= 0) return null
    return if (duration >= 60) "${duration / 60}h ${duration % 60}m" else "$duration min"
}

internal fun dateForRoutineDay(weekStart: Date, dayIndex: Int): Date =
    Calendar.getInstance().apply {
        time = weekStart
        add(Calendar.DAY_OF_YEAR, dayIndex)
    }.time

internal fun startOfCurrentWeek(): Date =
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

internal fun routineDateKey(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)

internal data class RoutineItem(
    val id: Long,
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val room: String,
    val building: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val kind: String,
    val isAcademic: Boolean,
    val isDailyRoutine: Boolean = false,
    val specificDate: String? = null,
    val academicClass: AcademicClassEntity? = null
)

internal fun routineLocationLabel(item: RoutineItem): String? =
    listOf(item.room.trim(), item.building.trim()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

internal fun formatScheduleCountdown(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
