package com.example.personal_financestudydaily_routine_assistant.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE dateString = :dateString ORDER BY timeString ASC")
    fun getExpensesByDate(dateString: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY dateMillis DESC")
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE dateString = :dateString")
    fun getDailyTotal(dateString: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE dateString LIKE :monthYearPattern")
    fun getMonthlyTotal(monthYearPattern: String): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE note LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    fun searchExpenses(query: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category AND monthYear = :monthYear LIMIT 1")
    suspend fun getBudgetByCategory(category: String, monthYear: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: BudgetEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startTimestamp DESC")
    fun getAllStudySessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE dateString = :dateString")
    fun getStudySessionsByDate(dateString: String): Flow<List<StudySessionEntity>>

    @Query("SELECT SUM(durationMinutes) FROM study_sessions WHERE dateString = :dateString")
    fun getDailyStudyDurationMinutes(dateString: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySession(session: StudySessionEntity): Long

    @Delete
    suspend fun deleteStudySession(session: StudySessionEntity)
}

@Dao
interface StudyGoalDao {
    @Query("SELECT * FROM study_goals WHERE dateString = :dateString LIMIT 1")
    fun getStudyGoalForDate(dateString: String): Flow<StudyGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStudyGoal(goal: StudyGoalEntity)
}

@Dao
interface WeeklyGoalDao {
    @Query("SELECT * FROM weekly_goals ORDER BY endDateMillis ASC")
    fun getAllWeeklyGoals(): Flow<List<WeeklyGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyGoal(goal: WeeklyGoalEntity): Long

    @Update
    suspend fun updateWeeklyGoal(goal: WeeklyGoalEntity)

    @Delete
    suspend fun deleteWeeklyGoal(goal: WeeklyGoalEntity)
}

@Dao
interface DailyRoutineDao {
    @Query("SELECT * FROM daily_routines WHERE dateString = :dateString ORDER BY startTime ASC")
    fun getRoutinesForDate(dateString: String): Flow<List<DailyRoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: DailyRoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: DailyRoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: DailyRoutineEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, deadlineMillis ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTasks(query: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getClassesForDay(dayOfWeek: String): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE courseName LIKE '%' || :query || '%' OR courseCode LIKE '%' || :query || '%'")
    fun searchClasses(query: String): Flow<List<ClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classEntity: ClassEntity): Long

    @Update
    suspend fun updateClass(classEntity: ClassEntity)

    @Delete
    suspend fun deleteClass(classEntity: ClassEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestampMillis DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs WHERE dateString = :dateString ORDER BY timestampMillis DESC")
    fun getActivityLogsForDate(dateString: String): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity): Long
}

@Dao
interface DailyReportDao {
    @Query("SELECT * FROM daily_reports ORDER BY createdAtMillis DESC")
    fun getAllDailyReports(): Flow<List<DailyReportEntity>>

    @Query("SELECT * FROM daily_reports WHERE dateString = :dateString LIMIT 1")
    suspend fun getReportForDate(dateString: String): DailyReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReport(report: DailyReportEntity): Long
}

@Dao
interface WeeklyReportDao {
    @Query("SELECT * FROM weekly_reports ORDER BY createdAtMillis DESC")
    fun getAllWeeklyReports(): Flow<List<WeeklyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyReport(report: WeeklyReportEntity): Long
}

@Dao
interface MonthlyReportDao {
    @Query("SELECT * FROM monthly_reports ORDER BY createdAtMillis DESC")
    fun getAllMonthlyReports(): Flow<List<MonthlyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyReport(report: MonthlyReportEntity): Long
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettingsDirect(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateUserSettings(settings: UserSettingsEntity)
}

@Dao
interface AssistantMessageDao {
    @Query("SELECT * FROM assistant_messages ORDER BY createdAtMillis ASC, id ASC")
    fun getAllMessages(): Flow<List<AssistantMessageEntity>>

    @Insert
    suspend fun insertMessage(message: AssistantMessageEntity): Long
}
