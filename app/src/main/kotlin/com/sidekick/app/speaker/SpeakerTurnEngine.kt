package com.sidekick.app.speaker

import android.content.Context
import android.os.SystemClock
import com.sidekick.core.*

/** One session's segmentation and anonymous identity; only independent enrollment grants USER. */
class SpeakerTurnEngine(context: Context, profile: VoiceProfile, private val speaker: SpeakerEngine,
    private val enabled: () -> Boolean = { true },
    /** False where other people's voices must not be grouped at all (Illinois BIPA). */
    private val groupVoices: () -> Boolean = { true }) {
    data class Attribution(val speaker: Speaker, val key: String, val label: String, val diagnostic: String)
    private val diarizer = NeuralDiarizer(context) { profile.model.value }
    private val voices = SessionVoices()
    private val session = java.util.UUID.randomUUID().toString().replace("-", "")
    private var unassigned = 0
    private var previous: String? = null
    var lastSeparationMs: Long = 0
        private set
    var lastError: String? = null
        private set
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
    fun classify(samples: FloatArray, part: SpeakerPart): Attribution {
        val vector = if (!part.ambiguous) try { speaker.embedding(samples) } catch (_: IllegalArgumentException) { null } else null
        val anonymous = try { vector?.takeIf { groupVoices() }?.let { voices.observe(it, SystemClock.elapsedRealtime()) } } finally { vector?.fill(0f) }
        val key = anonymous?.key ?: "spk_${session}_unassigned${++unassigned}"
        val label = anonymous?.label ?: if (part.ambiguous) "Overlapping or uncertain speech" else "Unassigned voice"
        // Only evidence of a different voice ends a turn. An unclustered chunk gets a fresh "_unassigned" key,
        // which says nothing about a change; short phrases still need their own profile and recent-voice scores.
        val changed = previous != null && previous != key && anonymous != null && previous?.contains("_unassigned") == false
        if (changed || part.ambiguous) speaker.newTurn()
        val identified = if (part.ambiguous) Speaker.UNKNOWN else speaker.identify(samples)
        previous = key
        val contextKey = if (identified == Speaker.USER) "spk_${session}_owner" else key
        return Attribution(identified, contextKey, label, "$label · ${lastError ?: if (part.ambiguous) "attribution withheld" else speaker.lastDiagnostic}")
    }
    fun close() { diarizer.close(); voices.clear(); speaker.newTurn(); previous = null }
}
