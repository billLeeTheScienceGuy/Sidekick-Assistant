package com.sidekick.app

import android.app.*
import android.content.*
import android.net.Uri
import com.sidekick.app.data.CommitmentDao
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.core.TimeResolver
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial background saves. Detection still controls ownership and event association. */
class AutomaticMemory(private val context: Context, private val settings: GuidanceSettings,
    private val temporary: TemporaryMemory, private val dao: CommitmentDao, private val reminders: ReminderCoordinator) {
    private val gate = Mutex()
    private val mutableStatus = MutableStateFlow("Automatic saving is ready")
    val status = mutableStatus.asStateFlow()
    private data class Anchor(val id: String, val at: Instant)
    private val anchors = linkedMapOf<String, Anchor>()
    private fun channel(source: String?) = if (source?.startsWith("voice:") == true) "voice" else source ?: "typed"
    private fun remember(item: com.sidekick.app.data.CommitmentEntity) {
        anchors[channel(item.sourceKey)] = Anchor(item.id, Instant.now())
        while (anchors.size > 32) anchors.remove(anchors.keys.first())
    }
    private var cleanupChecked = false
    private suspend fun consolidateAnnouncements() {
        val active = dao.all().filter { it.state == "ACCEPTED" && it.activityStartAt != null }
        val personalByKey = active.filter { !it.informational }.groupBy {
            Triple(channel(it.sourceKey), it.activityStartAt, com.sidekick.core.ActivityIdentity.subject(it.evidence))
        }
        for (announcement in active.filter { it.informational }) {
            if (dao.change(announcement.id) == null) continue // Manual changes and Undo are authoritative.
            val subject = com.sidekick.core.ActivityIdentity.subject(announcement.evidence) ?: continue
            val personal = personalByKey[Triple(channel(announcement.sourceKey), announcement.activityStartAt, subject)].orEmpty().filter {
                kotlin.math.abs(it.capturedAt - announcement.capturedAt) <= 7_200_000L &&
                com.sidekick.core.ActivityIdentity.subject(it.evidence) == subject &&
                (it.placeName == null || announcement.placeName == null || it.placeName.equals(announcement.placeName, true)) &&
                (it.placeAddress == null || announcement.placeAddress == null || it.placeAddress.equals(announcement.placeAddress, true)) }
            if (personal.size == 1 && reminders.changeEvent(announcement, null, true, archiveDuplicate = true)) {
                temporary.retire(announcement.id)
                runCatching { notifyChange(announcement.id, announcement.title, true) }
            }
        }
    }
    private suspend fun processChanges() {
        for (pending in temporary.takeChanges()) {
            val operation = pending.change
            val origin = channel(pending.source)
            val all = dao.all().filter { item ->
                item.state == "ACCEPTED" && item.activityStartAt?.let { it > pending.at.toEpochMilli() } == true &&
                    channel(item.sourceKey) == origin &&
                    (item.informational || pending.speaker == com.sidekick.core.Speaker.USER)
            }
            val anchor = anchors[origin]?.takeIf { java.time.Duration.between(it.at, pending.at).seconds in -5..120 }
            val candidates = if (operation.pronoun) {
                all.filter { it.id == anchor?.id }
            } else all.filter { operation.matches(it.title) || (operation.localized && dao.change(it.id) != null && operation.matches(it.evidence.substringBefore('\n'))) }
            // A bare event noun refers to the matching event just discussed in this channel.
            // Named targets, expired context and ownership restrictions keep normal ambiguity rules.
            val contextual = if (operation.localized && com.sidekick.core.LocalizedActivities.bareEventKey(operation.target) != null)
                candidates.singleOrNull { it.id == anchor?.id } else null
            val item = contextual ?: candidates.singleOrNull()
            if (item == null) { mutableStatus.value = "Event change unclear. Saved events were left unchanged."; continue }
            val start = if (operation.cancel) null else operation.newStart(Instant.ofEpochMilli(item.activityStartAt!!), pending.at, ZoneId.of(item.zoneId))?.toEpochMilli()
            if (!operation.cancel && start == null) { mutableStatus.value = "New event time is unclear. Saved time was left unchanged."; continue }
            try {
                if (reminders.changeEvent(item, start, operation.cancel)) {
                    dao.get(item.id)?.let { saved -> temporary.changedEvent(saved); remember(saved) }
                    runCatching { notifyChange(item.id, item.title, true) }
                    mutableStatus.value = if (operation.cancel) "Event cancelled automatically" else "Event rescheduled automatically"
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableStatus.value = "Could not change the saved event. Try again." }
        }
    }
    suspend fun process() = gate.withLock {
        if (!settings.handsOffMode.value) {
            if (temporary.takeChanges().isNotEmpty()) mutableStatus.value = "Event changes require Hands-off mode. Use Edit to change a saved event in Review mode."
            return@withLock
        }
        if (!cleanupChecked) { consolidateAnnouncements(); cleanupChecked = true }
        var wrote = false
        for (candidate in temporary.drafts.value) {
            currentCoroutineContext().ensureActive()
            if (!settings.handsOffMode.value) break
            if (candidate.evidence == "Manually added") continue
            try {
                var target = temporary.updateTarget(candidate.id) ?: candidate.id
                var linked = false
                var attendance = false
                var withdrawal = false
                var timingUpgrade = false
                if (target == candidate.id && candidate.activityStartAt != null) {
                    fun opening(text: String) = java.text.Normalizer.normalize(text.substringBefore('\n').lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFKC).filter { it.isLetterOrDigit() }
                    val dateRefinements = mutableSetOf<String>()
                    val matches = dao.all().filter { it.state in setOf("ACCEPTED", "CANCELLED") }.filter {
                        val sameOrigin = channel(candidate.sourceKey) == channel(it.sourceKey)
                        val baselineStart = com.sidekick.core.ActivityTiming.extract(com.sidekick.core.Transcript(it.evidence.substringBefore('\n'), Instant.ofEpochMilli(it.capturedAt), ZoneId.of(it.zoneId), com.sidekick.core.Speaker.OTHER, true), informationalOnly = true).singleOrNull()?.activityStartAt?.toEpochMilli()
                        val inferredDate = it.activityStartAt == baselineStart && com.sidekick.core.ActivityTiming.extract(
                            com.sidekick.core.Transcript(it.evidence, Instant.ofEpochMilli(it.capturedAt), ZoneId.of(it.zoneId), com.sidekick.core.Speaker.OTHER, true), true).singleOrNull()?.needsReview?.contains("Date inferred") == true
                        val refinesDate = it.sourceKey == candidate.sourceKey && it.capturedAt == candidate.capturedAt && inferredDate &&
                            !candidate.reviewNote.contains("Date inferred") && it.informational == candidate.informational && dao.change(it.id) != null
                        if (refinesDate) dateRefinements += it.id
                        sameOrigin && opening(it.evidence) == opening(candidate.evidence) &&
                            (it.activityStartAt == candidate.activityStartAt || baselineStart == candidate.activityStartAt ||
                                (it.activityStartAt == null && it.dueAt == null && it.informational == candidate.informational && dao.change(it.id) != null) ||
                                refinesDate)
                    }
                    if (matches.size == 1) { target = matches.single().id; linked = true; timingUpgrade = matches.single().activityStartAt == null || target in dateRefinements }
                }
                if (!linked && target == candidate.id && candidate.activityStartAt != null) {
                    val subject = com.sidekick.core.ActivityIdentity.subject(candidate.evidence)
                    val matches = if (subject == null) emptyList() else dao.activitiesAt(candidate.activityStartAt).filter { item ->
                        val sameOrigin = if (candidate.sourceKey?.startsWith("voice:") == true) item.sourceKey?.startsWith("voice:") == true else candidate.sourceKey == item.sourceKey
                        sameOrigin && subject == com.sidekick.core.ActivityIdentity.subject(item.evidence) &&
                            (item.placeName == null || candidate.placeName == null || item.placeName.equals(candidate.placeName, true)) &&
                            (item.placeAddress == null || candidate.placeAddress == null || item.placeAddress.equals(candidate.placeAddress, true))
                    }
                    if (matches.size > 1) { mutableStatus.value = "Several saved events fit. This suggestion remains in Review."; continue }
                    matches.singleOrNull()?.let { item ->
                        target = item.id; linked = true
                        attendance = item.informational && !candidate.informational &&
                            (candidate.sourceKey == null || candidate.sourceKey?.endsWith(":USER") == true)
                    }
                }
                val old = dao.get(target)
                if (old != null && channel(old.sourceKey) == channel(candidate.sourceKey) && (candidate.sourceKey == null || candidate.sourceKey.endsWith(":USER"))) {
                    val freshAttendance = candidate.evidence.lines().filter { it !in old.evidence.lines() }
                        .mapNotNull { com.sidekick.core.ConversationActivities.attendanceValue(it) }.lastOrNull()
                    if (freshAttendance != null) {
                        attendance = freshAttendance && old.informational && !candidate.informational
                        withdrawal = !freshAttendance && !old.informational && candidate.informational
                    }
                }
                if (old != null && old.activityStartAt == null && candidate.activityStartAt != null && old.informational == candidate.informational) timingUpgrade = true
                if (temporary.drafts.value.none { it == candidate }) continue
                if (old?.state == "CANCELLED") { temporary.dismiss(candidate.id); continue }
                val original = temporary.drafts.value.find { it.id == target }
                if (old == null && target != candidate.id && original == null) { temporary.dismiss(candidate.id); continue }
                val baseline = if (linked && old != null) com.sidekick.core.ActivityTiming.extract(
                    com.sidekick.core.Transcript(old.evidence, Instant.ofEpochMilli(old.capturedAt), ZoneId.of(old.zoneId), com.sidekick.core.Speaker.OTHER, true), informationalOnly = true).singleOrNull() else null
                val staleEnd = baseline?.activityEndAt?.toEpochMilli()?.let { it == candidate.activityEndAt && it != old?.activityEndAt } == true
                val stalePlace = baseline?.placeName?.let { it == candidate.placeName && it != old?.placeName && !candidate.evidence.contains('\n') } == true
                val changedPlace = !stalePlace && candidate.placeName != null && old?.placeName != candidate.placeName
                val source = if (linked && old != null) candidate.copy(id = target, title = if (attendance) candidate.title else old.title,
                    informational = if (attendance) false else if (withdrawal) true else old.informational, activityStartAt = if (timingUpgrade) candidate.activityStartAt else old.activityStartAt, activityEndAt = if (staleEnd) old.activityEndAt else candidate.activityEndAt ?: old.activityEndAt,
                    placeName = if (stalePlace) old.placeName else candidate.placeName ?: old.placeName,
                    placeAddress = if (stalePlace) old.placeAddress else candidate.placeAddress ?: if (changedPlace) null else old.placeAddress) else candidate.copy(id = target)
                if (linked && old != null) {
                    val unchanged = !attendance && !withdrawal && !timingUpgrade && source.activityEndAt == old.activityEndAt && source.placeName == old.placeName && source.placeAddress == old.placeAddress
                    if (unchanged) { temporary.approve(candidate.id, old); remember(old); continue }
                    // A manual edit invalidates automatic ownership; never overwrite it from a replay.
                    if (dao.change(target) == null) continue
                }
                if (old != null && dao.change(target) == null) continue
                val due = if (withdrawal || source.informational) null else if (attendance || timingUpgrade) source.activityStartAt?.takeIf { it > System.currentTimeMillis() } else if (old != null) old.dueAt else
                    (candidate.activityStartAt ?: TimeResolver.suggest(candidate.evidence, Instant.ofEpochMilli(candidate.capturedAt), ZoneId.of(candidate.zoneId))?.toEpochMilli())
                        ?.takeIf { it > System.currentTimeMillis() }
                reminders.save(target, source.title, due?.takeIf { it > System.currentTimeMillis() }, source.zoneId,
                    source, source.activityStartAt, source.activityEndAt, source.informational, source.placeName.orEmpty(), source.placeAddress.orEmpty(), automatic = true)
                wrote = true
                val saved = dao.get(target) ?: error("Automatic save did not complete")
                temporary.approve(candidate.id, saved)
                remember(saved)
                try { notifyChange(target, source.title, old != null) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Saving does not depend on notification permission. */ }
                mutableStatus.value = if (old == null) "Saved automatically" else "Updated automatically"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableStatus.value = "Automatic save failed. The item remains in Review." }
        }
        processChanges()
        if (wrote) consolidateAnnouncements()
    }
    suspend fun restoreNotifications() = gate.withLock {
        for (change in dao.recentChanges()) {
            val item = dao.get(change.targetId) ?: continue
            if (item.state in setOf("ACCEPTED", "CANCELLED", "DISMISSED")) runCatching { notifyChange(item.id, item.title, change.before != null) }
        }
    }
    @Suppress("MissingPermission")
    private suspend fun notifyChange(id: String, title: String, updated: Boolean) {
        val change = dao.change(id) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automatic event changes", NotificationManager.IMPORTANCE_LOW))
        if (!manager.areNotificationsEnabled()) return
        val undo = PendingIntent.getBroadcast(context, 0,
            Intent(context, AutomaticUndoReceiver::class.java).setData(Uri.parse("sidekick://automatic/$id"))
                .putExtra("token", change.token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NOTIFICATION, Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(if (dao.get(id)?.state == "CANCELLED") "SideKick cancelled an event" else if (updated) "SideKick updated an event" else "SideKick saved an event")
            .setContentText(title).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
            .setContentIntent(open).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Undo", undo).build()).build())
    }
    suspend fun undo(id: String, token: String): Boolean = gate.withLock {
        val undone = reminders.undoAutomatic(id, token)
        if (undone) {
            temporary.retire(id)
            context.getSystemService(NotificationManager::class.java).cancel(id, NOTIFICATION)
            mutableStatus.value = "Automatic change undone"
        }
        undone
    }
    companion object { const val CHANNEL = "automatic-events"; const val NOTIFICATION = 31 }
}

class AutomaticUndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.takeIf { it.scheme == "sidekick" && it.host == "automatic" }?.lastPathSegment ?: return
        val token = intent.getStringExtra("token") ?: return
        val pending = goAsync()
        val app = context.applicationContext as SidekickApplication
        app.background.launch {
            try { app.automation.undo(id, token) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the saved item and notification if storage is unavailable. */ }
            finally { pending.finish() }
        }
    }
}
