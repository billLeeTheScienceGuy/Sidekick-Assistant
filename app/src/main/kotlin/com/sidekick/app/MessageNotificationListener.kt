package com.sidekick.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.asStateFlow

class MessageNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { super.onListenerConnected(); connection.value = true }
    override fun onListenerDisconnected() {
        connection.value = false
        super.onListenerDisconnected()
        requestConnection(applicationContext)
    }
    override fun onDestroy() { connection.value = false; super.onDestroy() }
    companion object {
        private val connection = kotlinx.coroutines.flow.MutableStateFlow(false)
        val connected = connection.asStateFlow()
        fun requestConnection(context: Context) {
            if (connection.value || !(context.applicationContext as SidekickApplication).guidance.messageNotifications.value) return
            val component = ComponentName(context, MessageNotificationListener::class.java)
            val granted = if (android.os.Build.VERSION.SDK_INT >= 27)
                context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().split(':').contains(component.flattenToString())
            if (granted) try { requestRebind(component) } catch (_: SecurityException) { }
        }
    }
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
