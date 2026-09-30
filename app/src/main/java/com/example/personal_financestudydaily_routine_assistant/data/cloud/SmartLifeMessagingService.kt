package com.example.personal_financestudydaily_routine_assistant.data.cloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.personal_financestudydaily_routine_assistant.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SmartLifeMessagingService : FirebaseMessagingService() {
    companion object {
        private const val LOCATION_CHANNEL_ID = "location_requests"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // The next authenticated sync persists the latest token to Firestore.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["type"] != "LOCATION_REQUEST") return
        val shareUrl = message.data["shareUrl"] ?: return
        val uri = Uri.parse(shareUrl)
        val configuredOrigin = Uri.parse(BuildConfig.ASSISTANT_BASE_URL)
        if (
            uri.scheme != "https" ||
            uri.host != configuredOrigin.host ||
            uri.port != configuredOrigin.port ||
            uri.path != "/location-share" ||
            !Regex("^token=[A-Za-z0-9_-]{43}$").matches(uri.fragment.orEmpty())
        ) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) return
        createLocationChannel()

        val openConsentPage = Intent(Intent.ACTION_VIEW, uri)
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.data["requestId"]?.hashCode() ?: shareUrl.hashCode(),
            openConsentPage,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, LOCATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Location request")
            .setContentText("Someone is requesting your location. Review before sharing.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(message.data["requestId"]?.hashCode() ?: shareUrl.hashCode(), notification)
    }

    private fun createLocationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LOCATION_CHANNEL_ID,
                "Location requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Consent links for location-sharing requests"
            }
        )
    }
}
