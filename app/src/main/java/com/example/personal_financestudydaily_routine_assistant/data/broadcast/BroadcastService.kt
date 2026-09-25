package com.example.personal_financestudydaily_routine_assistant.data.broadcast

import com.example.personal_financestudydaily_routine_assistant.data.database.*

data class ChannelDeliveryResult(val status: String, val message: String)

interface NotificationChannel {
    val name: String
    suspend fun deliver(announcement: BatchAnnouncementEntity, contact: StudentContactEntity): ChannelDeliveryResult
}

class InAppNotificationChannel(private val dao: NotificationDao) : NotificationChannel {
    override val name = "In-App"

    override suspend fun deliver(
        announcement: BatchAnnouncementEntity,
        contact: StudentContactEntity
    ): ChannelDeliveryResult {
        dao.insertNotification(
            NotificationEntity(
                title = announcement.title,
                message = announcement.details,
                type = "batch"
            )
        )
        return ChannelDeliveryResult("Successful", "In-app notification created")
    }
}

class WhatsAppChannel : NotificationChannel {
    override val name = "WhatsApp"

    override suspend fun deliver(
        announcement: BatchAnnouncementEntity,
        contact: StudentContactEntity
    ): ChannelDeliveryResult =
        ChannelDeliveryResult(
            "Failed",
            "Not available through the configured official messaging channel."
        )
}

class MessengerChannel : NotificationChannel {
    override val name = "Messenger"

    override suspend fun deliver(
        announcement: BatchAnnouncementEntity,
        contact: StudentContactEntity
    ): ChannelDeliveryResult =
        ChannelDeliveryResult(
            "Failed",
            "Not available through the configured official messaging channel."
        )
}

class BroadcastService(
    private val broadcastDao: BroadcastDao,
    private val notificationDao: NotificationDao
) {
    private val channels = listOf(
        InAppNotificationChannel(notificationDao),
        WhatsAppChannel(),
        MessengerChannel()
    ).associateBy { it.name }

    suspend fun publish(
        announcement: BatchAnnouncementEntity,
        recipients: List<StudentContactEntity>
    ): Long {
        val announcementId = broadcastDao.insertAnnouncement(announcement)
        broadcastDao.insertRecipients(recipients.map { AnnouncementRecipientEntity(announcementId, it.id) })
        val attempts = announcement.channels.split(",").mapNotNull { channels[it.trim()] }.flatMap { channel ->
            recipients.map { contact ->
                val result = channel.deliver(announcement.copy(id = announcementId), contact)
                DeliveryAttemptEntity(
                    announcementId = announcementId,
                    contactId = contact.id,
                    channel = channel.name,
                    status = result.status,
                    message = result.message
                )
            }
        }
        if (attempts.isNotEmpty()) broadcastDao.insertAttempts(attempts)
        return announcementId
    }
}
