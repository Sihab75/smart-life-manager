package com.example.personal_financestudydaily_routine_assistant.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        UserEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        StudySessionEntity::class,
        StudyGoalEntity::class,
        StudyCourseEntity::class,
        StudyTopicEntity::class,
        StudyNoteEntity::class,
        StudyFlashcardEntity::class,
        WeeklyGoalEntity::class,
        DailyRoutineEntity::class,
        TaskEntity::class,
        ClassEntity::class,
        NotificationEntity::class,
        ActivityLogEntity::class,
        DailyReportEntity::class,
        WeeklyReportEntity::class,
        MonthlyReportEntity::class,
        UserSettingsEntity::class,
        AssistantMessageEntity::class,
        AssistantConversationEntity::class,
        SavingsGoalEntity::class,
        RecurringExpenseEntity::class,
        CpProblemEntity::class,
        CpGoalEntity::class,
        BatchItemEntity::class,
        StudentContactEntity::class, RecipientGroupEntity::class, BatchAnnouncementEntity::class,
        AnnouncementRecipientEntity::class, DeliveryAttemptEntity::class,
        TravelTripEntity::class
        ,AcademicSemesterEntity::class, AcademicCourseEntity::class, AcademicClassEntity::class,
        AcademicExamEntity::class, AcademicEventEntity::class
        ,HabitEntity::class, HabitCompletionEntity::class
        ,DocumentEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun studyGoalDao(): StudyGoalDao
    abstract fun studyManagementDao(): StudyManagementDao
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun dailyRoutineDao(): DailyRoutineDao
    abstract fun taskDao(): TaskDao
    abstract fun classDao(): ClassDao
    abstract fun notificationDao(): NotificationDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun dailyReportDao(): DailyReportDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun monthlyReportDao(): MonthlyReportDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun assistantMessageDao(): AssistantMessageDao
    abstract fun assistantConversationDao(): AssistantConversationDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun cpProblemDao(): CpProblemDao
    abstract fun cpGoalDao(): CpGoalDao
    abstract fun batchItemDao(): BatchItemDao
    abstract fun broadcastDao(): BroadcastDao
    abstract fun travelTripDao(): TravelTripDao
    abstract fun academicDao(): AcademicDao
    abstract fun habitDao(): HabitDao
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_life_manager_db"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .addMigrations(MIGRATION_12_13)
                    .addMigrations(MIGRATION_13_14)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedDefaultData(database)
                    }
                }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 did not require destructive data changes.
            }

        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve the user's existing profile while advancing the schema.
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assistant_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE expenses ADD COLUMN subcategory TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE expenses ADD COLUMN ledger TEXT NOT NULL DEFAULT 'Personal'")
                    db.execSQL("ALTER TABLE expenses ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE expenses ADD COLUMN recurrenceRule TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE categories ADD COLUMN parentName TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS savings_goals (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, targetAmount REAL NOT NULL, savedAmount REAL NOT NULL, " +
                            "deadlineMillis INTEGER, colorHex TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS recurring_expenses (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, " +
                            "amount REAL NOT NULL, category TEXT NOT NULL, ledger TEXT NOT NULL, " +
                            "frequency TEXT NOT NULL, nextDueDate TEXT NOT NULL, isActive INTEGER NOT NULL)"
                    )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS cp_problems (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, platform TEXT NOT NULL, " +
                            "problemName TEXT NOT NULL, contestName TEXT NOT NULL, problemRating INTEGER, " +
                            "difficulty TEXT NOT NULL, topic TEXT NOT NULL, solvedDate TEXT NOT NULL, " +
                            "attempts INTEGER NOT NULL, editorialLink TEXT NOT NULL, isUpsolved INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS cp_goals (" +
                            "platform TEXT NOT NULL PRIMARY KEY, weeklyTarget INTEGER NOT NULL, " +
                            "monthlyTarget INTEGER NOT NULL)"
                    )
                }
            }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE user_settings ADD COLUMN isClassRepresentative INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS batch_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, " +
                            "title TEXT NOT NULL, details TEXT NOT NULL, courseCode TEXT NOT NULL, " +
                            "date TEXT NOT NULL, time TEXT NOT NULL, location TEXT NOT NULL, " +
                            "postedBy TEXT NOT NULL, isPublished INTEGER NOT NULL, isImportant INTEGER NOT NULL, " +
                            "createdAtMillis INTEGER NOT NULL)"
                    )
                }
            }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS travel_trips (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, " +
                            "travelDate TEXT NOT NULL, transportType TEXT NOT NULL, serviceName TEXT NOT NULL, " +
                            "departureTime TEXT NOT NULL, arrivalTime TEXT NOT NULL, departureStation TEXT NOT NULL, " +
                            "arrivalStation TEXT NOT NULL, seatNumber TEXT NOT NULL, coachNumber TEXT NOT NULL, " +
                            "bookingReference TEXT NOT NULL, ticketInformation TEXT NOT NULL, ticketUri TEXT NOT NULL, " +
                            "departureMillis INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL)"
                    )
                }

            }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_semesters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, isArchived INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, semesterId INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, teacher TEXT NOT NULL, section TEXT NOT NULL, credits REAL NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_classes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, semesterId INTEGER NOT NULL, courseCode TEXT NOT NULL, courseName TEXT NOT NULL, teacher TEXT NOT NULL, section TEXT NOT NULL, dayOfWeek TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, room TEXT NOT NULL, building TEXT NOT NULL, kind TEXT NOT NULL, colorHex TEXT NOT NULL, notes TEXT NOT NULL, reminderMinutes INTEGER NOT NULL, notificationsEnabled INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_exams (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, semesterId INTEGER NOT NULL, courseCode TEXT NOT NULL, courseName TEXT NOT NULL, examType TEXT NOT NULL, examDate TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, room TEXT NOT NULL, seatNumber TEXT NOT NULL, syllabus TEXT NOT NULL, notes TEXT NOT NULL, preparationProgress INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, semesterId INTEGER NOT NULL, title TEXT NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, location TEXT NOT NULL, notes TEXT NOT NULL, taskId INTEGER)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, isPinned INTEGER NOT NULL, isArchived INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE assistant_messages ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("INSERT OR IGNORE INTO assistant_conversations (id, title, isPinned, isArchived, updatedAtMillis) VALUES (1, 'General chat', 0, 0, strftime('%s','now') * 1000)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS study_courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, targetProgress INTEGER NOT NULL, examDate TEXT NOT NULL, colorHex TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_topics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, title TEXT NOT NULL, isCompleted INTEGER NOT NULL, position INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, pdfUri TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, nextReviewDate TEXT NOT NULL, isMastered INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, reminderTime TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS habit_completions (habitId INTEGER NOT NULL, dateString TEXT NOT NULL, completedAtMillis INTEGER NOT NULL, PRIMARY KEY(habitId, dateString))")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS documents (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, " +
                        "uri TEXT NOT NULL, extractedText TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, " +
                        "updatedAtMillis INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS student_contacts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, studentName TEXT NOT NULL, studentId TEXT NOT NULL, section TEXT NOT NULL, phoneNumber TEXT NOT NULL, whatsappAvailable INTEGER NOT NULL, messengerAvailable INTEGER NOT NULL, isActive INTEGER NOT NULL, preferredChannel TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recipient_groups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, contactIds TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS batch_announcements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, type TEXT NOT NULL, courseCode TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, recipientMode TEXT NOT NULL, channels TEXT NOT NULL, recipientCount INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS announcement_recipients (announcementId INTEGER NOT NULL, contactId INTEGER NOT NULL, PRIMARY KEY(announcementId, contactId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS delivery_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, announcementId INTEGER NOT NULL, contactId INTEGER NOT NULL, channel TEXT NOT NULL, status TEXT NOT NULL, message TEXT NOT NULL, attemptedAtMillis INTEGER NOT NULL)")
            }
        }

        suspend fun seedDefaultData(db: AppDatabase) {
            // Seed User Settings
            db.userSettingsDao().updateUserSettings(
                UserSettingsEntity(
                    id = 1,
                    userName = "Md. Korimul Jaman",
                    currencySymbol = "৳",
                    monthlyBudgetAmount = 15000.0,
                    darkModeOption = "System",
                    appLockEnabled = false,
                    pinCode = "",
                    studyReminderEnabled = true,
                    morningReminderEnabled = true
                )
            )
            if (db.assistantConversationDao().getActiveConversationsOnce().isEmpty()) {
                db.assistantConversationDao().insertConversation(
                    AssistantConversationEntity(title = "General chat")
                )
            }

            // Seed Categories
            val defaultCategories = listOf(
                CategoryEntity(name = "Food", iconName = "fastfood", colorHex = "#FF5722"),
                CategoryEntity(name = "Transport", iconName = "directions_bus", colorHex = "#2196F3"),
                CategoryEntity(name = "Education", iconName = "school", colorHex = "#9C27B0"),
                CategoryEntity(name = "Internet", iconName = "wifi", colorHex = "#00BCD4"),
                CategoryEntity(name = "Shopping", iconName = "shopping_cart", colorHex = "#E91E63"),
                CategoryEntity(name = "Health", iconName = "local_hospital", colorHex = "#4CAF50"),
                CategoryEntity(name = "Entertainment", iconName = "movie", colorHex = "#FF9800"),
                CategoryEntity(name = "Bills", iconName = "receipt", colorHex = "#607D8B"),
                CategoryEntity(name = "Others", iconName = "more_horiz", colorHex = "#795548")
            )
            db.categoryDao().insertCategories(defaultCategories)

            // Seed Budgets
            val currentMonthYear = getCurrentMonthYearString()
            db.budgetDao().insertOrUpdateBudget(BudgetEntity(category = "Monthly", amount = 15000.0, monthYear = currentMonthYear))
            db.budgetDao().insertOrUpdateBudget(BudgetEntity(category = "Food", amount = 5000.0, monthYear = currentMonthYear))
            db.budgetDao().insertOrUpdateBudget(BudgetEntity(category = "Transport", amount = 2000.0, monthYear = currentMonthYear))
            db.budgetDao().insertOrUpdateBudget(BudgetEntity(category = "Education", amount = 3000.0, monthYear = currentMonthYear))

        }

        private fun getCurrentMonthYearString(): String {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
