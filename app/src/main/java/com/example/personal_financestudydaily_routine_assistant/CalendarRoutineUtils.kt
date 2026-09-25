package com.example.personal_financestudydaily_routine_assistant

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(input)
        }.getOrNull() ?: return@forEach
        val calendar = Calendar.getInstance().apply { time = parsed }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    return null
}

internal fun sortRoutineItems(items: List<RoutineItem>): List<RoutineItem> =
    items.sortedWith(compareBy<RoutineItem>({ parseRoutineTime(it.startTime) == null }, { parseRoutineTime(it.startTime) ?: Int.MAX_VALUE }, { it.courseCode }))

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
    val specificDate: String? = null
)
