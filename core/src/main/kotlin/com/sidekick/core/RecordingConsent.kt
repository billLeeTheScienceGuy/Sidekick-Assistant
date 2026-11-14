package com.sidekick.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where a conversation happens decides whose words SideKick may keep. Product rules from public secondary
 * sources, not legal advice (see the "SideKick: Consent, Law, Performance & Test Plan" document).
 *
 * Every uncertainty resolves to the stricter rule: no fix, a stale fix, a fix whose accuracy circle touches an
 * all-party state, or a place outside the bundled US borders all count as ALL_PARTY.
 */
object RecordingJurisdiction {
    enum class Rule { ONE_PARTY, ALL_PARTY }
    data class Place(val code: String?, val name: String, val rule: Rule, val reason: String) {
        /** Illinois BIPA: never group other people's voices there. */
        val groupsOtherVoices: Boolean get() = code != "IL"
    }

    const val MAX_FIX_AGE_MS = 30 * 60_000L
    private const val MARGIN_METERS = 500.0 // the 1:20M borders are generalised; never trust them to the metre

    private class State(val code: String, val name: String, val strict: Boolean, val rings: List<DoubleArray>) {
        val minLon = rings.minOf { r -> (r.indices step 2).minOf { r[it] } }
        val maxLon = rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
        val minLat = rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
        val maxLat = rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }
    }

    private val states: List<State> by lazy {
        val text = RecordingJurisdiction::class.java.getResourceAsStream("/us-state-borders.txt")
            ?.bufferedReader()?.use { it.readText() } ?: error("us-state-borders.txt missing from the core resources")
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (code, name, rule, rings) = line.split('|', limit = 4)
            State(code, name, rule != "one", rings.split(';').map { ring -> ring.split(',').map(String::toDouble).toDoubleArray() })
        }.toList()
    }

    fun unknown(reason: String) = Place(null, "Unknown location", Rule.ALL_PARTY, reason)

    fun locate(lat: Double?, lon: Double?, accuracyMeters: Double?, fixAgeMs: Long?): Place {
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return unknown("No location fix")
        if (fixAgeMs == null || fixAgeMs !in 0..MAX_FIX_AGE_MS) return unknown("Location older than 30 minutes")
        val radius = max(accuracyMeters?.takeIf { it.isFinite() && it >= 0 } ?: 5_000.0, 0.0) + MARGIN_METERS
        val inside = states.firstOrNull { contains(it, lat, lon) }
            ?: return Place(null, "Outside the US", Rule.ALL_PARTY, "Outside the bundled US borders: strictest rule")
        if (inside.strict) return Place(inside.code, inside.name, Rule.ALL_PARTY, "${inside.name} requires every party's consent")
        val near = states.filter { it.strict && it !== inside }.firstOrNull { distanceMeters(it, lat, lon, radius) <= radius }
        if (near != null) return Place(inside.code, inside.name, Rule.ALL_PARTY, "Within ${radius.toInt()} m of ${near.name}: stricter rule")
        return Place(inside.code, inside.name, Rule.ONE_PARTY, "${inside.name} allows one party's consent")
    }

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

/**
 * One listening session's consent state. The owner's own words are always kept. Other people's words are kept
 * in a one-party place only while the owner is taking part (spoke within [ownerWindowMs]), and in an all-party
 * place only after the spoken notice. Everything else is dropped on arrival.
 */
class ConsentGate(place: RecordingJurisdiction.Place, private val ownerWindowMs: Long = 120_000L) {
    var place: RecordingJurisdiction.Place = place
        private set
    private var ownerLastMs: Long? = null
    var noticeAtMs: Long? = null
        private set
    fun moveTo(next: RecordingJurisdiction.Place) { place = next }
    fun ownerSpoke(nowMs: Long) { ownerLastMs = nowMs }
    fun noticeGiven(nowMs: Long) { noticeAtMs = nowMs }
    fun keepOthers(nowMs: Long): Boolean = when (place.rule) {
        RecordingJurisdiction.Rule.ONE_PARTY -> ownerLastMs?.let { nowMs - it in 0..ownerWindowMs } == true || noticeAtMs != null
        RecordingJurisdiction.Rule.ALL_PARTY -> noticeAtMs != null
    }
    fun keep(speaker: Speaker, nowMs: Long): Boolean = speaker == Speaker.USER || keepOthers(nowMs)
    /** Short status for the listening screen. */
    fun status(nowMs: Long): String = when {
        place.rule == RecordingJurisdiction.Rule.ALL_PARTY && noticeAtMs == null -> "${place.name}: only your words are kept until you announce note-taking"
        place.rule == RecordingJurisdiction.Rule.ALL_PARTY -> "${place.name}: notice given, conversation kept"
        keepOthers(nowMs) -> "${place.name}: you're in the conversation, others' words kept as information"
        else -> "${place.name}: only your words are kept until you speak"
    }
}
