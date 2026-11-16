package com.sidekick.app.speaker

import android.content.Context
import android.os.SystemClock
import com.sidekick.core.*

/** One session's segmentation and anonymous identity; only independent enrollment grants USER. */
class SpeakerTurnEngine(context: Context, profile: VoiceProfile, private val speaker: SpeakerEngine,
    private val enabled: () -> Boolean = { true },
    /** False where other people's voices must not be grouped (ConsentGate.groupingAllowed: Illinois, TX/WA before notice, unknown). */
    private val groupVoices: () -> Boolean = { true }) {
    /**
     * [label] is the anonymous session label ("Voice 2") or "Unassigned voice"; [display] adds a session-only name if one
     * was given. [scores] holds only match scores (safe for logs); [diagnostic] adds the label for the on-screen list.
     */
    data class Attribution(val speaker: Speaker, val key: String, val label: String, val diagnostic: String,
        val scores: String = diagnostic, val display: String = label, val name: String? = null, val grouped: Boolean = false)
    private val diarizer = NeuralDiarizer(context) { profile.model.value }
    private val voices = SessionVoices()
    private val session = java.util.UUID.randomUUID().toString().replace("-", "")
    private var unassigned = 0
    private var previous: String? = null
    var lastSeparationMs: Long = 0
        private set
    var lastError: String? = null
        private set
    /** Number of grouped voices held in memory (0 after [forgetOthers]). */
    val groupedVoices: Int get() = synchronized(voices) { voices.size }
    fun partition(samples: FloatArray): List<SpeakerPart> {
        lastError = null
        if (!enabled() || samples.size < 48_000) { lastSeparationMs = 0; return listOf(SpeakerPart(0, samples.size, null, false)) }
        val begin = SystemClock.elapsedRealtime()
        return try {
            val segments = diarizer.process(samples).map {
                require(it.start.isFinite() && it.end.isFinite())
                SpeakerSpan((it.start * 16000).toInt().coerceIn(0, samples.size),
                    (it.end * 16000).toInt().coerceIn(0, samples.size), it.speaker)
            }
            Diarization.partition(samples.size, segments)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { lastError = "Speaker separation unavailable; attribution withheld."; listOf(SpeakerPart(0, samples.size, null, true)) }
        finally { lastSeparationMs = SystemClock.elapsedRealtime() - begin }
    }
    /** Set by [forgetOthers] from any thread; the speech worker clears the owner's recent anchors before its next use. */
    @Volatile private var anchorsStale = false
    fun classify(samples: FloatArray, part: SpeakerPart): Attribution {
        if (anchorsStale) { anchorsStale = false; speaker.newTurn(); previous = null }
        val vector = if (!part.ambiguous) try { speaker.embedding(samples) } catch (_: IllegalArgumentException) { null } else null
        // Task 10 (a): the signature exists only for this comparison and is zeroed right after it.
        val anonymous = vector?.let { v -> VoiceMatch.useOnce(v) { if (groupVoices()) synchronized(voices) { voices.observe(it, SystemClock.elapsedRealtime()) } else null } }
        val key = anonymous?.key ?: "spk_${session}_unassigned${++unassigned}"
        val label = anonymous?.label ?: if (part.ambiguous) "Overlapping or uncertain speech" else "Unassigned voice"
        // Only evidence of a different voice ends a turn. An unclustered chunk gets a fresh "_unassigned" key,
        // which says nothing about a change; short phrases still need their own profile and recent-voice scores.
        val changed = previous != null && previous != key && anonymous != null && previous?.contains("_unassigned") == false
        if (changed || part.ambiguous) speaker.newTurn()
        val identified = if (part.ambiguous) Speaker.UNKNOWN else speaker.identify(samples)
        previous = key
        val contextKey = if (identified == Speaker.USER) "spk_${session}_owner" else key
        val name = anonymous?.let { synchronized(voices) { voices.nameOf(it.key) } }?.takeIf { identified != Speaker.USER }
        val scores = lastError ?: if (part.ambiguous) "attribution withheld" else speaker.lastDiagnostic
        return Attribution(identified, contextKey, label, "$label · $scores", scores, name ?: label, name, anonymous != null)
    }
    /** Session-only name for a grouped voice; never persisted. Returns false if the voice is gone or the name is invalid. */
    fun nameVoice(key: String, name: String?): Boolean = groupVoices() && synchronized(voices) { voices.name(key, name) }
    /**
     * Clears every grouped voice, session name and recent owner anchor. Called at once when the place becomes
     * biometric STRICT, or NOTICE before notice, and at session end.
     */
    fun forgetOthers() { synchronized(voices) { voices.clear() }; anchorsStale = true }
    fun close() { diarizer.close(); synchronized(voices) { voices.clear() }; speaker.newTurn(); previous = null }
}
