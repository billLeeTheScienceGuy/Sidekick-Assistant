package com.sidekick.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class MessageNotificationListener : NotificationListenerService() {
    @Suppress("DEPRECATION")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { readMessage(sbn) }
    }
    private fun readMessage(sbn: StatusBarNotification) {
        val app = application as SidekickApplication
        if (!app.guidance.messageNotifications.value || sbn.packageName !in app.guidance.messageApps.value) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (notification.category != Notification.CATEGORY_MESSAGE && style == null) return
        val messages = if (style != null) style.messages
            .filter { it.person != null }.map { IncomingText(it.text.toString().take(4000), it.timestamp) }
        else listOfNotNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            .map { IncomingText(it.toString().take(4000), sbn.postTime) }
        if (messages.isNotEmpty()) app.messageInbox.enqueue(MessageEnvelope(sbn.packageName, notification.shortcutId ?: sbn.key, messages.takeLast(25)))
    }
}
