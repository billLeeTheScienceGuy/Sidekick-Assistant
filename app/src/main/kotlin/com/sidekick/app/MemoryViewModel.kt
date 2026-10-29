package com.sidekick.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sidekick.app.data.CommitmentEntity
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RecentAutomaticChange(val id: String, val token: String, val title: String, val action: String)

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application as SidekickApplication
    private val dao = container.database.commitments()
    val items = combine(dao.observe(), container.temporary.drafts) { saved, drafts ->
        drafts.filter { draft -> saved.none { it.id == draft.id } } + saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val liveText = container.temporary.text
    val automaticChanges = combine(dao.observe(), dao.observeChanges()) { items, changes ->
        changes.mapNotNull { change -> runCatching {
            val item = items.singleOrNull { it.id == change.targetId } ?: return@runCatching null
            val expected = com.sidekick.app.data.MemorySnapshot.decode(change.after)
            if (!com.sidekick.app.data.MemorySnapshot.matchesForUndo(item, expected)) return@runCatching null
            RecentAutomaticChange(item.id, change.token, item.title, when {
                item.state == "CANCELLED" -> "Cancelled"
                item.state == "DISMISSED" -> "Archived"
                change.before == null -> "Added"
                else -> "Updated"
            })
        }.getOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun undoAutomatic(id: String, token: String) = mutate {
        message.value = if (container.automation.undo(id, token)) "Automatic change undone"
            else "This change was already undone or edited."
    }
    val smartReady = container.intent.ready
    val guidance = container.guidance.mode
    fun setGuidance(mode: GuidanceMode) = container.guidance.setMode(mode)
    val message = MutableStateFlow("Record your voice or type your own words.")
    val busy = MutableStateFlow(false)
    val transcript = MutableStateFlow("")
    private var capturedAt = Instant.now()
    private var capturedZone = ZoneId.systemDefault()
    private var analysisSource: String? = null
    val editError = MutableStateFlow<String?>(null)
    private var importJob: Job? = null

    fun importIntentModel(uri: Uri) {
        if (busy.value) return
        busy.value = true; message.value = "Verifying and installing the local Qwen model…"
        importJob = viewModelScope.launch {
            try { container.intent.importModel(uri); message.value = "Qwen installed. Find commitments now uses local smart analysis." }
            catch (e: CancellationException) { message.value = "Model import cancelled."; throw e }
            catch (e: Exception) { message.value = e.message ?: "Model import failed." }
            finally { busy.value = false }
        }
    }

    fun importWav(uri: Uri) {
        if (busy.value) return
        busy.value = true
        message.value = "Transcribing WAV locally…"
        importJob = viewModelScope.launch {
            try {
                val text = withTimeout(120_000) {
                    val audio = withContext(Dispatchers.IO) {
                        val bytes = container.contentResolver.openInputStream(uri)?.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 1_100_000) { "Use a WAV file of at most 30 seconds." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                            ?: error("Could not open audio file.")
                        try { WavAudio.decode(bytes) } finally { bytes.fill(0) }
                    }
                    container.speech.transcribe(audio)
                }
                setTranscript(text, Instant.now())
                message.value = "Imported transcript ready. Check the recording's dates before saving commitments."
            } catch (e: TimeoutCancellationException) { message.value = "Transcription timed out. Try shorter audio." }
            catch (e: CancellationException) { message.value = "Audio import cancelled."; throw e }
            catch (e: Exception) { message.value = e.message ?: "Could not transcribe WAV. Check the format and bundled model." }
            finally { busy.value = false }
        }
    }
    fun cancelImport() { importJob?.cancel() }

    fun setTranscript(text: String, captured: Instant? = null, zone: ZoneId? = null) {
        if (text.length > 10_000) return
        if (transcript.value.isEmpty() || captured != null) {
            capturedAt = captured ?: Instant.now(); capturedZone = zone ?: ZoneId.systemDefault()
            analysisSource = null
        }
        transcript.value = text
    }
    fun copyLiveText() {
        setTranscript(liveText.value.takeLast(if (smartReady.value) 4000 else 6000), Instant.now())
        analysisSource = "voice:copied"
    }
    fun clearTranscript() { transcript.value = ""; analysisSource = null; container.temporary.clearText() }

    fun analyze() {
        if (busy.value) return
        val origin = analysisSource
        val input = container.temporary.prepareChanges(Transcript(transcript.value.trim(), capturedAt, capturedZone, Speaker.USER, true), origin ?: "typed")
        busy.value = true
        importJob = viewModelScope.launch {
            try {
                val count = if (input.text.isBlank()) 0 else if (smartReady.value) {
                    val proposals = withTimeout(90_000) { container.intent.extract(input) }
                    container.temporary.addProposals(proposals.map { it.copy(sourceKey = origin) })
                } else withContext(Dispatchers.Default) { container.temporary.propose(input, origin) }
                container.automation.process()
                message.value = if (container.guidance.handsOffMode.value) container.automation.status.value
                    else if (count > 0) "$count temporary proposal(s) ready for review. Approve to save."
                    else "No new validated commitment found. Review existing proposals or add one manually."
                container.intent.notice.value?.let { message.value += " " + it }
            } catch (_: TimeoutCancellationException) {
                container.temporary.propose(input, origin); container.automation.process()
                message.value = "Smart analysis timed out. Used local rules; some plans may be missed."
            }
            catch (e: CancellationException) { message.value = "Analysis cancelled."; throw e }
            catch (e: Exception) { message.value = e.message ?: "Could not analyze. Try a shorter note." }
            finally { busy.value = false }
        }
    }
    fun addManual() = mutate {
        container.temporary.add(CommitmentEntity(UUID.randomUUID().toString(), "New commitment", "Manually added",
            Instant.now().toEpochMilli(), ZoneId.systemDefault().id, "Temporary proposal. Edit and approve to save."))
    }
    fun analyzeSharedText(text: String) = mutate {
        if (!container.guidance.sharedMessages.value) { message.value = "Shared-message analysis is disabled in Settings."; return@mutate }
        val input = container.temporary.prepareChanges(Transcript(text.take(6000), Instant.now(), ZoneId.systemDefault(), Speaker.OTHER, true), "shared")
        val proposals = withContext(Dispatchers.Default) { ActivityTiming.extract(input, informationalOnly = true) }
            .map { it.copy(sourceKey = "shared", needsReview = "Shared message: attendance is unconfirmed. " + it.needsReview) }
        val count = container.temporary.addProposals(proposals)
        container.automation.process()
        message.value = if (container.guidance.handsOffMode.value) container.automation.status.value else "$count shared-message suggestion(s) ready for review. Nothing was saved automatically."
    }
    fun save(id: String, title: String, time: String, zoneId: String, activityStart: String = "", activityEnd: String = "",
        informational: Boolean? = null, placeName: String? = null, placeAddress: String? = null, onSaved: () -> Unit) {
        editError.value = null
        viewModelScope.launch {
            try {
                val due = if (time.isBlank()) null else TimeResolver.parseEditor(time, ZoneId.of(zoneId)).toEpochMilli()
                val proposal = container.temporary.drafts.value.find { it.id == id }
                val start = if (activityStart.isBlank()) null else TimeResolver.parseEditor(activityStart, ZoneId.of(zoneId)).toEpochMilli()
                val end = if (activityEnd.isBlank()) null else TimeResolver.parseEditor(activityEnd, ZoneId.of(zoneId)).toEpochMilli()
                val target = container.temporary.updateTarget(id) ?: id
                val targetDraft = container.temporary.drafts.value.find { it.id == target && it.id != id }
                val source = if (targetDraft != null) proposal?.copy(id = target) else proposal
                container.reminders.save(target, title, due, zoneId, source, start, end, informational, placeName, placeAddress)
                dao.get(target)?.let { container.temporary.approve(id, it) }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { editError.value = e.message ?: "Could not save. Check the title and date/time." }
        }
    }
    fun dismiss(id: String) = mutate { container.temporary.dismiss(id); container.reminders.mark(id, "DISMISSED") }
    fun done(id: String) = mutate { container.reminders.mark(id, "DONE"); container.temporary.retire(id) }
    fun delete(id: String) = mutate { container.temporary.dismiss(id); container.reminders.delete(id); container.temporary.retire(id) }
    fun deleteAll() = mutate { container.messageInbox.clear(); container.temporary.discard(); container.reminders.deleteAll() }
    fun reopen() { editError.value = null }
    fun notificationPermissionChanged() { viewModelScope.launch { runCatching { container.reminders.restore() } } }
    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Could not update local memory. Try again." }
        }
    }
}
