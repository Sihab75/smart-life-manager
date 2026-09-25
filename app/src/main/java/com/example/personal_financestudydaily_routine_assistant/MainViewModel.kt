package com.example.personal_financestudydaily_routine_assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_financestudydaily_routine_assistant.data.database.*
import com.example.personal_financestudydaily_routine_assistant.data.cloud.CloudUser
import com.example.personal_financestudydaily_routine_assistant.data.cloud.FirebaseCloudService
import com.example.personal_financestudydaily_routine_assistant.data.broadcast.BroadcastService
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val month = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date())
    private val monthDatePrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    private val workManager = WorkManager.getInstance(application)
    private val cloud = FirebaseCloudService(application)
    private val broadcastService = BroadcastService(db.broadcastDao(), db.notificationDao())
    private val _cloudUser = MutableStateFlow(cloud.currentUser)
    val cloudUser: StateFlow<CloudUser?> = _cloudUser

    val settings: Flow<UserSettingsEntity?> = db.userSettingsDao().getUserSettings()
    val expenses: Flow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()
    val todayExpenses: Flow<List<ExpenseEntity>> = db.expenseDao().getExpensesByDate(today)
    val todayExpenseTotal: Flow<Double?> = db.expenseDao().getDailyTotal(today)
    val monthExpenseTotal: Flow<Double?> = db.expenseDao().getMonthlyTotal("$monthDatePrefix-%")
    val studySessions: Flow<List<StudySessionEntity>> = db.studySessionDao().getAllStudySessions()
    val todayStudyMinutes: Flow<Int?> = db.studySessionDao().getDailyStudyDurationMinutes(today)
    val todayGoal: Flow<StudyGoalEntity?> = db.studyGoalDao().getStudyGoalForDate(today)
    val tasks: Flow<List<TaskEntity>> = db.taskDao().getAllTasks()
    val todayRoutines: Flow<List<DailyRoutineEntity>> = db.dailyRoutineDao().getRoutinesForDate(today)
    val classes: Flow<List<ClassEntity>> = db.classDao().getAllClasses()
    val weeklyGoals: Flow<List<WeeklyGoalEntity>> = db.weeklyGoalDao().getAllWeeklyGoals()
    val reports: Flow<List<DailyReportEntity>> = db.dailyReportDao().getAllDailyReports()
    val assistantMessages: Flow<List<AssistantMessageEntity>> = db.assistantMessageDao().getAllMessages()
    val assistantConversations: Flow<List<AssistantConversationEntity>> = db.assistantConversationDao().getActiveConversations()
    val archivedAssistantConversations: Flow<List<AssistantConversationEntity>> = db.assistantConversationDao().getArchivedConversations()
    fun assistantMessages(conversationId: Long): Flow<List<AssistantMessageEntity>> = db.assistantMessageDao().getMessages(conversationId)
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().getAllCategories()
    val budgets: Flow<List<BudgetEntity>> = db.budgetDao().getBudgetsForMonth(month)
    val savingsGoals: Flow<List<SavingsGoalEntity>> = db.savingsGoalDao().getAllGoals()
    val recurringExpenses: Flow<List<RecurringExpenseEntity>> = db.recurringExpenseDao().getActiveExpenses()
    val cpProblems: Flow<List<CpProblemEntity>> = db.cpProblemDao().getAllProblems()
    val cpGoals: Flow<List<CpGoalEntity>> = db.cpGoalDao().getAllGoals()
    val batchItems: Flow<List<BatchItemEntity>> = db.batchItemDao().getPublishedItems()
    val travelTrips: Flow<List<TravelTripEntity>> = db.travelTripDao().getAllTrips()
    val notifications: Flow<List<NotificationEntity>> = db.notificationDao().getAllNotifications()
    val unreadNotifications: Flow<Int> = db.notificationDao().getUnreadCount()
    val academicSemesters: Flow<List<AcademicSemesterEntity>> = db.academicDao().getSemesters()
    val academicClasses: Flow<List<AcademicClassEntity>> = db.academicDao().getAllAcademicClasses()
    val academicExams: Flow<List<AcademicExamEntity>> = db.academicDao().getAllAcademicExams()
    val academicDaoEvents: Flow<List<AcademicEventEntity>> = db.academicDao().getAllAcademicEvents()
    val studyCourses: Flow<List<StudyCourseEntity>> = db.studyManagementDao().getCourses()
    val habits: Flow<List<HabitEntity>> = db.habitDao().getAllHabits()
    val habitCompletions: Flow<List<HabitCompletionEntity>> = db.habitDao().getAllCompletions()
    val documents: Flow<List<DocumentEntity>> = db.documentDao().getAllDocuments()
    val studentContacts: Flow<List<StudentContactEntity>> = db.broadcastDao().getContacts()
    val recipientGroups: Flow<List<RecipientGroupEntity>> = db.broadcastDao().getRecipientGroups()
    val broadcastHistory: Flow<List<BatchAnnouncementEntity>> = db.broadcastDao().getAnnouncements()
    fun deliveryAttempts(announcementId: Long): Flow<List<DeliveryAttemptEntity>> =
        db.broadcastDao().getAttempts(announcementId)

    fun saveStudentContact(contact: StudentContactEntity) = viewModelScope.launch {
        if (contact.studentName.isNotBlank() && contact.studentId.isNotBlank()) {
            if (contact.id == 0L) db.broadcastDao().insertContact(contact)
            else db.broadcastDao().updateContact(contact)
        }
    }

    fun deleteStudentContact(contact: StudentContactEntity) = viewModelScope.launch {
        db.broadcastDao().deleteContact(contact)
    }

    fun saveRecipientGroup(name: String, contactIds: List<Long>) = viewModelScope.launch {
        if (name.isNotBlank() && contactIds.isNotEmpty()) {
            db.broadcastDao().insertRecipientGroup(
                RecipientGroupEntity(name = name.trim(), contactIds = contactIds.joinToString(","))
            )
        }
    }

    fun publishBroadcast(
        type: String,
        title: String,
        details: String,
        courseCode: String,
        date: String,
        time: String,
        recipientMode: String,
        channels: List<String>,
        recipients: List<StudentContactEntity>,
        postedBy: String,
        isImportant: Boolean
    ) = viewModelScope.launch {
        val announcement = BatchAnnouncementEntity(
            title = title.trim(), details = details.trim(), type = type,
            courseCode = courseCode.trim(), date = date.trim(), time = time.trim(),
            recipientMode = recipientMode, channels = channels.joinToString(","),
            recipientCount = recipients.size
        )
        broadcastService.publish(announcement, recipients)
        db.batchItemDao().insertItem(
            BatchItemEntity(
                type = type, title = title.trim(), details = details.trim(),
                courseCode = courseCode.trim(), date = date.trim(), time = time.trim(),
                postedBy = postedBy.trim(), isImportant = isImportant
            )
        )
    }

    fun addDocument(document: DocumentEntity) = viewModelScope.launch {
        db.documentDao().insertDocument(document)
    }

    fun deleteDocument(document: DocumentEntity) = viewModelScope.launch {
        db.documentDao().deleteDocument(document)
    }

    fun markAllNotificationsAsRead() = viewModelScope.launch {
        db.notificationDao().markAllAsRead()
    }

    fun studyTopics(courseId: Long): Flow<List<StudyTopicEntity>> = db.studyManagementDao().getTopics(courseId)
    fun studyNotes(courseId: Long): Flow<List<StudyNoteEntity>> = db.studyManagementDao().getNotes(courseId)
    fun studyFlashcards(courseId: Long): Flow<List<StudyFlashcardEntity>> = db.studyManagementDao().getFlashcards(courseId)

    fun addStudyCourse(code: String, name: String, examDate: String) = viewModelScope.launch {
        db.studyManagementDao().insertCourse(StudyCourseEntity(code = code.trim(), name = name.trim(), examDate = examDate.trim()))
    }

    fun addStudyTopic(courseId: Long, title: String) = viewModelScope.launch {
        db.studyManagementDao().insertTopic(StudyTopicEntity(courseId = courseId, title = title.trim()))
    }

    fun toggleStudyTopic(topic: StudyTopicEntity) = viewModelScope.launch {
        db.studyManagementDao().updateTopic(topic.copy(isCompleted = !topic.isCompleted))
    }

    fun addStudyNote(courseId: Long, title: String, content: String, pdfUri: String = "") = viewModelScope.launch {
        db.studyManagementDao().insertNote(StudyNoteEntity(courseId = courseId, title = title.trim(), content = content.trim(), pdfUri = pdfUri))
    }

    fun addStudyFlashcard(courseId: Long, front: String, back: String) = viewModelScope.launch {
        db.studyManagementDao().insertFlashcard(
            StudyFlashcardEntity(courseId = courseId, front = front.trim(), back = back.trim(), nextReviewDate = today)
        )
    }

    fun toggleFlashcard(card: StudyFlashcardEntity) = viewModelScope.launch {
        db.studyManagementDao().updateFlashcard(card.copy(isMastered = !card.isMastered))
    }

    fun academicClasses(semesterId: Long): Flow<List<AcademicClassEntity>> = db.academicDao().getClasses(semesterId)
    fun academicExams(semesterId: Long): Flow<List<AcademicExamEntity>> = db.academicDao().getExams(semesterId)
    fun academicEvents(semesterId: Long): Flow<List<AcademicEventEntity>> = db.academicDao().getEvents(semesterId)

    fun addSemester(name: String, startDate: String, endDate: String) = viewModelScope.launch {
        db.academicDao().insertSemester(AcademicSemesterEntity(name = name.trim(), startDate = startDate.trim(), endDate = endDate.trim()))
    }

    fun archiveSemester(semester: AcademicSemesterEntity) = viewModelScope.launch {
        db.academicDao().updateSemester(semester.copy(isArchived = true))
    }

    fun addAcademicClass(item: AcademicClassEntity) = viewModelScope.launch { db.academicDao().insertClass(item) }
    fun deleteAcademicClass(item: AcademicClassEntity) = viewModelScope.launch { db.academicDao().deleteClass(item) }
    fun addAcademicExam(item: AcademicExamEntity) = viewModelScope.launch { db.academicDao().insertExam(item) }
    fun deleteAcademicExam(item: AcademicExamEntity) = viewModelScope.launch { db.academicDao().deleteExam(item) }
    fun addAcademicEvent(item: AcademicEventEntity) = viewModelScope.launch {
        val eventId = db.academicDao().insertEvent(item)
        if (item.type == "Assignment" && item.taskId == null) {
            db.taskDao().insertTask(
                TaskEntity(
                    title = item.title,
                    description = item.notes,
                    deadlineMillis = parseAcademicDate(item.date),
                    priority = "Medium",
                    category = "Academic"
                )
            )
        }
        eventId
    }
    fun deleteAcademicEvent(item: AcademicEventEntity) = viewModelScope.launch { db.academicDao().deleteEvent(item) }

    private fun parseAcademicDate(value: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value)?.time
    }.getOrNull() ?: System.currentTimeMillis()

    fun signInWithGoogle(account: GoogleSignInAccount, onResult: (Result<CloudUser>) -> Unit = {}) =
        viewModelScope.launch {
            val result = cloud.signInWithGoogle(account)
            if (result.isSuccess) {
                _cloudUser.value = result.getOrNull()
                cloud.registerMessagingToken()
            }
            onResult(result)
        }

    fun signInWithEmail(email: String, password: String, onResult: (Result<CloudUser>) -> Unit = {}) =
        viewModelScope.launch {
            val result = cloud.signInWithEmail(email, password)
            if (result.isSuccess) {
                _cloudUser.value = result.getOrNull()
                cloud.registerMessagingToken()
            }
            onResult(result)
        }

    fun registerWithEmail(email: String, password: String, onResult: (Result<CloudUser>) -> Unit = {}) =
        viewModelScope.launch {
            val result = cloud.registerWithEmail(email, password)
            if (result.isSuccess) {
                _cloudUser.value = result.getOrNull()
                cloud.registerMessagingToken()
            }
            onResult(result)
        }

    fun signOutFromCloud() {
        cloud.signOut()
        _cloudUser.value = null
    }

    suspend fun uploadCloudFile(path: String, uri: android.net.Uri): Result<String> =
        cloud.uploadFile(path, uri)

    fun saveAssistantMessage(role: String, content: String, conversationId: Long = 1) = viewModelScope.launch {
        db.assistantMessageDao().insertMessage(AssistantMessageEntity(role = role, content = content, conversationId = conversationId))
        val conversation = db.assistantConversationDao().getActiveConversationsOnce().firstOrNull { it.id == conversationId }
        if (conversation != null) {
            val title = if (role == "ME" && conversation.title == "New chat") {
                content.lineSequence().firstOrNull()?.trim()?.take(42).orEmpty().ifBlank { "New chat" }
            } else conversation.title
            db.assistantConversationDao().updateConversation(
                conversation.copy(title = title, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun createAssistantConversation(title: String = "New chat", onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = db.assistantConversationDao().insertConversation(AssistantConversationEntity(title = title.ifBlank { "New chat" }))
        onCreated(id)
    }

    fun renameAssistantConversation(conversation: AssistantConversationEntity, title: String) = viewModelScope.launch {
        db.assistantConversationDao().updateConversation(conversation.copy(title = title.trim().ifBlank { "New chat" }))
    }

    fun toggleAssistantPinned(conversation: AssistantConversationEntity) = viewModelScope.launch {
        db.assistantConversationDao().updateConversation(conversation.copy(isPinned = !conversation.isPinned))
    }

    fun archiveAssistantConversation(conversation: AssistantConversationEntity) = viewModelScope.launch {
        db.assistantConversationDao().updateConversation(conversation.copy(isArchived = true))
    }

    fun restoreAssistantConversation(conversation: AssistantConversationEntity) = viewModelScope.launch {
        db.assistantConversationDao().updateConversation(conversation.copy(isArchived = false))
    }

    fun deleteAssistantConversation(conversation: AssistantConversationEntity) = viewModelScope.launch {
        db.assistantMessageDao().deleteMessages(conversation.id)
        db.assistantConversationDao().deleteConversation(conversation)
    }

    fun addExpense(
        amount: Double,
        category: String,
        note: String,
        paymentMethod: String,
        subcategory: String = "",
        ledger: String = "Personal",
        isRecurring: Boolean = false,
        recurrenceRule: String = ""
    ) = viewModelScope.launch {
        require(amount.isFinite() && amount > 0.0) { "Expense amount must be greater than zero." }
        require(category.isNotBlank()) { "Expense category is required." }
        db.expenseDao().insertExpense(
            ExpenseEntity(
                amount = amount,
                category = category,
                dateMillis = System.currentTimeMillis(),
                dateString = today,
                timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                note = note,
                paymentMethod = paymentMethod,
                subcategory = subcategory,
                ledger = ledger,
                isRecurring = isRecurring,
                recurrenceRule = recurrenceRule
            )
        )
        if (isRecurring) {
            db.recurringExpenseDao().insertExpense(
                RecurringExpenseEntity(
                    title = note.ifBlank { "$category expense" },
                    amount = amount,
                    category = category,
                    ledger = ledger,
                    frequency = recurrenceRule.ifBlank { "Monthly" },
                    nextDueDate = today
                )
            )
        }
    }

    fun addCategory(name: String, parentName: String = "") = viewModelScope.launch {
        db.categoryDao().insertCategories(
            listOf(CategoryEntity(name = name.trim(), parentName = parentName.trim(), iconName = "label", colorHex = "#607D8B", isDefault = false))
        )
    }

    fun saveBudget(category: String, amount: Double, period: String = month) = viewModelScope.launch {
        db.budgetDao().insertOrUpdateBudget(BudgetEntity(category = category, amount = amount, monthYear = period))
    }

    fun addSavingsGoal(name: String, targetAmount: Double) = viewModelScope.launch {
        db.savingsGoalDao().insertGoal(SavingsGoalEntity(name = name.trim(), targetAmount = targetAmount))
    }

    fun updateSavingsGoal(goal: SavingsGoalEntity, savedAmount: Double) = viewModelScope.launch {
        db.savingsGoalDao().updateGoal(goal.copy(savedAmount = savedAmount.coerceIn(0.0, goal.targetAmount)))
    }

    fun deleteSavingsGoal(goal: SavingsGoalEntity) = viewModelScope.launch { db.savingsGoalDao().deleteGoal(goal) }

    fun addRecurringExpense(title: String, amount: Double, category: String, ledger: String, frequency: String) = viewModelScope.launch {
        db.recurringExpenseDao().insertExpense(
            RecurringExpenseEntity(
                title = title.trim(), amount = amount, category = category.trim(),
                ledger = ledger, frequency = frequency,
                nextDueDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )
        )
    }

    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch { db.expenseDao().deleteExpense(expense) }

    fun addStudySession(subject: String, minutes: Int, notes: String = "") = viewModelScope.launch {
        require(subject.isNotBlank()) { "Study subject is required." }
        require(minutes > 0) { "Study duration must be greater than zero." }
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
        require(title.isNotBlank()) { "Task title is required." }
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

    fun deleteClass(item: ClassEntity) = viewModelScope.launch {
        db.classDao().deleteClass(item)
    }

    fun toggleRoutine(routine: DailyRoutineEntity) = viewModelScope.launch {
        db.dailyRoutineDao().updateRoutine(routine.copy(isCompleted = !routine.isCompleted))
    }

    fun deleteRoutine(routine: DailyRoutineEntity) = viewModelScope.launch {
        db.dailyRoutineDao().deleteRoutine(routine)
    }

    fun addHabit(name: String, category: String, reminderEnabled: Boolean, reminderTime: String) = viewModelScope.launch {
        db.habitDao().insertHabit(
            HabitEntity(
                name = name.trim(),
                category = category.trim().ifBlank { "Other" },
                reminderEnabled = reminderEnabled,
                reminderTime = reminderTime.trim().ifBlank { "08:00 AM" }
            )
        )
    }

    fun deleteHabit(habit: HabitEntity) = viewModelScope.launch {
        db.habitDao().deleteHabit(habit)
    }

    fun toggleHabit(habit: HabitEntity, dateString: String) = viewModelScope.launch {
        if (db.habitDao().getCompletion(habit.id, dateString) == null) {
            db.habitDao().saveCompletion(HabitCompletionEntity(habit.id, dateString))
        } else {
            db.habitDao().deleteCompletion(habit.id, dateString)
        }
    }

    fun addCpProblem(
        platform: String,
        problemName: String,
        contestName: String,
        rating: Int?,
        difficulty: String,
        topic: String,
        solvedDate: String,
        attempts: Int,
        editorialLink: String,
        isUpsolved: Boolean
    ) = viewModelScope.launch {
        db.cpProblemDao().insertProblem(
            CpProblemEntity(
                platform = platform,
                problemName = problemName.trim(),
                contestName = contestName.trim(),
                problemRating = rating,
                difficulty = difficulty.trim(),
                topic = topic.trim(),
                solvedDate = solvedDate,
                attempts = attempts,
                editorialLink = editorialLink.trim(),
                isUpsolved = isUpsolved
            )
        )
    }

    fun deleteCpProblem(problem: CpProblemEntity) = viewModelScope.launch {
        db.cpProblemDao().deleteProblem(problem)
    }

    fun saveCpGoal(platform: String, weeklyTarget: Int, monthlyTarget: Int) = viewModelScope.launch {
        db.cpGoalDao().upsertGoal(CpGoalEntity(platform, weeklyTarget, monthlyTarget))
    }

    fun addBatchItem(
        type: String,
        title: String,
        details: String,
        courseCode: String,
        date: String,
        time: String,
        location: String,
        postedBy: String,
        isImportant: Boolean
    ) = viewModelScope.launch {
        db.batchItemDao().insertItem(
            BatchItemEntity(
                type = type, title = title.trim(), details = details.trim(),
                courseCode = courseCode.trim(), date = date.trim(), time = time.trim(),
                location = location.trim(), postedBy = postedBy.trim(),
                isImportant = isImportant
            )
        )
    }

    fun deleteBatchItem(item: BatchItemEntity) = viewModelScope.launch {
        db.batchItemDao().deleteItem(item)
    }

    fun addTravelTrip(
        title: String,
        travelDate: String,
        transportType: String,
        serviceName: String,
        departureTime: String,
        arrivalTime: String,
        departureStation: String,
        arrivalStation: String,
        seatNumber: String,
        coachNumber: String,
        bookingReference: String,
        ticketInformation: String,
        ticketUri: String,
        departureMillis: Long,
        reminderEnabled: Boolean
    ) = viewModelScope.launch {
        val trip = TravelTripEntity(
            title = title.trim(), travelDate = travelDate.trim(), transportType = transportType.trim(),
            serviceName = serviceName.trim(), departureTime = departureTime.trim(), arrivalTime = arrivalTime.trim(),
            departureStation = departureStation.trim(), arrivalStation = arrivalStation.trim(),
            seatNumber = seatNumber.trim(), coachNumber = coachNumber.trim(),
            bookingReference = bookingReference.trim(), ticketInformation = ticketInformation.trim(),
            ticketUri = ticketUri, departureMillis = departureMillis, reminderEnabled = reminderEnabled
        )
        val id = db.travelTripDao().insertTrip(trip)
        if (reminderEnabled && departureMillis > System.currentTimeMillis()) {
            scheduleTravelReminder(trip.copy(id = id))
        }
    }

    fun deleteTravelTrip(trip: TravelTripEntity) = viewModelScope.launch {
        db.travelTripDao().deleteTrip(trip)
        workManager.cancelUniqueWork("travel-reminder-${trip.id}")
    }

    private fun scheduleTravelReminder(trip: TravelTripEntity) {
        val delayMillis = (trip.departureMillis - System.currentTimeMillis() - 86_400_000L).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TravelReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                TravelReminderWorker.TRIP_TITLE to trip.title,
                TravelReminderWorker.ROUTE to "${trip.departureStation} → ${trip.arrivalStation}",
                TravelReminderWorker.DEPARTURE_TIME to "${trip.travelDate} · ${trip.departureTime}"
            ))
            .build()
        workManager.enqueueUniqueWork(
            "travel-reminder-${trip.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun setClassRepresentative(enabled: Boolean) = viewModelScope.launch {
        val current = db.userSettingsDao().getUserSettingsDirect() ?: UserSettingsEntity()
        db.userSettingsDao().updateUserSettings(current.copy(isClassRepresentative = enabled))
    }
}
