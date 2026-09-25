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
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY deadlineMillis ASC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)
}

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses WHERE isActive = 1 ORDER BY nextDueDate ASC")
    fun getActiveExpenses(): Flow<List<RecurringExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: RecurringExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: RecurringExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: RecurringExpenseEntity)
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
interface StudyManagementDao {
    @Query("SELECT * FROM study_courses ORDER BY createdAt DESC")
    fun getCourses(): Flow<List<StudyCourseEntity>>

    @Query("SELECT * FROM study_topics WHERE courseId = :courseId ORDER BY position ASC, id ASC")
    fun getTopics(courseId: Long): Flow<List<StudyTopicEntity>>

    @Query("SELECT * FROM study_notes WHERE courseId = :courseId ORDER BY updatedAt DESC")
    fun getNotes(courseId: Long): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_flashcards WHERE courseId = :courseId ORDER BY id DESC")
    fun getFlashcards(courseId: Long): Flow<List<StudyFlashcardEntity>>

    @Insert
    suspend fun insertCourse(course: StudyCourseEntity): Long

    @Insert
    suspend fun insertTopic(topic: StudyTopicEntity): Long

    @Update
    suspend fun updateTopic(topic: StudyTopicEntity)

    @Delete
    suspend fun deleteTopic(topic: StudyTopicEntity)

    @Insert
    suspend fun insertNote(note: StudyNoteEntity): Long

    @Delete
    suspend fun deleteNote(note: StudyNoteEntity)

    @Insert
    suspend fun insertFlashcard(card: StudyFlashcardEntity): Long

    @Update
    suspend fun updateFlashcard(card: StudyFlashcardEntity)
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
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAtMillis ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsOnce(): List<HabitEntity>

