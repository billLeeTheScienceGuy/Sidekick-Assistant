package com.sidekick.app.reminders

import android.app.*
import android.content.*
import android.net.Uri
import com.sidekick.app.MainActivity
import com.sidekick.app.SidekickApplication
import com.sidekick.app.data.CommitmentDao
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.app.data.AutomaticChange
import com.sidekick.app.data.MemorySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All reminder mutations and delivery are serialized in this process. */
class ReminderCoordinator(private val context: Context, private val dao: CommitmentDao, private val scheduler: ReminderAlarms = AndroidReminderAlarms(context)) {
    private val gate = Mutex()
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL, "Commitment reminders", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) }) }

    fun notificationsAllowed(): Boolean = manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun alertDescription(): String {
        val channel = manager.getNotificationChannel(CHANNEL) ?: return "Reminder channel unavailable"
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val muted = audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL || audio.isStreamMute(android.media.AudioManager.STREAM_NOTIFICATION) || audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) == 0
        return listOf(if (muted || channel.sound == null) "Sound off or muted" else "Sound enabled",
            if (audio.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT) "Phone is in Silent mode; sound/vibration may be suppressed" else if (audio.ringerMode == android.media.AudioManager.RINGER_MODE_VIBRATE) "Phone is in Vibrate mode" else "Phone is in Ring mode",
            if (channel.shouldVibrate()) "Vibration enabled" else "Vibration off",
            if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) "Pop-up enabled" else "Pop-up off",
            if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) "Do Not Disturb may suppress alerts" else "Do Not Disturb off").joinToString(" · ")
    }
    suspend fun scheduleCheck(): Long {
        check(notificationsAllowed()) { "Enable reminder notifications first." }
        val now = System.currentTimeMillis(); val due = now + 120_000
        val zone = java.time.ZoneId.systemDefault().id
        val source = CommitmentEntity(CHECK_ID, "SideKick reminder check", "Reminder delivery check", now, zone, "Test reminder")
        save(CHECK_ID, source.title, due, zone, source, automatic = true)
        return due
    }

    private fun arm(item: CommitmentEntity) {
        if (item.state == "ACCEPTED" && item.dueAt != null && item.deliveredAt == null) {
            scheduler.arm(item)
        }
    }
    private fun cancel(item: CommitmentEntity) { scheduler.cancel(item); manager.cancel(item.id, 10) }
    private fun clearUndo(id: String) { manager.cancel(id, com.sidekick.app.AutomaticMemory.NOTIFICATION) }

    suspend fun save(id: String, title: String, dueAt: Long?, zoneId: String, proposal: CommitmentEntity? = null,
        activityStartAt: Long? = null, activityEndAt: Long? = null, informational: Boolean? = null,
        placeName: String? = null, placeAddress: String? = null, automatic: Boolean = false) = gate.withLock {
        require(title.isNotBlank() && title.length <= 500) { "Enter a title of 1–500 characters." }
        if (activityStartAt != null && activityEndAt != null) com.sidekick.core.CalendarOverlap.validate(activityStartAt, activityEndAt)
        if (dueAt != null) {
            require(dueAt > System.currentTimeMillis()) { "Choose a future reminder time." }
            require(automatic || notificationsAllowed()) { "Enable SideKick notifications in Android settings before scheduling." }
        }
        val old = dao.get(id)
        val oldChange = dao.change(id)
        val source = old ?: proposal?.takeIf { it.id == id } ?: error("This commitment was deleted.")
        require((placeName?.length ?: 0) <= 200 && (placeAddress?.length ?: 0) <= 500) { "Use a shorter place name or address." }
        val updated = source.copy(title = title.trim(), state = "ACCEPTED", dueAt = dueAt, zoneId = zoneId,
            evidence = if (automatic) proposal?.evidence?.take(1200) ?: source.evidence else source.evidence,
            activityStartAt = activityStartAt, activityEndAt = activityEndAt,
            informational = informational ?: source.informational,
            placeName = (placeName ?: source.placeName)?.trim()?.ifBlank { null },
            placeAddress = (placeAddress ?: source.placeAddress)?.trim()?.ifBlank { null },
            deliveredAt = null, reviewNote = (if (automatic) "Saved automatically. " else "") + if (dueAt == null) "Saved without a reminder." else "Reminder requested. Android may delay delivery to save battery.")
        val change = if (automatic) AutomaticChange(id, java.util.UUID.randomUUID().toString(), old?.let(MemorySnapshot::encode), MemorySnapshot.encode(updated), System.currentTimeMillis()) else null
        old?.let(::cancel)
        try { dao.write(updated, old != null, change); arm(updated); clearUndo(id) }
        catch (e: Exception) {
            withContext(NonCancellable) {
                cancel(updated)
                if (old == null) dao.delete(id) else { dao.write(old, true, oldChange); arm(old) }
            }
            throw e
        }
    }
    /** Compare with the resolved event under the same lock as edits and alarm delivery. */
    suspend fun changeEvent(expected: CommitmentEntity, start: Long?, cancellation: Boolean, archiveDuplicate: Boolean = false): Boolean = gate.withLock {
        val old = dao.get(expected.id)?.takeIf { it == expected && it.state == "ACCEPTED" } ?: return@withLock false
        if (!cancellation) require(start != null && start > System.currentTimeMillis())
        require(!archiveDuplicate || (cancellation && old.informational))
        val duration = old.activityEndAt?.let { end -> old.activityStartAt?.let { end - it } }
        val due = if (cancellation) null else old.dueAt?.let { due ->
            old.activityStartAt?.let { (start!! - (it - due)).takeIf { value -> value > System.currentTimeMillis() } }
        }
        val updated = old.copy(title = if (cancellation) old.title else if (com.sidekick.core.LocalizedActivities.hasEvent(old.title)) com.sidekick.core.LocalizedActivities.eventName(old.title) ?: old.title else old.title.replace(
            Regex("\\s+(?:at|on|today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b.*", RegexOption.IGNORE_CASE), "").trim().ifBlank { old.title },
            state = if (archiveDuplicate) "DISMISSED" else if (cancellation) "CANCELLED" else "ACCEPTED",
            activityStartAt = if (cancellation) old.activityStartAt else start,
            activityEndAt = if (cancellation) old.activityEndAt else duration?.let { start!! + it },
            dueAt = due, deliveredAt = null,
            reviewNote = if (archiveDuplicate) "Duplicate announcement archived; your personal event remains active. Undo is available in the notification."
                else if (cancellation) "Cancelled automatically. Reminder removed. Undo is available in the notification."
                else "Rescheduled automatically. Known duration and reminder lead time retained.")
        if (old.state == updated.state && old.activityStartAt == updated.activityStartAt) return@withLock false
        val previousChange = dao.change(old.id)
        val journal = AutomaticChange(old.id, java.util.UUID.randomUUID().toString(), MemorySnapshot.encode(old), MemorySnapshot.encode(updated), System.currentTimeMillis())
        cancel(old)
        try { dao.write(updated, true, journal); arm(updated); clearUndo(old.id) }
        catch (e: Exception) {
            withContext(NonCancellable) { cancel(updated); dao.write(old, true, previousChange); arm(old) }
            throw e
        }
        true
    }
    suspend fun undoAutomatic(id: String, token: String): Boolean = gate.withLock {
        val change = dao.change(id)?.takeIf { it.token == token } ?: return@withLock false
        val current = dao.get(id) ?: return@withLock false
        val expected = MemorySnapshot.decode(change.after)
        if (!MemorySnapshot.matchesForUndo(current, expected)) return@withLock false
        val previous = change.before?.let(MemorySnapshot::decode)
        try {
            cancel(current); dao.undoWrite(id, previous); previous?.let(::arm); clearUndo(id)
        } catch (e: Exception) {
            withContext(NonCancellable) {
                previous?.let(::cancel)
                dao.write(current, dao.get(id) != null, change)
                arm(current)
            }
            throw e
        }
        true
    }
    suspend fun mark(id: String, state: String) = gate.withLock {
        require(state in setOf("DISMISSED", "DONE"))
        dao.get(id)?.let { cancel(it); dao.write(it.copy(state = state, dueAt = null), true, null); clearUndo(id) }
    }
    suspend fun delete(id: String) = gate.withLock { dao.get(id)?.let { cancel(it); dao.delete(id); clearUndo(id) } }
    suspend fun deleteAll() = gate.withLock { val all = dao.all(); all.forEach(::cancel); dao.deleteAll(); all.forEach { clearUndo(it.id) } }

    suspend fun restore() = gate.withLock { dao.all().forEach(::arm) }

    @Suppress("MissingPermission")
    suspend fun deliver(id: String, expectedDue: Long) = gate.withLock {
        val item = dao.get(id) ?: return@withLock
        if (item.state != "ACCEPTED" || item.deliveredAt != null || item.dueAt != expectedDue) return@withLock
        if (expectedDue > System.currentTimeMillis() + 1000) { arm(item); return@withLock }
        if (!notificationsAllowed()) return@withLock
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(item.id, 10, Notification.Builder(context, CHANNEL)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("SideKick reminder")
            .setContentText(item.title).setStyle(Notification.BigTextStyle().bigText(item.title))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build())
        dao.update(item.copy(deliveredAt = System.currentTimeMillis(), reviewNote = "Reminder delivered."))
    }

    companion object { const val CHANNEL = "commitments"; const val DELIVER = "com.sidekick.REMIND"; const val CHECK_ID = "sidekick-reminder-check" }
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
                if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) app.automation.restoreNotifications()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The persisted reminder is retried when the app reopens. */ }
            finally { pending.finish() }
        }
    }
}
