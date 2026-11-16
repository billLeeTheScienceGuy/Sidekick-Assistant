package com.sidekick.core

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where a conversation happens decides whose words SideKick may keep and whether other people's voices may be
 * grouped. Two independent product rules from public secondary sources (see docs/PRIVACY_POLICY.md), not legal advice:
 *  - recording: one-party or all-party consent to record a conversation;
 *  - biometric: none, notice (Texas CUBI, Washington RCW 19.375) or strict (Illinois BIPA).
 *
 * Every uncertainty resolves to the strictest rule. The containing state and every state whose border lies within the
 * buffer radius are collected, and the strictest recording and biometric rules are taken independently across them.
 * No fix, a stale fix, a place outside the bundled borders or a lookup error give ALL_PARTY + STRICT.
 */
object RecordingJurisdiction {
    enum class Rule { ONE_PARTY, ALL_PARTY }
    enum class BiometricRule { NONE, NOTICE, STRICT }

    data class Place(
        val code: String?,
        val name: String,
        val recording: Rule,
        val biometric: BiometricRule,
        val reason: String,
        /** State codes near the position that made either rule stricter than the containing state's. */
        val forcedBy: List<String> = emptyList(),
    ) {
        /** Illinois BIPA (and anywhere near it, or anywhere unknown): never group other people's voices. */
        val groupsOtherVoices: Boolean get() = biometric != BiometricRule.STRICT
        /** True if either rule is stricter here than at [other]. */
        fun stricterThan(other: Place): Boolean = recording > other.recording || biometric > other.biometric
    }

    const val MAX_FIX_AGE_MS = 30 * 60_000L
    /** Smallest buffer with precise (fine) location. */
    const val PRECISE_FLOOR_METERS = 1_000.0
    /** Smallest buffer with approximate (coarse) location or unknown accuracy. */
    const val COARSE_FLOOR_METERS = 3_000.0
    /** Android's approximate location is snapped to a ~2 km grid that the reported accuracy may not include. */
    const val COARSE_ASSUMED_ACCURACY_METERS = 2_000.0
    /** The bundled Census 1:500k borders are simplified to ~100 m; never trust them to the metre. */
    const val BORDER_MARGIN_METERS = 500.0

