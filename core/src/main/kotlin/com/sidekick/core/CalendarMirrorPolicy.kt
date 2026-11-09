package com.sidekick.core

import java.security.MessageDigest
import java.time.ZoneId

/** The only fields SideKick manages in an explicitly selected writable calendar. */
data class CalendarMirrorFields(
    val calendarId: Long, val title: String, val start: Long, val end: Long, val zone: String,
    val location: String, val description: String, val marker: String, val ownerPackage: String,
    val availability: Int, val status: Int = 1, val allDay: Boolean = false,
    val rrule: String = "", val rdate: String = "", val exrule: String = "", val exdate: String = "",
    val hasGuests: Boolean = false,
) {
    fun fingerprint(): String {
        val normalizedZone = runCatching { ZoneId.of(zone).normalized().id }.getOrDefault(zone)
        val fields = listOf(calendarId.toString(), title, start.toString(), end.toString(), normalizedZone, location,
            description, marker, ownerPackage, availability.toString(), status.toString(), allDay.toString(),
            rrule, rdate, exrule, exdate, hasGuests.toString())
        val canonical = fields.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

object CalendarMirrorPolicy {
    enum class Action { UPSERT, KEEP, REMOVE, WAIT }
    fun action(state: String?, start: Long?, end: Long?, zone: String?, informational: Boolean,
        includeInformational: Boolean, linked: Boolean, now: Long): Action {
        if (state == null || state in setOf("CANCELLED", "DISMISSED", "PROPOSED")) return Action.REMOVE
        if (state == "DONE") return Action.KEEP
        if (state != "ACCEPTED") return Action.WAIT
        if (informational && !includeInformational) return Action.REMOVE
        if (start == null || end == null) return if (linked) Action.REMOVE else Action.WAIT
        if (runCatching { CalendarOverlap.validate(start, end); ZoneId.of(zone); true }.getOrDefault(false).not()) return Action.WAIT
        return if (!linked && end < now) Action.WAIT else Action.UPSERT
    }
}
