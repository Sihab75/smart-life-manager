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
        // Study score
        val studyPercent = if (targetStudyMinutes > 0) {
            ((actualStudyMinutes.toDouble() / targetStudyMinutes.toDouble()) * 100).coerceAtMost(100.0).toInt()
        } else {
            if (actualStudyMinutes > 0) 100 else 80
        }

        // Task score
        val taskPercent = if (totalTasks > 0) {
            ((completedTasks.toDouble() / totalTasks.toDouble()) * 100).toInt()
        } else {
            100
        }

        // Routine score
        val routinePercent = if (totalRoutines > 0) {
            ((completedRoutines.toDouble() / totalRoutines.toDouble()) * 100).toInt()
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
