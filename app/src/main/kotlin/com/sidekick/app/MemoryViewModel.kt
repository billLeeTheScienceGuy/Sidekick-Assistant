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
    fun clearRecentConversation() { container.temporary.clearText() }
    val automaticChanges = combine(dao.observe(), dao.observeChanges()) { items, changes ->
        changes.mapNotNull { change -> runCatching {
            val item = items.singleOrNull { it.id == change.targetId } ?: return@runCatching null
            val expected = com.sidekick.app.data.MemorySnapshot.decode(change.after)
            if (!com.sidekick.app.data.MemorySnapshot.matchesForUndo(item, expected)) return@runCatching null
            RecentAutomaticChange(item.id, change.token, item.title, when {
                item.state == "CANCELLED" -> tr(R.string.cancelled)
                item.state == "DISMISSED" -> tr(R.string.archived)
                change.before == null -> tr(R.string.added)
                else -> tr(R.string.updated)
            })
        }.getOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun undoAutomatic(id: String, token: String) = mutate {
        message.value = if (container.automation.undo(id, token)) tr(R.string.automatic_change_undone)
            else tr(R.string.this_change_was_already_undone_or)
    }
    val guidance = container.guidance.mode
    fun setGuidance(mode: GuidanceMode) = container.guidance.setMode(mode)
    val message = MutableStateFlow(tr(R.string.record_your_voice_or_type_your))
    val busy = MutableStateFlow(false)
    val transcript = MutableStateFlow("")
    private var capturedAt = Instant.now()
    private var capturedZone = ZoneId.systemDefault()
    private var analysisSource: String? = null
    val editError = MutableStateFlow<String?>(null)
    private var importJob: Job? = null

    fun importWav(uri: Uri) {
        if (busy.value) return
        busy.value = true
        message.value = tr(R.string.transcribing_wav_locally)
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
                                require(output.size() + count <= 1_100_000) { tr(R.string.use_a_wav_file_of_at) }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                            ?: error(tr(R.string.could_not_open_audio_file))
                        try { WavAudio.decode(bytes) } finally { bytes.fill(0) }
                    }
                    container.speech.transcribe(audio)
                }
                setTranscript(text, Instant.now())
                message.value = tr(R.string.imported_transcript_ready_check_the_recording)
            } catch (e: TimeoutCancellationException) { message.value = tr(R.string.transcription_timed_out_try_shorter_audio) }
            catch (e: CancellationException) { message.value = tr(R.string.audio_import_cancelled); throw e }
            catch (e: Exception) { message.value = e.message ?: tr(R.string.could_not_transcribe_wav_check_the) }
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
        setTranscript(liveText.value.takeLast(6000), Instant.now())
        analysisSource = "voice:copied"
    }
    fun clearTranscript() { transcript.value = ""; analysisSource = null; container.temporary.clearText() }

    fun analyze() {
        if (busy.value) return
        val origin = analysisSource
        container.risk.inspectMessage(transcript.value)
        val input = container.temporary.prepareChanges(Transcript(transcript.value.trim(), capturedAt, capturedZone, Speaker.USER, true), origin ?: "typed")
        busy.value = true
        importJob = viewModelScope.launch {
            try {
                val count = if (input.text.isBlank()) 0 else withContext(Dispatchers.Default) { container.temporary.propose(input, origin) }
                container.automation.process()
                message.value = if (container.guidance.handsOffMode.value) container.automation.status.value
                    else if (count > 0) trn(R.plurals.temp_proposals_ready, count, count)
                    else tr(R.string.no_new_validated_commitment_found_review)
            } catch (e: CancellationException) { message.value = tr(R.string.analysis_cancelled); throw e }
            catch (e: Exception) { message.value = e.message ?: tr(R.string.could_not_analyze_try_a_shorter) }
            finally { busy.value = false }
        }
    }
    fun addManual() = mutate {
        container.temporary.add(CommitmentEntity(UUID.randomUUID().toString(), tr(R.string.new_commitment), tr(R.string.manually_added),
            Instant.now().toEpochMilli(), ZoneId.systemDefault().id, tr(R.string.temporary_proposal_edit_and_approve_to)))
    }
    fun analyzeSharedText(text: String) = mutate {
        if (!container.guidance.sharedMessages.value) { message.value = tr(R.string.shared_message_analysis_is_disabled_in); return@mutate }
        container.risk.inspect(text)
        val input = container.temporary.prepareChanges(Transcript(text.take(6000), Instant.now(), ZoneId.systemDefault(), Speaker.OTHER, true), "shared")
        val proposals = withContext(Dispatchers.Default) { AnalysisContext.others(input) }
            .map { it.copy(sourceKey = "shared", needsReview = tr(R.string.shared_message_attendance_is_unconfirmed) + it.needsReview) }
        val count = container.temporary.addProposals(proposals)
        container.automation.process()
        message.value = if (container.guidance.handsOffMode.value) container.automation.status.value else trn(R.plurals.shared_suggestions_ready, count, count)
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
                if (proposal?.sourceKey?.startsWith("review:cancel:") == true) {
                    // Accepting another voice's cancellation cancels the saved plan (journaled, with Undo).
                    val saved = dao.get(target)?.takeIf { it.state == "ACCEPTED" } ?: error(tr(R.string.plan_already_changed))
                    container.reminders.changeEvent(saved, null, true)
                    container.temporary.remove(id)
                    onSaved()
                    return@launch
                }
                val targetDraft = container.temporary.drafts.value.find { it.id == target && it.id != id }
                val source = if (targetDraft != null) proposal?.copy(id = target) else proposal
                container.reminders.save(target, title, due, zoneId, source, start, end, informational, placeName, placeAddress)
                dao.get(target)?.let { container.temporary.approve(id, it) }
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { editError.value = e.message ?: tr(R.string.could_not_save_check_the_title) }
        }
    }
    fun dismiss(id: String) = mutate { container.temporary.dismiss(id); container.reminders.mark(id, "DISMISSED") }
    fun done(id: String) = mutate { container.reminders.mark(id, "DONE"); container.temporary.retire(id) }
    fun delete(id: String) = mutate { container.temporary.dismiss(id); container.reminders.delete(id); container.temporary.retire(id) }
    fun deleteAll() = mutate { container.messageInbox.clear(); container.temporary.discard(); container.reminders.deleteAll(); container.database.noticeRecords().deleteAll(); container.guidance.clearLearnedNames() }
    fun reopen() { editError.value = null }
    fun notificationPermissionChanged() { viewModelScope.launch { runCatching { container.reminders.restore() } } }
    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = tr(R.string.could_not_update_local_memory_try) }
        }
    }
}
