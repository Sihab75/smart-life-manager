package com.example.personal_financestudydaily_routine_assistant

import com.example.personal_financestudydaily_routine_assistant.data.database.AcademicClassEntity
import org.junit.Assert.*
import org.junit.Test

class AcademicScheduleUtilsTest {
    private fun item(id: Long, day: String = "Monday", start: String = "09:00 AM", end: String = "10:00 AM") =
        AcademicClassEntity(id, 1, "CSE $id", "Course $id", dayOfWeek = day, startTime = start, endTime = end)

    @Test fun dayOrderIsSundayThroughSaturday() {
        assertEquals(listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
            routineDayNames.sortedBy { dayOrder(it.second) }.map { it.second })
    }

    @Test fun timesSortChronologically() {
        val result = sortRoutineItems(listOf(
            RoutineItem(1, "A", "", "", "", "", "Monday", "10:00 AM", "11:00 AM", "Theory", true),
            RoutineItem(2, "B", "", "", "", "", "Monday", "08:30 AM", "09:30 AM", "Theory", true)
        ))
        assertEquals("B", result.first().courseCode)
    }

    @Test fun touchingClassesDoNotConflict() {
        assertTrue(detectScheduleConflicts(listOf(item(1), item(2, start = "10:00 AM"))).isEmpty())
    }

    @Test fun overlappingClassesConflict() {
        assertEquals(1, detectScheduleConflicts(listOf(item(1, end = "10:30 AM"), item(2, start = "10:00 AM"))).size)
    }

    @Test fun freePeriodIsDerivedFromMiddleGap() {
        val periods = calculateFreePeriods(listOf(item(1, start = "09:00 AM", end = "10:00 AM"), item(2, start = "11:30 AM", end = "12:30 PM")))
        assertEquals(90, periods.single().durationMinutes)
    }

    @Test fun malformedTimeIsIgnored() {
        assertNull(parseRoutineTime("not a time"))
        assertNull(parseRoutineTime("09:30 AM trailing text"))
        assertTrue(detectScheduleConflicts(listOf(item(1, start = "bad"), item(2))).isEmpty())
    }

    @Test fun nestedOverlapsDoNotCreateFalseFreePeriods() {
        val classes = listOf(
            item(1, start = "09:00 AM", end = "12:00 PM"),
            item(2, start = "10:00 AM", end = "11:00 AM"),
            item(3, start = "11:30 AM", end = "12:30 PM")
        )
        assertTrue(calculateFreePeriods(classes).isEmpty())
    }

    @Test fun scheduleStatusFindsCurrentAndNext() {
        val current = RoutineItem(1, "NOW", "", "", "", "", "Monday", "09:00 AM", "10:00 AM", "Theory", true)
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        assertEquals(ScheduleState.CURRENT, scheduleStatus(listOf(current), now).state)
    }

    @Test fun saturdayClassTransitionsFromUpcomingToLiveToCompleted() {
        val classItem = RoutineItem(1, "EEE 202", "Electrical Devices", "Lecturer", "A-607",
            "Admin Building", "Saturday", "09:00 AM", "11:00 AM", "Theory", true)
        fun localTime(hour: Int, minute: Int): Long =
            java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.SEPTEMBER, 26, hour, minute, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

        assertEquals(ScheduleState.NEXT, scheduleStatus(listOf(classItem), localTime(7, 14)).state)
        assertEquals(ScheduleState.CURRENT, scheduleStatus(listOf(classItem), localTime(9, 30)).state)
        assertEquals(ScheduleState.COMPLETED, routineItemStatus(classItem, localTime(11, 30), localTime(11, 30)))
    }

    @Test fun noClassesProducesNoneState() {
        assertEquals(ScheduleState.NONE, scheduleStatus(emptyList(), System.currentTimeMillis()).state)
    }

    @Test fun saturdayMorningClassIsUpcomingForSelectedDay() {
        val classItem = RoutineItem(1, "EEE 202", "Electrical Devices", "Lecturer", "", "",
            "Saturday", "09:00 AM", "11:00 AM", "Theory", true)
        val today = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.SEPTEMBER, 26, 7, 14, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(ScheduleState.NEXT, routineItemStatus(classItem, today, today))
    }

    @Test fun academicIdentityIsRetainedInRoutineItem() {
        val original = item(42, start = "01:30 PM", end = "04:30 PM").copy(
            section = "A", notes = "Bring report", reminderMinutes = 15, notificationsEnabled = false,
            building = "Admin", colorHex = "#123456"
        )
        val routine = RoutineItem(original.id, original.courseCode, original.courseName, original.teacher, original.room,
            original.building, original.dayOfWeek, original.startTime, original.endTime, original.kind, true, academicClass = original)
        assertSame(original, routine.academicClass)
        assertEquals(original, routine.academicClass)
    }
}
