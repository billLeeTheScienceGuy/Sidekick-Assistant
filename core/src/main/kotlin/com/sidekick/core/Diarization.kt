package com.sidekick.core

data class SpeakerSpan(val start: Int, val end: Int, val speaker: Int)
data class SpeakerPart(val start: Int, val end: Int, val localSpeaker: Int?, val ambiguous: Boolean)

/** Convert model segments to a complete, bounded partition; never drop audio at a gap or overlap. */
object Diarization {
    fun partition(samples: Int, segments: List<SpeakerSpan>, minimum: Int = 24_000, maximumParts: Int = 4): List<SpeakerPart> {
        require(samples in 1..160_000 && minimum > 0 && maximumParts in 1..8)
        fun unknown() = listOf(SpeakerPart(0, samples, null, true))
        if (segments.isEmpty() || segments.size > 64 || segments.any { it.start < 0 || it.end > samples || it.end <= it.start || it.speaker < 0 }) return unknown()
        val speakers = segments.map { it.speaker }.toSet()
        if (speakers.size == 1) return listOf(SpeakerPart(0, samples, speakers.single(), false))
        val points = (listOf(0, samples) + segments.flatMap { listOf(it.start, it.end) }).distinct().sorted()
        val parts = mutableListOf<SpeakerPart>()
        for ((start, end) in points.zipWithNext()) {
            val active = segments.filter { it.start < end && it.end > start }.map { it.speaker }.toSet()
            val id = active.singleOrNull()
            val previous = parts.lastOrNull()
            if (previous != null && previous.localSpeaker == id && previous.ambiguous == (id == null))
                parts[parts.lastIndex] = previous.copy(end = end)
            else parts += SpeakerPart(start, end, id, id == null)
        }
        // Quiet leading/trailing padding belongs to the adjacent speech region.
        if (parts.size > 1 && parts.first().localSpeaker == null && segments.none { it.start == 0 }) {
            parts[1] = parts[1].copy(start = 0); parts.removeAt(0)
        }
        if (parts.size > 1 && parts.last().localSpeaker == null && segments.none { it.end == samples }) {
            parts[parts.lastIndex - 1] = parts[parts.lastIndex - 1].copy(end = samples); parts.removeAt(parts.lastIndex)
        }
        // Tiny fragments need context for ASR; merging always removes attribution, never guesses it.
        while (parts.size > 1) {
            val index = parts.indexOfFirst { it.end - it.start < minimum }
            if (index < 0) break
            val other = when (index) { 0 -> 1; parts.lastIndex -> index - 1
                else -> if (parts[index - 1].end - parts[index - 1].start <= parts[index + 1].end - parts[index + 1].start) index - 1 else index + 1 }
            val left = minOf(index, other); val right = maxOf(index, other)
            parts[left] = SpeakerPart(parts[left].start, parts[right].end, null, true); parts.removeAt(right)
        }
        return if (parts.size > maximumParts) unknown() else parts
    }
}

data class SessionVoice(val key: String, val label: String)

/** Anonymous, bounded, session-only clustering. It never establishes USER ownership or consent. */
class SessionVoices(private val session: String = java.util.UUID.randomUUID().toString().replace("-", "")) {
    private data class Cluster(val voice: SessionVoice, var vector: FloatArray, var seen: Long)
    private val clusters = mutableListOf<Cluster>()
    private var next = 0
    fun observe(vector: FloatArray, now: Long): SessionVoice? {
        val normalized = VoiceMatch.normalized(vector)
        try {
            clusters.filter { now - it.seen !in 0..900_000 }.toList().forEach { it.vector.fill(0f); clusters.remove(it) }
            val ranked = clusters.map { it to VoiceMatch.similarity(it.vector, normalized) }.sortedByDescending { it.second }
            val best = ranked.firstOrNull()
            if (best != null && best.second >= .75f && best.second - (ranked.getOrNull(1)?.second ?: -1f) >= .12f) {
                val cluster = best.first
                val updated = VoiceMatch.normalized(FloatArray(normalized.size) { .9f * cluster.vector[it] + .1f * normalized[it] })
                cluster.vector.fill(0f); cluster.vector = updated; cluster.seen = now
                return cluster.voice
            }
            if (best != null && best.second >= .45f || clusters.size == 8) return null
            val voice = SessionVoice("spk_${session}_${++next}", "Voice $next")
            clusters += Cluster(voice, normalized.copyOf(), now)
            return voice
        } finally { normalized.fill(0f) }
    }
    fun clear() { clusters.forEach { it.vector.fill(0f) }; clusters.clear() }
}

object VoiceSource {
    fun distinctPeople(left: String?, right: String?): Boolean {
        val a = track(left) ?: return false
        val b = track(right) ?: return false
        return a != b && !a.contains("_unassigned") && !b.contains("_unassigned") &&
            left?.endsWith(":USER") != true && right?.endsWith(":USER") != true
    }
    fun track(source: String?): String? = source?.split(':')?.takeIf { it.size == 4 && it[0] == "voice" }
        ?.get(2)?.takeIf { it.startsWith("spk_") && it.length <= 80 && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
}