    /**
     * Buffer radius around a fix. Precise: max(accuracy, 1 km) + margin (~1.5–2 km typical).
     * Coarse or unknown accuracy: the 3 km floor is added on top of the reported (or assumed 2 km) accuracy, because the
     * approximate-location offset is not part of the reported accuracy; ~5.5 km typical. This is stricter than
     * max(accuracy, floor) and keeps "coarse 2 km accuracy, 4 km from Illinois" strict.
     */
    fun bufferMeters(accuracyMeters: Double?, precise: Boolean): Double {
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it >= 0 }
        return if (precise && accuracy != null) max(accuracy, PRECISE_FLOOR_METERS) + BORDER_MARGIN_METERS
        else (accuracy ?: COARSE_ASSUMED_ACCURACY_METERS) + COARSE_FLOOR_METERS + BORDER_MARGIN_METERS
    }

    private class State(val code: String, val name: String, val recording: Rule, val biometric: BiometricRule, val rings: List<DoubleArray>) {
        val minLon = rings.minOf { r -> (r.indices step 2).minOf { r[it] } }
        val maxLon = rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
        val minLat = rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
        val maxLat = rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }
    }

    /** Unknown values in the data resolve to the strictest rule. */
    fun parseRecording(value: String): Rule = if (value == "one") Rule.ONE_PARTY else Rule.ALL_PARTY
    fun parseBiometric(value: String): BiometricRule = when (value) {
        "none" -> BiometricRule.NONE
        "notice" -> BiometricRule.NOTICE
        else -> BiometricRule.STRICT
    }

    private val states: List<State> by lazy {
        val text = RecordingJurisdiction::class.java.getResourceAsStream("/us-state-borders.txt")
            ?.bufferedReader()?.use { it.readText() } ?: error("us-state-borders.txt missing from the core resources")
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (code, name, recording, biometric, rings) = line.split('|', limit = 5)
            State(code, name, parseRecording(recording), parseBiometric(biometric),
                rings.split(';').map { ring -> ring.split(',').map(String::toDouble).toDoubleArray() })
        }.toList()
    }

    /** Rules for a state code from the bundled data (null if unknown). */
    fun rulesFor(code: String): Pair<Rule, BiometricRule>? = states.firstOrNull { it.code == code }?.let { it.recording to it.biometric }

    fun unknown(reason: String) = Place(null, "Unknown location", Rule.ALL_PARTY, BiometricRule.STRICT, reason)
    /** The "Treat every place as the strictest state" setting: no location is requested at all. */
    fun strictestEverywhere() = Place(null, "Strictest rules", Rule.ALL_PARTY, BiometricRule.STRICT, "Strictest rules everywhere (setting)")
    /** A phone call can reach someone in any state. */
    fun duringCall() = Place(null, "Phone call", Rule.ALL_PARTY, BiometricRule.STRICT, "Call detected: strictest rule")

    fun locate(lat: Double?, lon: Double?, accuracyMeters: Double?, fixAgeMs: Long?, precise: Boolean = false): Place = try {
        locateOrThrow(lat, lon, accuracyMeters, fixAgeMs, precise)
    } catch (e: Exception) {
        unknown("Location lookup failed")
    }

    private fun locateOrThrow(lat: Double?, lon: Double?, accuracyMeters: Double?, fixAgeMs: Long?, precise: Boolean): Place {
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return unknown("No location fix")
        if (fixAgeMs == null || fixAgeMs !in 0..MAX_FIX_AGE_MS) return unknown("Location older than 30 minutes")
        val radius = bufferMeters(accuracyMeters, precise)
        val inside = states.firstOrNull { contains(it, lat, lon) }
            ?: return Place(null, "Outside the US", Rule.ALL_PARTY, BiometricRule.STRICT, "Outside the bundled US borders: strictest rule")
        val near = states.filter { it !== inside }.mapNotNull { s -> distanceMeters(s, lat, lon, radius).takeIf { it <= radius }?.let { s to it } }
        val recording = (near.map { it.first.recording } + inside.recording).max()
        val biometric = (near.map { it.first.biometric } + inside.biometric).max()
        val forcing = near.filter { (s, _) -> (s.recording > inside.recording && s.recording == recording) || (s.biometric > inside.biometric && s.biometric == biometric) }
            .sortedBy { it.second }
        val reason = if (forcing.isEmpty()) "${inside.name}: " + effects(recording, biometric)
        else {
            val km = max(1, ceil(forcing.first().second / 1000.0).toInt())
            val names = forcing.joinToString(" and ") { it.first.name }
            val raised = listOfNotNull(
                if (recording > inside.recording) "all-party rule" else null,
                when { biometric <= inside.biometric -> null; biometric == BiometricRule.STRICT -> "no voice grouping"; else -> "voice notice needed" },
            )
            "Within $km km of $names: " + raised.joinToString(", ")
        }
        return Place(inside.code, inside.name, recording, biometric, reason, forcing.map { it.first.code })
    }

    private fun effects(recording: Rule, biometric: BiometricRule): String = listOfNotNull(
        if (recording == Rule.ALL_PARTY) "all-party rule" else "one-party rule",
        when (biometric) { BiometricRule.NONE -> null; BiometricRule.NOTICE -> "voice notice needed"; BiometricRule.STRICT -> "no voice grouping" },
    ).joinToString(", ")

    private fun contains(s: State, lat: Double, lon: Double): Boolean {
        if (lon < s.minLon || lon > s.maxLon || lat < s.minLat || lat > s.maxLat) return false
        var inside = false // even-odd over every ring also handles holes
        for (r in s.rings) {
            var j = r.size - 2
            var i = 0
            while (i < r.size) {
                val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i; i += 2
            }
        }
        return inside
    }

    /** Distance to a state's border (0 inside); equirectangular metres, exact enough at a few kilometres. */
    private fun distanceMeters(s: State, lat: Double, lon: Double, limit: Double): Double {
        val degLat = limit / 111_320.0
        val degLon = limit / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        if (lon < s.minLon - degLon || lon > s.maxLon + degLon || lat < s.minLat - degLat || lat > s.maxLat + degLat) return Double.MAX_VALUE
        if (contains(s, lat, lon)) return 0.0
        val kx = 111_320.0 * cos(Math.toRadians(lat)); val ky = 111_320.0
        var best = Double.MAX_VALUE
        for (r in s.rings) {
            var i = 0
            while (i + 3 < r.size) {
                val ax = (r[i] - lon) * kx; val ay = (r[i + 1] - lat) * ky
                val bx = (r[i + 2] - lon) * kx; val by = (r[i + 3] - lat) * ky
                val dx = bx - ax; val dy = by - ay; val len = dx * dx + dy * dy
                val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
                val px = ax + t * dx; val py = ay + t * dy
                best = minOf(best, sqrt(px * px + py * py))
                i += 2
            }
        }
        return best
    }
}

