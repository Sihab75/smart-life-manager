package com.example.personal_financestudydaily_routine_assistant.domain.calculator

data class ProductivityBreakdown(
    val overallScore: Int,
    val studyGoalPercent: Int,
    val taskCompletionPercent: Int,
    val routineCompletionPercent: Int,
    val summaryMessage: String
)

object ProductivityCalculator {

    fun calculate(
        targetStudyMinutes: Int,
        actualStudyMinutes: Int,
        totalTasks: Int,
        completedTasks: Int,
        totalRoutines: Int,
        completedRoutines: Int
    ): ProductivityBreakdown {
        val safeTargetMinutes = targetStudyMinutes.coerceAtLeast(0)
        val safeActualMinutes = actualStudyMinutes.coerceAtLeast(0)
        val studyPercent = if (safeTargetMinutes > 0) {
            ((safeActualMinutes.toDouble() / safeTargetMinutes.toDouble()) * 100).coerceIn(0.0, 100.0).toInt()
        } else {
            if (safeActualMinutes > 0) 100 else 80
        }

        val taskPercent = if (totalTasks > 0) {
            ((completedTasks.coerceIn(0, totalTasks).toDouble() / totalTasks.toDouble()) * 100).toInt()
        } else {
            100
        }

        val routinePercent = if (totalRoutines > 0) {
            ((completedRoutines.coerceIn(0, totalRoutines).toDouble() / totalRoutines.toDouble()) * 100).toInt()
        } else {
            100
        }

        // Weighted Average: Study 40%, Tasks 30%, Routine 30%
        val weightedScore = (studyPercent * 0.40) + (taskPercent * 0.30) + (routinePercent * 0.30)
        val overallScore = weightedScore.toInt().coerceIn(0, 100)

        val summary = when {
            overallScore >= 85 -> "Outstanding productivity today! Keep up the great momentum! 🚀"
            overallScore >= 70 -> "Solid progress! You accomplished most of your targets. 👍"
            overallScore >= 50 -> "Fair day. Try to focus more on your core study targets tomorrow. 📚"
            else -> "Low productivity today. Re-evaluate your routine and start fresh tomorrow! 💪"
        }

        return ProductivityBreakdown(
            overallScore = overallScore,
            studyGoalPercent = studyPercent,
            taskCompletionPercent = taskPercent,
            routineCompletionPercent = routinePercent,
            summaryMessage = summary
        )
    }
}
