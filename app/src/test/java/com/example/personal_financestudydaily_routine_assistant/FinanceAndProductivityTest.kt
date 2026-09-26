package com.example.personal_financestudydaily_routine_assistant

import com.example.personal_financestudydaily_routine_assistant.domain.calculator.ProductivityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FinanceAndProductivityTest {
    @Test
    fun financePeriodsStartAtCalendarBoundaries() {
        val instant = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .parse("2026-09-26 17:14")!!.time
        assertEquals("2026-09-26", expensePeriodStartDate("Day", instant))
        assertEquals("2026-09-01", expensePeriodStartDate("Month", instant))
        assertEquals("2026-01-01", expensePeriodStartDate("Year", instant))

        val calendar = Calendar.getInstance().apply { timeInMillis = instant }
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        assertEquals(startOfMonth, expensePeriodStartDate("Month", instant))
    }

    @Test
    fun weeklyFinancePeriodBeginsOnTheLocaleFirstDay() {
        val instant = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .parse("2026-09-26 17:14")!!.time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = instant
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7))
        }
        assertEquals(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time),
            expensePeriodStartDate("Week", instant)
        )
    }

    @Test
    fun productivityPercentagesStayWithinBoundsForInvalidCounts() {
        val result = ProductivityCalculator.calculate(
            targetStudyMinutes = 60,
            actualStudyMinutes = -30,
            totalTasks = 2,
            completedTasks = 9,
            totalRoutines = 4,
            completedRoutines = -1
        )
        assertEquals(0, result.studyGoalPercent)
        assertEquals(100, result.taskCompletionPercent)
        assertEquals(0, result.routineCompletionPercent)
        assertTrue(result.overallScore in 0..100)
    }

    @Test
    fun zeroStudyTargetDoesNotProduceAnInvalidPercentage() {
        val result = ProductivityCalculator.calculate(0, 0, 0, 0, 0, 0)
        assertEquals(80, result.studyGoalPercent)
        assertEquals(92, result.overallScore)
    }
}
