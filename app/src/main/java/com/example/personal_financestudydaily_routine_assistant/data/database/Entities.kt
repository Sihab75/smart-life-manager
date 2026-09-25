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
    val subcategory: String = "",
    val ledger: String = "Personal",
    val isRecurring: Boolean = false,
    val recurrenceRule: String = "",
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
    val isDefault: Boolean = true,
    val parentName: String = ""
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val deadlineMillis: Long? = null,
    val colorHex: String = "#2E7D32"
)

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val ledger: String = "Personal",
    val frequency: String = "Monthly",
    val nextDueDate: String,
    val isActive: Boolean = true
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

@Entity(tableName = "study_courses")
data class StudyCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val targetProgress: Int = 0,
    val examDate: String = "",
    val colorHex: String = "#6750A4",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_topics")
data class StudyTopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val position: Int = 0
)

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val content: String,
    val pdfUri: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_flashcards")
data class StudyFlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val front: String,
    val back: String,
    val nextReviewDate: String,
    val isMastered: Boolean = false
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

@Entity(tableName = "cp_problems")
data class CpProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val problemName: String,
    val contestName: String = "",
    val problemRating: Int? = null,
    val difficulty: String = "",
    val topic: String = "",
    val solvedDate: String,
    val attempts: Int = 1,
    val editorialLink: String = "",
    val isUpsolved: Boolean = false
)

@Entity(tableName = "cp_goals")
data class CpGoalEntity(
    @PrimaryKey val platform: String,
    val weeklyTarget: Int = 0,
    val monthlyTarget: Int = 0
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

@Entity(tableName = "academic_semesters")
data class AcademicSemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val endDate: String,
    val isArchived: Boolean = false
)

@Entity(tableName = "academic_courses")
data class AcademicCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val code: String,
    val name: String,
    val teacher: String = "",
    val section: String = "",
    val credits: Double = 0.0,
    val notes: String = ""
)

@Entity(tableName = "academic_classes")
data class AcademicClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val courseCode: String,
    val courseName: String,
    val teacher: String = "",
    val section: String = "",
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String = "",
    val building: String = "",
    val kind: String = "Theory",
    val colorHex: String = "#6750A4",
    val notes: String = "",
    val reminderMinutes: Int = 30,
    val notificationsEnabled: Boolean = true
)

@Entity(tableName = "academic_exams")
data class AcademicExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val courseCode: String,
    val courseName: String,
    val examType: String = "Final",
    val examDate: String,
    val startTime: String,
    val endTime: String = "",
    val room: String = "",
    val seatNumber: String = "",
    val syllabus: String = "",
    val notes: String = "",
    val preparationProgress: Int = 0,
    val reminderEnabled: Boolean = true
)

@Entity(tableName = "academic_events")
data class AcademicEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val title: String,
    val type: String = "Other",
    val date: String,
    val time: String = "",
    val location: String = "",
    val notes: String = "",
    val taskId: Long? = null
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
    val cloudSyncEnabled: Boolean = false,
    val isClassRepresentative: Boolean = false
)

@Entity(tableName = "assistant_messages")
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long = 1,
    val role: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "assistant_conversations")
data class AssistantConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New chat",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_items")
data class BatchItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val details: String = "",
    val courseCode: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val postedBy: String = "",
    val isPublished: Boolean = true,
    val isImportant: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "student_contacts")
data class StudentContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentName: String,
    val studentId: String,
    val section: String = "",
    val phoneNumber: String = "",
    val whatsappAvailable: Boolean = false,
    val messengerAvailable: Boolean = false,
    val isActive: Boolean = true,
    val preferredChannel: String = "In-App",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipient_groups")
data class RecipientGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactIds: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_announcements")
data class BatchAnnouncementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val type: String,
    val courseCode: String = "",
    val date: String = "",
    val time: String = "",
    val recipientMode: String = "Entire batch",
    val channels: String = "In-App",
    val recipientCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "announcement_recipients",
    primaryKeys = ["announcementId", "contactId"]
)
data class AnnouncementRecipientEntity(
    val announcementId: Long,
    val contactId: Long
)

@Entity(tableName = "delivery_attempts")
data class DeliveryAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val announcementId: Long,
    val contactId: Long,
    val channel: String,
    val status: String = "Pending",
    val message: String = "",
    val attemptedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "travel_trips")
data class TravelTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val travelDate: String, // YYYY-MM-DD
    val transportType: String, // Train, Bus, Flight
    val serviceName: String = "",
    val departureTime: String,
    val arrivalTime: String,
    val departureStation: String,
    val arrivalStation: String,
    val seatNumber: String = "",
    val coachNumber: String = "",
    val bookingReference: String = "",
    val ticketInformation: String = "",
    val ticketUri: String = "",
    val departureMillis: Long,
    val reminderEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Other",
    val reminderEnabled: Boolean = true,
    val reminderTime: String = "08:00 AM",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "dateString"]
)
data class HabitCompletionEntity(
    val habitId: Long,
    val dateString: String,
    val completedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val uri: String,
    val extractedText: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
