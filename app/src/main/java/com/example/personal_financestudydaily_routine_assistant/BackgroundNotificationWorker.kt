package com.example.personal_financestudydaily_routine_assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.personal_financestudydaily_routine_assistant.data.database.AppDatabase
import com.example.personal_financestudydaily_routine_assistant.data.database.NotificationEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class BackgroundNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val now = Calendar.getInstance()
        val today = dateFormat.format(now.time)
        val tomorrow = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowDate = dateFormat.format(tomorrow.time)

        database.classDao().getAllClassesOnce().forEach { classEntity ->
            val nextClass = nextClassTime(classEntity.dayOfWeek, classEntity.startTime, now)
            if (nextClass != null && dateFormat.format(nextClass.time) == tomorrowDate) {
                val key = "class:${classEntity.id}:$tomorrowDate"
                notifyOnce(
                    key = key,
                    title = "Tomorrow at ${classEntity.startTime}",
                    message = "${classEntity.courseCode} ${classEntity.courseName}",
                    type = "class",
                    notificationId = key.hashCode()
                )
            }
        }

        database.academicDao().getAllClassesOnce().forEach { classEntity ->
            val nextClass = nextClassTime(classEntity.dayOfWeek, classEntity.startTime, now)
            if (classEntity.notificationsEnabled && nextClass != null &&
                dateFormat.format(nextClass.time) == today &&
                nextClass.timeInMillis - now.timeInMillis in 0..(classEntity.reminderMinutes * 60_000L)
            ) {
                val key = "academic-class:${classEntity.id}:${today}:${classEntity.startTime}"
                notifyOnce(
                    key,
                    "Class starts in ${classEntity.reminderMinutes} minutes",
                    "${classEntity.courseCode} - ${classEntity.courseName}${classEntity.room.takeIf { it.isNotBlank() }?.let { " · Room $it" } ?: ""}",
                    "academic",
                    key.hashCode()
                )
            }
        }

        database.academicDao().getUpcomingExams(today).forEach { exam ->
            if (exam.reminderEnabled && exam.examDate == tomorrowDate) {
                val key = "academic-exam:${exam.id}:$tomorrowDate"
                notifyOnce(
                    key,
                    "Upcoming exam tomorrow",
                    "${exam.courseCode} - ${exam.courseName} at ${exam.startTime}${exam.room.takeIf { it.isNotBlank() }?.let { " · Room $it" } ?: ""}",
                    "academic",
                    key.hashCode()
                )
            }
        }

        database.taskDao().getPendingTasksOnce().filter { task ->
            dateFormat.format(Date(task.deadlineMillis)) == tomorrowDate
        }.forEach { task ->
            val key = "task:${task.id}:$tomorrowDate"
            notifyOnce(
                key = key,
                title = "Assignment Deadline",
                message = "${task.title} is due tomorrow.",
                type = "task",
                notificationId = key.hashCode()
            )
        }

        if (now.get(Calendar.HOUR_OF_DAY) >= 20) {
            val goals = database.cpGoalDao().getAllGoalsOnce()
            val dailyTarget = goals.sumOf { goal ->
                if (goal.weeklyTarget > 0) {
                    ceil(goal.weeklyTarget / 7.0).toInt()
                } else {
                    ceil(goal.monthlyTarget / 30.0).toInt()
                }
            }
            val solvedToday = database.cpProblemDao().getSolvedCountForDate(today)
            val remaining = (dailyTarget - solvedToday).coerceAtLeast(0)
            if (remaining > 0) {
                val key = "cp-target:$today"
                notifyOnce(
                    key = key,
                    title = "CP Target",
                    message = "You still have $remaining ${if (remaining == 1) "problem" else "problems"} remaining for today's target.",
                    type = "study",
                    notificationId = key.hashCode()
                )
            }
        }

        database.habitDao().getAllHabitsOnce()
            .filter { it.reminderEnabled }
            .forEach { habit ->
                val reminder = runCatching { timeFormat.parse(habit.reminderTime) }.getOrNull()
                if (reminder != null) {
                    val reminderCalendar = Calendar.getInstance().apply { time = reminder }
                    val reminderMinutes = reminderCalendar.get(Calendar.HOUR_OF_DAY) * 60 + reminderCalendar.get(Calendar.MINUTE)
                    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    if (currentMinutes in reminderMinutes..(reminderMinutes + 14) &&
                        database.habitDao().getCompletion(habit.id, today) == null
                    ) {
                        val key = "habit:${habit.id}:$today"
                        notifyOnce(key, "Habit reminder", "Time to complete ${habit.name}.", "habit", key.hashCode())
                    }
                }
            }
        return Result.success()
    }

    private suspend fun notifyOnce(
        key: String,
        title: String,
        message: String,
        type: String,
        notificationId: Int
    ) {
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getBoolean(key, false)) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Smart Life reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        AppDatabase.getDatabase(applicationContext).notificationDao().insertNotification(
            NotificationEntity(title = title, message = message, type = type)
        )
        preferences.edit().putBoolean(key, true).apply()
    }

    private fun nextClassTime(dayOfWeek: String, startTime: String, now: Calendar): Calendar? {
        val targetDay = dayNames.indexOfFirst { it.equals(dayOfWeek.trim(), ignoreCase = true) }
        if (targetDay < 0) return null
        val parsedTime = timeFormat.parse(startTime.trim()) ?: return null
        val time = Calendar.getInstance(TimeZone.getDefault()).apply { time = parsedTime }
        val result = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, targetDay + Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, time.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!result.after(now)) result.add(Calendar.WEEK_OF_YEAR, 1)
        return result
    }

    companion object {
        private const val CHANNEL_ID = "smart_life_reminders"
        private const val PREFERENCES = "background_notification_state"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        private val dayNames = listOf(
            "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"
        )

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundNotificationWorker>(
                15, TimeUnit.MINUTES
            ).setInitialDelay(1, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "background-notifications",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
