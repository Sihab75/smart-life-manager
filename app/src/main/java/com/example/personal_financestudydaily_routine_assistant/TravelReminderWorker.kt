package com.example.personal_financestudydaily_routine_assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TravelReminderWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Travel reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val title = inputData.getString(TRIP_TITLE) ?: "Upcoming journey"
        val route = inputData.getString(ROUTE) ?: "Your saved trip"
        val departure = inputData.getString(DEPARTURE_TIME) ?: ""
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Travel reminder · $title")
            .setContentText("$route · $departure")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your journey is tomorrow. $route · Departure $departure"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(title.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val TRIP_TITLE = "trip_title"
        const val ROUTE = "route"
        const val DEPARTURE_TIME = "departure_time"
        private const val CHANNEL_ID = "travel_reminders"
    }
}
