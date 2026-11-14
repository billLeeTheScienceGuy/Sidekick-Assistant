package com.sidekick.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.sidekick.app.data.*
import com.sidekick.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CalendarSyncConfig(val enabled: Boolean = false, val calendarId: Long? = null, val includeInformational: Boolean = false)
data class CalendarSyncStatus(val synced: Int = 0, val waiting: Int = 0, val conflicts: Int = 0,
    val note: String = "Calendar sync is off. Existing calendar copies stay.")

/** Recoverable one-way mirror. Provider writes use snapshots and never overwrite external edits. */
class CalendarMirror(private val context: Context, private val db: MemoryDatabase, private val settings: GuidanceSettings,
    private val gateway: CalendarGateway = AndroidCalendarGateway(context)) {
    private val gate = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow(CalendarSyncStatus())
    val status = mutableStatus.asStateFlow()
    private val links get() = db.calendarLinks()
    private var observer: ContentObserver? = null
    private var lastAlert = Long.MIN_VALUE
    fun requestSync() { requests.trySend(Unit) }
    fun start(scope: CoroutineScope) {
        scope.launch { combine(db.commitments().observe(), settings.calendarSync) { _, _ -> Unit }.collect { requestSync() } }
        scope.launch { try { for (request in requests) sync() } finally { observe(false) } }
        scope.launch { while (isActive) { delay(900_000); if (settings.calendarSync.value.enabled) requestSync() } }
    }
    fun close() { observe(false); requests.close() }
    private fun observe(enabled: Boolean) {
        if (gateway !is AndroidCalendarGateway) return
        if (!enabled && observer != null) { context.contentResolver.unregisterContentObserver(observer!!); observer = null }
        if (enabled && observer == null) {
            val callback = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { requestSync() } }
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, callback); observer = callback
        }
    }
    private fun publish(status: CalendarSyncStatus, attention: Boolean = false) {
        mutableStatus.value = status
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!attention) { manager.cancel("calendar-sync", 41); return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastAlert != Long.MIN_VALUE && now - lastAlert < 900_000) return
        lastAlert = now
        manager.createNotificationChannel(NotificationChannel("calendar-sync", "Calendar sync status", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try { if (manager.areNotificationsEnabled()) manager.notify("calendar-sync", 41, Notification.Builder(context, "calendar-sync")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SideKick calendar sync needs attention")
            .setContentText("Review calendar access or changed calendar items in Settings. Local reminders remain active.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()) }
        catch (_: SecurityException) { }
    }
    private fun marker(id: String) = CalendarTarget.uri(context.packageName, id)
    private fun fields(item: CommitmentEntity, calendarId: Long) = CalendarMirrorFields(calendarId, item.title.trim(),
        requireNotNull(item.activityStartAt), requireNotNull(item.activityEndAt), java.time.ZoneId.of(item.zoneId).id,
        (item.placeAddress ?: item.placeName).orEmpty(),
        if (item.informational) "Information only; attendance unconfirmed. Saved from SideKick." else "Saved from SideKick.",
        marker(item.id), context.packageName, if (item.informational) CalendarContract.Events.AVAILABILITY_FREE else CalendarContract.Events.AVAILABILITY_BUSY)
    private fun owned(record: CalendarRecord, id: String) = record.fields.marker == marker(id) &&
        record.fields.ownerPackage == context.packageName && !record.fields.hasGuests
    private suspend fun conflict(link: CalendarLink, sourceExists: Boolean) {
        if (sourceExists) links.put(link.copy(state = "CONFLICT")) else links.delete(link.memoryId)
    }
    private suspend fun removed(link: CalendarLink, sourceExists: Boolean) {
        if (sourceExists) links.put(link.copy(eventId = null, appliedHash = null, pendingHash = null, state = "REMOVED"))
        else links.delete(link.memoryId)
    }
    suspend fun sync() = gate.withLock { withContext(Dispatchers.IO) {
        val config = settings.calendarSync.value
        if (!config.enabled || config.calendarId == null) { observe(false); publish(CalendarSyncStatus()); return@withContext }
        if (!gateway.permitted()) { observe(false); publish(CalendarSyncStatus(note = "Calendar access is off. Sync will retry after access returns; local reminders stay active."), true); return@withContext }
        var synced = 0; var waiting = 0; var conflicts = 0
        try {
            observe(true)
            val writable = gateway.calendars().map { it.id }.toSet()
            var unavailable = config.calendarId !in writable
            val ids = (db.commitments().all().map { it.id } + links.all().map { it.memoryId }).distinct()
            for (id in ids) {
                currentCoroutineContext().ensureActive()
                val item = db.commitments().get(id)
                var link = links.get(id)
                if (link?.state == "CONFLICT") {
                    if (item == null) links.delete(id) else conflicts++
                    continue
                }
                val action = CalendarMirrorPolicy.action(item?.state, item?.activityStartAt, item?.activityEndAt, item?.zoneId,
                    item?.informational ?: false, config.includeInformational, link != null, System.currentTimeMillis())
                if (action == CalendarMirrorPolicy.Action.KEEP) { if (link?.eventId != null) synced++; continue }
                if (action == CalendarMirrorPolicy.Action.WAIT) { waiting++; continue }
                if (action == CalendarMirrorPolicy.Action.REMOVE && link == null) continue
                val calendarId = link?.calendarId ?: config.calendarId
                if (calendarId !in writable) { unavailable = true; waiting++; continue }
                val desired = if (action == CalendarMirrorPolicy.Action.UPSERT) fields(requireNotNull(item), calendarId) else null
                val desiredHash = desired?.fingerprint()
                if (link == null) link = CalendarLink(id, calendarId, null, null, desiredHash, "INSERTING")
                var current = link.eventId?.let(gateway::read)
                if (link.eventId == null) {
                    val recovered = gateway.find(calendarId, marker(id))
                    if (recovered.size > 1) { conflict(link, item != null); conflicts++; continue }
                    current = recovered.singleOrNull()
                    if (current != null) {
                        val hash = current.fields.fingerprint()
                        if (!owned(current, id) || hash !in listOfNotNull(link.pendingHash, link.appliedHash, desiredHash)) {
                            conflict(link.copy(eventId = current.id), item != null); conflicts++; continue
                        }
                        link = link.copy(eventId = current.id, appliedHash = hash, pendingHash = null, state = "ACTIVE")
                        links.put(link)
                    }
                }
                if (desired == null) {
                    if (item?.state == "ACCEPTED" && (item.activityStartAt == null || item.activityEndAt == null)) waiting++
                    if (current == null) { removed(link, item != null); continue }
                    val hash = current.fields.fingerprint()
                    if (!owned(current, id) || hash !in listOfNotNull(link.appliedHash, link.pendingHash)) {
                        conflict(link, item != null); conflicts++; continue
                    }
                    links.put(link.copy(appliedHash = hash, pendingHash = null, state = "DELETING"))
                    if (gateway.remove(current)) removed(link, item != null) else { conflict(link, item != null); conflicts++ }
                    continue
                }
                if (current == null) {
                    if (link.eventId != null && link.state != "DELETING") { conflict(link, true); conflicts++; continue }
                    links.put(link.copy(eventId = null, pendingHash = desiredHash, state = "INSERTING"))
                    val eventId = gateway.insert(desired)
                    val inserted = gateway.read(eventId) ?: error("Inserted calendar event unavailable; retrying from marker")
                    if (!owned(inserted, id) || inserted.fields.fingerprint() != desiredHash) {
                        conflict(link.copy(eventId = eventId), true); conflicts++; continue
                    }
                    links.put(link.copy(eventId = eventId, appliedHash = desiredHash, pendingHash = null, state = "ACTIVE")); synced++
                    continue
                }
                val actualHash = current.fields.fingerprint()
                if (!owned(current, id) || actualHash !in listOfNotNull(link.appliedHash, link.pendingHash)) {
                    conflict(link, true); conflicts++; continue
                }
                if (actualHash != desiredHash) {
                    links.put(link.copy(pendingHash = desiredHash, state = "UPDATING"))
                    if (!gateway.replace(current, desired)) { conflict(link, true); conflicts++; continue }
                }
                links.put(link.copy(appliedHash = desiredHash, pendingHash = null, state = "ACTIVE")); synced++
            }
            publish(CalendarSyncStatus(synced, waiting, conflicts,
                "$synced calendar copies up to date · $waiting need dates or calendar access · $conflicts left unchanged after outside edits." +
                    if (unavailable) " A calendar can’t be opened." else ""), conflicts > 0 || unavailable)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { publish(CalendarSyncStatus(synced, waiting, conflicts, "Calendar sync interrupted; saved work will be retried. Local reminders remain active."), true) }
    } }
}
