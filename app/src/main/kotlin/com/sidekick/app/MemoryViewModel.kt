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
    val items = dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow("Record your voice or type your own words.")
    val busy = MutableStateFlow(false)
    val transcript = MutableStateFlow("")
    private var capturedAt = Instant.now()
    private var capturedZone = ZoneId.systemDefault()
    val editError = MutableStateFlow<String?>(null)
    private var importJob: Job? = null

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
    fun clearTranscript() { transcript.value = "" }

    fun analyze() {
        if (busy.value) return
        val input = Transcript(transcript.value.trim(), capturedAt, capturedZone, Speaker.USER, true)
        busy.value = true
        viewModelScope.launch {
            try {
                val proposals = withContext(Dispatchers.Default) { container.pipeline.process(input) }
                val existing = dao.all().map { it.evidence.lowercase() to it.capturedAt }.toSet()
                val fresh = proposals.filter { (it.evidence.lowercase() to it.capturedAt.toEpochMilli()) !in existing }
                dao.insertAll(fresh.map {
                    CommitmentEntity(UUID.randomUUID().toString(), it.title, it.evidence, it.capturedAt.toEpochMilli(),
                        it.zoneId.id, "Review the wording and reminder time before accepting.")
                })
                message.value = when {
                    fresh.isNotEmpty() -> "${fresh.size} proposal(s) saved for review. Nothing has been scheduled."
                    proposals.isNotEmpty() -> "These proposals are already in your memory."
                    else -> "No simple promise found. You can add a commitment manually below."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Could not analyze or save. Try again." }
            finally { busy.value = false }
        }
    }
    fun addManual() = mutate {
        dao.insertAll(listOf(CommitmentEntity(UUID.randomUUID().toString(), "New commitment", "Manually added",
            Instant.now().toEpochMilli(), ZoneId.systemDefault().id, "Edit the title and optional reminder time.")))
    }
    fun save(id: String, title: String, time: String, zoneId: String, onSaved: () -> Unit) {
        editError.value = null
        viewModelScope.launch {
            try {
                val due = if (time.isBlank()) null else TimeResolver.parseEditor(time, ZoneId.of(zoneId)).toEpochMilli()
                container.reminders.save(id, title, due, zoneId)
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { editError.value = e.message ?: "Could not save. Check the title and date/time." }
        }
    }
    fun dismiss(id: String) = mutate { container.reminders.mark(id, "DISMISSED") }
    fun done(id: String) = mutate { container.reminders.mark(id, "DONE") }
    fun delete(id: String) = mutate { container.reminders.delete(id) }
    fun deleteAll() = mutate { container.reminders.deleteAll() }
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
