package com.example.personal_financestudydaily_routine_assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val month = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())

    val settings: Flow<UserSettingsEntity?> = db.userSettingsDao().getUserSettings()
    val expenses: Flow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()
    val todayExpenses: Flow<List<ExpenseEntity>> = db.expenseDao().getExpensesByDate(today)
    val todayExpenseTotal: Flow<Double?> = db.expenseDao().getDailyTotal(today)
    val monthExpenseTotal: Flow<Double?> = db.expenseDao().getMonthlyTotal("%-$month")
    val studySessions: Flow<List<StudySessionEntity>> = db.studySessionDao().getAllStudySessions()
    val todayStudyMinutes: Flow<Int?> = db.studySessionDao().getDailyStudyDurationMinutes(today)
    val todayGoal: Flow<StudyGoalEntity?> = db.studyGoalDao().getStudyGoalForDate(today)
    val tasks: Flow<List<TaskEntity>> = db.taskDao().getAllTasks()
    val todayRoutines: Flow<List<DailyRoutineEntity>> = db.dailyRoutineDao().getRoutinesForDate(today)
    val classes: Flow<List<ClassEntity>> = db.classDao().getAllClasses()
    val weeklyGoals: Flow<List<WeeklyGoalEntity>> = db.weeklyGoalDao().getAllWeeklyGoals()
    val reports: Flow<List<DailyReportEntity>> = db.dailyReportDao().getAllDailyReports()
    val assistantMessages: Flow<List<AssistantMessageEntity>> = db.assistantMessageDao().getAllMessages()

    fun saveAssistantMessage(role: String, content: String) = viewModelScope.launch {
        db.assistantMessageDao().insertMessage(AssistantMessageEntity(role = role, content = content))
    }

    fun addExpense(amount: Double, category: String, note: String, paymentMethod: String) = viewModelScope.launch {
        db.expenseDao().insertExpense(
            ExpenseEntity(
                amount = amount,
                category = category,
                dateMillis = System.currentTimeMillis(),
                dateString = today,
                timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                note = note,
                paymentMethod = paymentMethod
            )
        )
    }

    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch { db.expenseDao().deleteExpense(expense) }

    fun addStudySession(subject: String, minutes: Int, notes: String = "") = viewModelScope.launch {
        val end = System.currentTimeMillis()
        db.studySessionDao().insertStudySession(
            StudySessionEntity(subject = subject, durationMinutes = minutes, notes = notes,
                startTimestamp = end - minutes * 60_000L, endTimestamp = end, dateString = today)
        )
    }

    fun toggleTask(task: TaskEntity) = viewModelScope.launch {
        db.taskDao().updateTask(task.copy(isCompleted = !task.isCompleted))
    }

    fun addTask(title: String, description: String, priority: String) = viewModelScope.launch {
        db.taskDao().insertTask(
            TaskEntity(
                title = title,
                description = description,
                deadlineMillis = System.currentTimeMillis() + 86_400_000L,
                priority = priority
            )
        )
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch { db.taskDao().deleteTask(task) }

    fun setTodayStudyGoal(hours: Double) = viewModelScope.launch {
        db.studyGoalDao().insertOrUpdateStudyGoal(
            StudyGoalEntity(targetHours = hours, dateString = today)
        )
    }

    fun addClass(
        courseName: String,
        courseCode: String,
        teacher: String,
        room: String,
        dayOfWeek: String,
        startTime: String,
        endTime: String
    ) = viewModelScope.launch {
        db.classDao().insertClass(
            ClassEntity(
                courseName = courseName,
                courseCode = courseCode,
                teacher = teacher,
                room = room,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime
            )
        )
    }

    fun toggleRoutine(routine: DailyRoutineEntity) = viewModelScope.launch {
        db.dailyRoutineDao().updateRoutine(routine.copy(isCompleted = !routine.isCompleted))
    }
}