/** The spoken notice. Bump [VERSION] whenever either line changes; the notice record stores it. */
object ConsentNotice {
    const val VERSION = 2
    const val BASE = "Just so you know, SideKick is taking notes on this conversation."
    const val VOICE = "It briefly analyzes voices to tell speakers apart. Nothing about anyone's voice is saved."
    fun text(includeVoice: Boolean) = if (includeVoice) "$BASE $VOICE" else BASE
}

enum class NoticeMethod { SPOKEN, SPOKEN_AUTO, ATTESTED }

/** What a notice covered, kept per session (no audio, no voice data). Washington treats a recorded announcement as consent. */
data class NoticeRecord(
    val atEpochMs: Long,
    val stateCodes: List<String>,
    val recording: RecordingJurisdiction.Rule,
    val biometric: RecordingJurisdiction.BiometricRule,
    val textVersion: Int,
    val includesVoice: Boolean,
    val method: NoticeMethod,
)

/**
 * One listening session's consent state. The owner's own words are always kept. Other people's words are kept
 * in a one-party place only while the owner is taking part (spoke within [ownerWindowMs]), and in an all-party
 * place only after the spoken notice. Everything else is dropped on arrival.
 *
 * Voice grouping (anonymous "Voice N" labels and session names) is allowed with biometric NONE, or with NOTICE after a
 * notice that included the voice line; never with STRICT. [voiceNoticeEverywhere] (setting) requires the voice line
 * before grouping in every state.
 *
 * A notice covers one conversation, not a whole (possibly all-day) session: it expires after [conversationGapMs] with no
 * speech from other people, and in any case [noticeMaxAgeMs] after it was given. Call [expireIfStale] regularly.
 */
