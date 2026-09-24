package com.example.personal_financestudydaily_routine_assistant.domain.calculator

import com.example.personal_financestudydaily_routine_assistant.data.database.ClassEntity

data class StudySessionSlot(
    val sessionName: String,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val durationMinutes: Int
)

data class WeeklyTimeAllocation(
    val dayOfWeek: String,
    val classCount: Int,
    val availableHours: Double,
    val allocatedStudyHours: Double,
    val slots: List<StudySessionSlot>
)

object SmartTimeCalculator {

    /**
     * Given target study hours for today (e.g. 4.0 hours), automatically breaks down into 2-3 sessions
     * based on typical available slots (10:00 AM, 03:00 PM, 09:00 PM).
     */
    fun calculateDailyStudyPlan(targetHours: Double, primarySubject: String = "Core Subject"): List<StudySessionSlot> {
        val totalMinutes = (targetHours * 60).toInt()
        if (totalMinutes <= 0) return emptyList()

        val slots = mutableListOf<StudySessionSlot>()
        if (totalMinutes <= 90) {
            slots.add(
                StudySessionSlot(
                    sessionName = "Session 1",
                    startTime = "10:00 AM",
                    endTime = formatEndTime("10:00 AM", totalMinutes),
                    subject = primarySubject,
                    durationMinutes = totalMinutes
                )
            )
        } else if (totalMinutes <= 180) {
            val half = totalMinutes / 2
            slots.add(
                StudySessionSlot(
                    sessionName = "Session 1: Morning Focus",
                    startTime = "10:00 AM",
                    endTime = formatEndTime("10:00 AM", half),
                    subject = primarySubject,
                    durationMinutes = half
                )
            )
            slots.add(
                StudySessionSlot(
                    sessionName = "Session 2: Evening Practice",
                    startTime = "08:00 PM",
                    endTime = formatEndTime("08:00 PM", totalMinutes - half),
                    subject = "Revision & CP",
                    durationMinutes = totalMinutes - half
                )
            )
        } else {
            val session1 = (totalMinutes * 0.4).toInt()
            val session2 = (totalMinutes * 0.35).toInt()
            val session3 = totalMinutes - session1 - session2

            slots.add(
                StudySessionSlot(
                    sessionName = "Session 1: Morning Core",
                    startTime = "10:00 AM",
                    endTime = formatEndTime("10:00 AM", session1),
                    subject = primarySubject,
                    durationMinutes = session1
                )
            )
            slots.add(
                StudySessionSlot(
                    sessionName = "Session 2: Afternoon Lab/CP",
                    startTime = "03:00 PM",
                    endTime = formatEndTime("03:00 PM", session2),
                    subject = "Competitive Programming",
                    durationMinutes = session2
                )
            )
            slots.add(
                StudySessionSlot(
                    sessionName = "Session 3: Night Revision",
                    startTime = "09:00 PM",
                    endTime = formatEndTime("09:00 PM", session3),
                    subject = "Revision & Notes",
                    durationMinutes = session3
                )
            )
        }

        return slots
    }

    /**
     * Calculates realistic daily study allocations for a weekly target (e.g. 20 hours).
     * Takes into account class load per day so heavy class days get less study and light days get more.
     */
    fun calculateWeeklySchedule(
        targetWeeklyHours: Double,
        classes: List<ClassEntity>
    ): List<WeeklyTimeAllocation> {
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val classesByDay = classes.groupBy { it.dayOfWeek }

        // Calculate available hours per day (assuming 8 hours max free time per day minus class duration)
        val dayAvailabilities = days.map { day ->
            val dayClasses = classesByDay[day] ?: emptyList()
            val classHours = dayClasses.size * 1.5 // estimate 1.5 hours per class
            val available = (8.0 - classHours).coerceAtLeast(1.0)
            Triple(day, dayClasses.size, available)
        }

        val totalAvailable = dayAvailabilities.sumOf { it.third }

        return dayAvailabilities.map { (day, classCount, available) ->
            val allocatedHours = if (totalAvailable > 0) {
                ((available / totalAvailable) * targetWeeklyHours).let {
                    Math.round(it * 10) / 10.0 // round to 1 decimal
                }
            } else {
                targetWeeklyHours / 7
            }

            val slots = calculateDailyStudyPlan(allocatedHours, "Main Course")
            WeeklyTimeAllocation(
                dayOfWeek = day,
                classCount = classCount,
                availableHours = available,
                allocatedStudyHours = allocatedHours,
                slots = slots
            )
        }
    }

    private fun formatEndTime(startTime: String, durationMinutes: Int): String {
        return try {
            val parts = startTime.split(" ", ":")
            var hour = parts[0].toInt()
            val min = parts[1].toInt()
            val amPm = parts[2]

            if (amPm.uppercase() == "PM" && hour < 12) hour += 12
            if (amPm.uppercase() == "AM" && hour == 12) hour = 0

            var totalMins = hour * 60 + min + durationMinutes
            var endHour = (totalMins / 60) % 24
            val endMin = totalMins % 60

            val endAmPm = if (endHour >= 12) "PM" else "AM"
            if (endHour > 12) endHour -= 12
            if (endHour == 0) endHour = 12

            String.format("%02d:%02d %s", endHour, endMin, endAmPm)
        } catch (e: Exception) {
            "$startTime + ${durationMinutes}m"
        }
    }
}