    @Query("SELECT * FROM habit_completions")
    fun getAllCompletions(): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString LIMIT 1")
    suspend fun getCompletion(habitId: Long, dateString: String): HabitCompletionEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString")
    suspend fun deleteCompletion(habitId: Long, dateString: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, deadlineMillis ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0")
    suspend fun getPendingTasksOnce(): List<TaskEntity>

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
interface CpProblemDao {
    @Query("SELECT * FROM cp_problems ORDER BY solvedDate DESC, id DESC")
    fun getAllProblems(): Flow<List<CpProblemEntity>>

    @Insert
    suspend fun insertProblem(problem: CpProblemEntity): Long

    @Query("SELECT COUNT(*) FROM cp_problems WHERE solvedDate = :date")
    suspend fun getSolvedCountForDate(date: String): Int

    @Delete
    suspend fun deleteProblem(problem: CpProblemEntity)
}

@Dao
interface CpGoalDao {
    @Query("SELECT * FROM cp_goals ORDER BY platform ASC")
    fun getAllGoals(): Flow<List<CpGoalEntity>>

    @Query("SELECT * FROM cp_goals")
    suspend fun getAllGoalsOnce(): List<CpGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: CpGoalEntity)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes")
    suspend fun getAllClassesOnce(): List<ClassEntity>

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
interface AcademicDao {
    @Query("SELECT * FROM academic_semesters ORDER BY isArchived ASC, startDate DESC")
    fun getSemesters(): Flow<List<AcademicSemesterEntity>>

    @Query("SELECT * FROM academic_classes WHERE semesterId = :semesterId ORDER BY dayOfWeek ASC, startTime ASC")
    fun getClasses(semesterId: Long): Flow<List<AcademicClassEntity>>

    @Query("SELECT * FROM academic_exams WHERE semesterId = :semesterId ORDER BY examDate ASC, startTime ASC")
    fun getExams(semesterId: Long): Flow<List<AcademicExamEntity>>

    @Query("SELECT * FROM academic_events WHERE semesterId = :semesterId ORDER BY date ASC, time ASC")
    fun getEvents(semesterId: Long): Flow<List<AcademicEventEntity>>
    @Query("SELECT * FROM academic_events ORDER BY date ASC, time ASC")
    fun getAllAcademicEvents(): Flow<List<AcademicEventEntity>>

    @Query("SELECT * FROM academic_classes")
    suspend fun getAllClassesOnce(): List<AcademicClassEntity>
    @Query("SELECT * FROM academic_classes ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllAcademicClasses(): Flow<List<AcademicClassEntity>>
    @Query("SELECT * FROM academic_exams ORDER BY examDate ASC, startTime ASC")
    fun getAllAcademicExams(): Flow<List<AcademicExamEntity>>

    @Query("SELECT * FROM academic_exams WHERE examDate >= :today ORDER BY examDate ASC, startTime ASC")
    suspend fun getUpcomingExams(today: String): List<AcademicExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSemester(item: AcademicSemesterEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(item: AcademicCourseEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(item: AcademicClassEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(item: AcademicExamEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(item: AcademicEventEntity): Long

    @Update
    suspend fun updateSemester(item: AcademicSemesterEntity)
    @Delete
    suspend fun deleteClass(item: AcademicClassEntity)
    @Delete
    suspend fun deleteExam(item: AcademicExamEntity)
    @Delete
    suspend fun deleteEvent(item: AcademicEventEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestampMillis DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

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
    @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC, id ASC")
    fun getMessages(conversationId: Long): Flow<List<AssistantMessageEntity>>

    @Query("SELECT * FROM assistant_messages ORDER BY createdAtMillis ASC, id ASC")
    fun getAllMessages(): Flow<List<AssistantMessageEntity>>

    @Insert
    suspend fun insertMessage(message: AssistantMessageEntity): Long

    @Query("DELETE FROM assistant_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: Long)
}

@Dao
interface AssistantConversationDao {
    @Query("SELECT * FROM assistant_conversations WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun getActiveConversations(): Flow<List<AssistantConversationEntity>>

    @Query("SELECT * FROM assistant_conversations")
    suspend fun getActiveConversationsOnce(): List<AssistantConversationEntity>

    @Query("SELECT * FROM assistant_conversations WHERE isArchived = 1 ORDER BY updatedAtMillis DESC")
    fun getArchivedConversations(): Flow<List<AssistantConversationEntity>>

    @Insert
    suspend fun insertConversation(conversation: AssistantConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: AssistantConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: AssistantConversationEntity)
}

@Dao
interface BatchItemDao {
    @Query("SELECT * FROM batch_items WHERE isPublished = 1 ORDER BY isImportant DESC, date ASC, createdAtMillis DESC")
    fun getPublishedItems(): Flow<List<BatchItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BatchItemEntity): Long

    @Delete
    suspend fun deleteItem(item: BatchItemEntity)
}

@Dao
interface BroadcastDao {
    @Query("SELECT * FROM student_contacts ORDER BY isActive DESC, studentName ASC")
    fun getContacts(): Flow<List<StudentContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: StudentContactEntity): Long

    @Update
    suspend fun updateContact(contact: StudentContactEntity)

    @Delete
    suspend fun deleteContact(contact: StudentContactEntity)

    @Query("SELECT * FROM recipient_groups ORDER BY name ASC")
    fun getRecipientGroups(): Flow<List<RecipientGroupEntity>>

    @Insert
    suspend fun insertRecipientGroup(group: RecipientGroupEntity): Long

    @Query("SELECT * FROM batch_announcements ORDER BY createdAtMillis DESC")
    fun getAnnouncements(): Flow<List<BatchAnnouncementEntity>>

    @Insert
    suspend fun insertAnnouncement(announcement: BatchAnnouncementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<AnnouncementRecipientEntity>)

    @Insert
    suspend fun insertAttempts(attempts: List<DeliveryAttemptEntity>)

    @Query("SELECT * FROM announcement_recipients WHERE announcementId = :announcementId")
    suspend fun getRecipients(announcementId: Long): List<AnnouncementRecipientEntity>

    @Query("SELECT * FROM delivery_attempts WHERE announcementId = :announcementId ORDER BY channel ASC, contactId ASC")
    fun getAttempts(announcementId: Long): Flow<List<DeliveryAttemptEntity>>
}

@Dao
interface TravelTripDao {
    @Query("SELECT * FROM travel_trips ORDER BY departureMillis ASC")
    fun getAllTrips(): Flow<List<TravelTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TravelTripEntity): Long

    @Delete
    suspend fun deleteTrip(trip: TravelTripEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAtMillis DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)
}
