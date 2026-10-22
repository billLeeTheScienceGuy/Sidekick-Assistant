package com.sidekick.app.reminders

import android.app.*
import android.content.*
import android.net.Uri
import com.sidekick.app.MainActivity
import com.sidekick.app.SidekickApplication
import com.sidekick.app.data.CommitmentDao
import com.sidekick.app.data.CommitmentEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All reminder mutations and delivery are serialized in this process. */
class ReminderCoordinator(private val context: Context, private val dao: CommitmentDao) {
    private val gate = Mutex()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL, "Commitment reminders", NotificationManager.IMPORTANCE_DEFAULT)) }

    fun notificationsAllowed(): Boolean = manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun pending(item: CommitmentEntity): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(DELIVER)
            .setData(Uri.parse("sidekick://reminder/${item.id}"))
            .putExtra("dueAt", item.dueAt ?: 0L), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(item: CommitmentEntity) {
        if (item.state == "ACCEPTED" && item.dueAt != null && item.deliveredAt == null) {
            // Inexact by design: no exact-alarm privilege. Android may defer in Doze.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(item.dueAt, System.currentTimeMillis() + 1000), pending(item))
        }
    }
    private fun cancel(item: CommitmentEntity) { alarms.cancel(pending(item)); manager.cancel(item.id, 10) }

    suspend fun save(id: String, title: String, dueAt: Long?, zoneId: String) = gate.withLock {
        require(title.isNotBlank() && title.length <= 500) { "Enter a title of 1–500 characters." }
        if (dueAt != null) {
            require(dueAt > System.currentTimeMillis()) { "Choose a future reminder time." }
            require(notificationsAllowed()) { "Enable SideKick notifications in Android settings before scheduling." }
        }
        val old = dao.get(id) ?: error("This commitment was deleted.")
        val updated = old.copy(title = title.trim(), state = "ACCEPTED", dueAt = dueAt, zoneId = zoneId,
            deliveredAt = null, reviewNote = if (dueAt == null) "Saved without a reminder." else "Reminder requested. Android may delay delivery to save battery.")
        cancel(old)
        try { dao.update(updated); arm(updated) }
        catch (e: Exception) { dao.update(old); arm(old); throw e }
    }
    suspend fun mark(id: String, state: String) = gate.withLock {
        require(state in setOf("DISMISSED", "DONE"))
        dao.get(id)?.let { cancel(it); dao.update(it.copy(state = state, dueAt = null)) }
    }
    suspend fun delete(id: String) = gate.withLock { dao.get(id)?.let { cancel(it); dao.delete(id) } }
    suspend fun deleteAll() = gate.withLock { dao.all().forEach(::cancel); dao.deleteAll() }

    suspend fun restore() = gate.withLock { dao.all().forEach(::arm) }

    @Suppress("MissingPermission")
    suspend fun deliver(id: String, expectedDue: Long) = gate.withLock {
        val item = dao.get(id) ?: return@withLock
        if (item.state != "ACCEPTED" || item.deliveredAt != null || item.dueAt != expectedDue) return@withLock
        if (expectedDue > System.currentTimeMillis() + 1000) { arm(item); return@withLock }
        if (!notificationsAllowed()) return@withLock
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(item.id, 10, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("SideKick reminder")
            .setContentText(item.title).setStyle(Notification.BigTextStyle().bigText(item.title))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
        dao.update(item.copy(deliveredAt = System.currentTimeMillis(), reviewNote = "Reminder delivered."))
    }

    companion object { const val CHANNEL = "commitments"; const val DELIVER = "com.sidekick.REMIND" }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as SidekickApplication
        app.background.launch {
            try {
                if (intent.action == ReminderCoordinator.DELIVER) {
                    intent.data?.lastPathSegment?.let { app.reminders.deliver(it, intent.getLongExtra("dueAt", 0)) }
                } else if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) app.reminders.restore()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The persisted reminder is retried when the app reopens. */ }
            finally { pending.finish() }
        }
    }
}