class ConsentGate(
    place: RecordingJurisdiction.Place,
    private val ownerWindowMs: Long = 120_000L,
    @Volatile var voiceNoticeEverywhere: Boolean = false,
    private val conversationGapMs: Long = CONVERSATION_GAP_MS,
    private val noticeMaxAgeMs: Long = NOTICE_MAX_AGE_MS,
) {
    companion object {
        /** No speech from other people this long: the conversation the notice covered is over. Matches SessionVoices. */
        const val CONVERSATION_GAP_MS = SessionVoices.KEEP_MS
        /** A notice never lasts longer than this, even in one continuous meeting. */
        const val NOTICE_MAX_AGE_MS = 2 * 60 * 60_000L
        const val EXPIRED_GAP = "conversation ended"
        const val EXPIRED_MAX_AGE = "2 hours passed"
    }
    /** When someone other than the owner last spoke. */
    @Volatile var othersLastMs: Long? = null
        private set
    /** Why the last notice ended ("conversation ended", "2 hours passed"); cleared by a new notice. */
    @Volatile var noticeExpired: String? = null
        private set
    @Volatile var place: RecordingJurisdiction.Place = place
        private set
    @Volatile private var ownerLastMs: Long? = null
    @Volatile var noticeAtMs: Long? = null
        private set
    /** Where the current notice was given; a move to a stricter place clears it. */
    @Volatile var noticePlace: RecordingJurisdiction.Place? = null
        private set
    @Volatile var noticeIncludedVoice: Boolean = false
        private set

    /** Returns true if the move cleared the notice (the new place is stricter in either rule than where it was given). */
    @Synchronized fun moveTo(next: RecordingJurisdiction.Place): Boolean {
        place = next
        val given = noticePlace ?: return false
        if (!next.stricterThan(given)) return false
        noticeAtMs = null; noticePlace = null; noticeIncludedVoice = false
        return true
    }
    fun ownerSpoke(nowMs: Long) { ownerLastMs = nowMs }
    /** Someone other than the owner spoke (kept or dropped): the conversation is still going. */
    fun othersSpoke(nowMs: Long) { othersLastMs = nowMs }
    /** Ends a notice whose conversation is over or that is too old. Returns the reason if it just expired. */
    @Synchronized fun expireIfStale(nowMs: Long): String? {
        val at = noticeAtMs ?: return null
        val lastTalk = maxOf(at, othersLastMs ?: at)
        val reason = when {
            nowMs - at > noticeMaxAgeMs -> EXPIRED_MAX_AGE
            nowMs - lastTalk > conversationGapMs -> EXPIRED_GAP
            else -> return null
        }
        noticeAtMs = null; noticePlace = null; noticeIncludedVoice = false; noticeExpired = reason
        return reason
    }
    /** [includesVoice]: the notice included the voice-analysis line (spoken, or attested "incl. voice analysis"). */
    @Synchronized fun noticeGiven(nowMs: Long, includesVoice: Boolean = true) {
        noticeAtMs = nowMs; noticePlace = place; noticeIncludedVoice = includesVoice; noticeExpired = null
    }

    /** The voice-analysis line belongs in the spoken notice here. */
    /** The voice notice must come before voice grouping here (TX, WA, or the "every state" setting). */
    fun voiceLineRequired(): Boolean = place.biometric != RecordingJurisdiction.BiometricRule.NONE || voiceNoticeEverywhere
    /** The spoken notice always includes the voice-analysis line, in every state (product decision, Nov 16, 2026). */
    fun noticeText(): String = ConsentNotice.text(true)
    private fun voiceNoticeGiven() = noticeAtMs != null && noticeIncludedVoice

    fun groupingAllowed(): Boolean = when (place.biometric) {
        RecordingJurisdiction.BiometricRule.STRICT -> false
        RecordingJurisdiction.BiometricRule.NOTICE -> voiceNoticeGiven()
        RecordingJurisdiction.BiometricRule.NONE -> !voiceNoticeEverywhere || voiceNoticeGiven()
    }

    fun keepOthers(nowMs: Long): Boolean = when (place.recording) {
        RecordingJurisdiction.Rule.ONE_PARTY -> ownerLastMs?.let { nowMs - it in 0..ownerWindowMs } == true || noticeAtMs != null
        RecordingJurisdiction.Rule.ALL_PARTY -> noticeAtMs != null
    }
    fun keep(speaker: Speaker, nowMs: Long): Boolean {
        if (speaker != Speaker.USER) { expireIfStale(nowMs); othersSpoke(nowMs) }
        return speaker == Speaker.USER || keepOthers(nowMs)
    }
    /** Naming a voice for this session: grouping allowed and others' words may be kept. */
    fun namingAllowed(nowMs: Long): Boolean = groupingAllowed() && keepOthers(nowMs)
    /** Before notice in an all-party place, owner matching uses the stricter thresholds and no short phrases. */
    fun strictOwnerMatch(): Boolean = place.recording == RecordingJurisdiction.Rule.ALL_PARTY && noticeAtMs == null

    /** Show Announce / "I told everyone": recording notice missing, or the voice line is required and missing. */
    fun noticeNeeded(): Boolean =
        (place.recording == RecordingJurisdiction.Rule.ALL_PARTY && noticeAtMs == null) ||
            (place.biometric == RecordingJurisdiction.BiometricRule.NOTICE && !voiceNoticeGiven()) ||
            (voiceNoticeEverywhere && place.biometric == RecordingJurisdiction.BiometricRule.NONE && !voiceNoticeGiven())

    /** Button label for the owner's attestation. */
    fun attestLabel(): String = "I told everyone (incl. voice analysis)"

    fun record(nowEpochMs: Long, method: NoticeMethod): NoticeRecord = NoticeRecord(nowEpochMs,
        listOfNotNull(place.code) + place.forcedBy, place.recording, place.biometric, ConsentNotice.VERSION, noticeIncludedVoice, method)

    /** Short status for the listening screen. */
    fun status(nowMs: Long): String {
        val voice = when {
            place.biometric == RecordingJurisdiction.BiometricRule.STRICT -> " · no voice grouping"
            !groupingAllowed() -> " · voice grouping after notice"
            else -> ""
        }
        val words = when {
            place.recording == RecordingJurisdiction.Rule.ALL_PARTY && noticeAtMs == null -> "only your words are kept until you announce note-taking"
            place.recording == RecordingJurisdiction.Rule.ALL_PARTY -> "notice given, conversation kept"
            keepOthers(nowMs) -> "you're in the conversation, others' words kept as information"
            else -> "only your words are kept until you speak"
        }
        val expired = noticeExpired?.takeIf { noticeAtMs == null }?.let { " · notice expired ($it)" } ?: ""
        return "${place.name}: $words$voice$expired"
    }
}
