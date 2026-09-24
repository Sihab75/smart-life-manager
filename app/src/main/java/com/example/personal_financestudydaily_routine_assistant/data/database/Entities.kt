package com.example.personal_financestudydaily_routine_assistant.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String, // Food, Transport, Education, Internet, Shopping, Health, Entertainment, Bills, Others
    val dateMillis: Long,
    val dateString: String, // YYYY-MM-DD
    val timeString: String, // HH:mm AM/PM
    val note: String = "",
    val paymentMethod: String = "Cash", // Cash, Card, Mobile Banking
    val receiptImageUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "Monthly" or specific category name like "Food"
    val amount: Double,
    val monthYear: String // MM-YYYY
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = true
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val courseCode: String = "",
    val durationMinutes: Int,
    val notes: String = "",
    val startTimestamp: Long,
    val endTimestamp: Long,
    val dateString: String // YYYY-MM-DD
)

@Entity(tableName = "study_goals")
data class StudyGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetHours: Double,
    val dateString: String, // YYYY-MM-DD
    val dividedSessionsJson: String = "" // Auto-scheduled breakdown
)

@Entity(tableName = "weekly_goals")
data class WeeklyGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, // e.g. "Study", "Coding", "CP Problems", "Books"
    val targetValue: Double,
    val completedValue: Double = 0.0,
    val unit: String = "hours", // hours, problems, chapters
    val category: String = "General",
    val startDateMillis: Long,
    val endDateMillis: Long
)

@Entity(tableName = "daily_routines")
data class DailyRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: String, // e.g. "08:00 AM"
    val endTime: String,   // e.g. "09:30 AM"
    val category: String, // Class, Study, Lunch, Coding, Exercise, Revision, Other
    val reminderEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val dateString: String // YYYY-MM-DD
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val deadlineMillis: Long,
    val priority: String = "Medium", // High, Medium, Low
    val category: String = "General",
    val isCompleted: Boolean = false,
    val reminderEnabled: Boolean = true
)

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: String, // Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday
    val startTime: String, // e.g. "10:00 AM"
    val endTime: String    // e.g. "11:30 AM"
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "class", "study", "budget", "report", "task"
    val timestampMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Study, Coding, Class, Exercise, Entertainment, Other
    val durationMinutes: Int,
    val title: String,
    val dateString: String, // YYYY-MM-DD
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_reports")
data class DailyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String, // YYYY-MM-DD
    val studyTargetMinutes: Int,
    val actualStudyMinutes: Int,
    val codingTargetMinutes: Int,
    val actualCodingMinutes: Int,
    val totalExpense: Double,
    val totalTasksCompleted: Int,
    val totalTasksPending: Int,
    val productivityScore: Int, // 0 to 100
    val reportJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDate: String,
    val totalExpense: Double,
    val avgDailyExpense: Double,
    val studyHours: Double,
    val codingHours: Double,
    val classHours: Double,
    val goalCompletionPercent: Double,
    val taskCompletionPercent: Double,
    val reportJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "monthly_reports")
data class MonthlyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monthYear: String, // MM-YYYY
    val totalExpense: Double,
    val topCategory: String,
    val totalStudyHours: Double,
    val goalCompletionPercent: Double,
    val reportJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val userName: String = "Md. Korimul Jaman",
    val currencySymbol: String = "৳", // Default BDT ৳
    val monthlyBudgetAmount: Double = 15000.0,
    val darkModeOption: String = "System", // System, Light, Dark
    val appLockEnabled: Boolean = false,
    val pinCode: String = "",
    val studyReminderEnabled: Boolean = true,
    val morningReminderEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false
)

@Entity(tableName = "assistant_messages")
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
