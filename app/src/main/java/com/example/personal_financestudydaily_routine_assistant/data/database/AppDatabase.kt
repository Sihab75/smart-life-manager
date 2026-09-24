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
        AssistantMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun studyGoalDao(): StudyGoalDao
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
                    .fallbackToDestructiveMigration()
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
                db.execSQL("DELETE FROM expenses")
                db.execSQL("DELETE FROM classes")
                db.execSQL("DELETE FROM tasks")
                db.execSQL("DELETE FROM daily_routines")
                db.execSQL("DELETE FROM study_sessions")
                db.execSQL("DELETE FROM study_goals")
                db.execSQL("DELETE FROM weekly_goals")
                db.execSQL("DELETE FROM daily_reports")
                db.execSQL("DELETE FROM weekly_reports")
                db.execSQL("DELETE FROM monthly_reports")
                db.execSQL("DELETE FROM notifications")
                db.execSQL("DELETE FROM activity_logs")
            }

        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE user_settings SET userName = 'Md. Korimul Jaman' WHERE id = 1")
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
