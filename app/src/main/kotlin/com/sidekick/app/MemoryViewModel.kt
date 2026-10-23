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

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application as SidekickApplication
    private val dao = container.database.commitments()
    val items = combine(dao.observe(), container.temporary.drafts) { saved, drafts ->
        drafts.filter { draft -> saved.none { it.id == draft.id } } + saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val liveText = container.temporary.text
    val smartReady = container.intent.ready
    val message = MutableStateFlow("Record your voice or type your own words.")
    val busy = MutableStateFlow(false)
    val transcript = MutableStateFlow("")
    private var capturedAt = Instant.now()
    private var capturedZone = ZoneId.systemDefault()
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
        }
        transcript.value = text
    }
    fun clearTranscript() { transcript.value = ""; container.temporary.clearText() }

    fun analyze() {
        if (busy.value) return
        val input = Transcript(transcript.value.trim(), capturedAt, capturedZone, Speaker.USER, true)
        busy.value = true
        importJob = viewModelScope.launch {
            try {
                val count = if (smartReady.value) {
                    val proposals = withTimeout(90_000) { container.intent.extract(input) }
                    container.temporary.addProposals(proposals)
                } else withContext(Dispatchers.Default) { container.temporary.propose(input) }
                message.value = if (count > 0) "$count temporary proposal(s) ready for review. Approve to save."
                    else "No new validated commitment found. Review existing proposals or add one manually."
            } catch (_: TimeoutCancellationException) { message.value = "Local analysis timed out. Try a shorter note." }
            catch (e: CancellationException) { message.value = "Analysis cancelled."; throw e }
            catch (e: Exception) { message.value = e.message ?: "Could not analyze. Try a shorter note." }
            finally { busy.value = false }
        }
    }
    fun addManual() = mutate {
        container.temporary.add(CommitmentEntity(UUID.randomUUID().toString(), "New commitment", "Manually added",
            Instant.now().toEpochMilli(), ZoneId.systemDefault().id, "Temporary proposal. Edit and approve to save."))
    }
    fun save(id: String, title: String, time: String, zoneId: String, onSaved: () -> Unit) {
        editError.value = null
        viewModelScope.launch {
            try {
                val due = if (time.isBlank()) null else TimeResolver.parseEditor(time, ZoneId.of(zoneId)).toEpochMilli()
                val proposal = container.temporary.drafts.value.find { it.id == id }
                container.reminders.save(id, title, due, zoneId, proposal)
                container.temporary.remove(id)
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { editError.value = e.message ?: "Could not save. Check the title and date/time." }
        }
    }
    fun dismiss(id: String) = mutate { container.temporary.remove(id); container.reminders.mark(id, "DISMISSED") }
    fun done(id: String) = mutate { container.reminders.mark(id, "DONE") }
    fun delete(id: String) = mutate { container.temporary.remove(id); container.reminders.delete(id) }
    fun deleteAll() = mutate { container.temporary.discard(); container.reminders.deleteAll() }
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
