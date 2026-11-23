package com.sidekick.core

import java.time.Duration
import java.time.Instant

/**
 * Dated invitations between the owner and a session-named voice ("meet me at 5", "I'll meet you at 5", "sure").
 * Pure text rules; the voice-to-name link lives only in memory (SessionVoices) and only the resulting text is saved.
 */
object VoiceInvitations {
    private const val CLOCK = "(?:\\d{1,2}(?::\\d{2})?(?:\\s*(?:a\\.?m\\.?|p\\.?m\\.?|o['’]clock))?|noon)"
    private const val DAY = "(?:\\s+(?:today|tonight|tomorrow|on\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)))?"
    private val invite = Regex("\\b(?:meet me|see you|let['’]?s meet|let us meet)\\s+(?:at\\s+)?($CLOCK$DAY)\\b", RegexOption.IGNORE_CASE)
    private val reply = Regex("\\bi(?:['’]ll| will) (?:meet|see) you (?:there )?(?:at\\s+)?($CLOCK$DAY)\\b", RegexOption.IGNORE_CASE)
    private val acceptance = Regex("^(?:sure|ok|okay|yes|yeah|yep|sounds good|deal|see you then|see you there|perfect)(?:[,!.]?\\s*(?:sure|ok|okay|see you then|see you there|sounds good))?[.!]*$", RegexOption.IGNORE_CASE)
    // "Sarah, ..." needs a capital letter; after a greeting ("hey sarah") any case works, because speech
    // recognition often writes names in lowercase.
    private val address = Regex("^(?i:hey|hi|hello)[\\s,]+(\\p{L}{2,21})\\b|^(?i:hey|hi|hello|so|ok|okay)?[\\s,]*(\\p{Lu}\\p{Ll}{1,20})(?:\\s*,|\\s*[!?.]\\s*$)")
    private val notNames = setOf("Hey", "Hi", "Hello", "So", "Ok", "Okay", "Yes", "Yeah", "No", "Well", "Sure", "Thanks", "Thank", "Sorry",
        "Please", "And", "But", "Then", "Now", "Today", "Tomorrow", "Tonight", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday", "Sunday", "I", "Actually", "Right", "Wait", "Look", "Listen", "Guys", "Everyone", "Alright", "Great", "Cool",
        "There", "You", "Everybody", "All", "Folks", "Man", "Dude", "Again", "Anyway", "What", "How", "Why", "Who", "Where", "When")

    /** The clock phrase of another speaker's dated invitation, e.g. "5" or "5 pm tomorrow"; null if none. */
    fun invitation(text: String): String? = invite.find(text)?.groupValues?.get(1)?.trim()
    /** The clock phrase of the owner's reply "I'll meet you at 5"; null if none. */
    fun ownerReply(text: String): String? = reply.find(text)?.groupValues?.get(1)?.trim()
    fun isAcceptance(text: String): Boolean = acceptance.matches(text.trim())
    /** The name the owner addresses ("Hey Sarah", "Sarah, ..."); null if none. */
    fun addressed(text: String): String? = address.find(text.trim())?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        ?.lowercase()?.replaceFirstChar { it.titlecase() }
        ?.takeIf { name -> notNames.none { it.equals(name, ignoreCase = true) } }

    /** Event text from another speaker: "<who>: meet at <clock>"; information only, never a reminder. */
    fun otherTitle(who: String, clock: String) = "$who: meet at $clock"
    /** Owner event: "Meet <name> at <clock>". */
    fun ownerTitle(name: String, clock: String) = "Meet $name at $clock"
    /** A sentence the existing activity rules can date ("Meeting at 5 tomorrow"). */
    fun datable(clock: String) = "Meeting at $clock."

    private val bareHour = Regex("^(\\d{1,2})(:\\d{2})?((?:\\s+\\S+)*)$")
    /**
     * Dated activity for an invitation clock. A bare hour ("5") has no am/pm: 1-7 read as pm, 8-12 as the next
     * occurrence after [at]; the review note says so. Returns null when the time cannot be dated.
     */
    fun propose(clock: String, at: Instant, zone: java.time.ZoneId): ProposedCommitment? {
        fun extract(c: String) = ActivityTiming.extract(Transcript(datable(c), at, zone, Speaker.OTHER, true), informationalOnly = true)
            .firstOrNull { it.activityStartAt != null }
        extract(clock)?.let { return it }
        val m = bareHour.matchEntire(clock.trim()) ?: return null
        val hour = m.groupValues[1].toInt()
        if (hour !in 1..12) return null
        val rest = m.groupValues[2] + " %s" + m.groupValues[3]
        // Meetups at 1-7 o'clock are almost always afternoon/evening; 8-12 take the next occurrence. Always flagged for review.
        val readings = if (hour in 1..7) listOf("pm") else listOf("am", "pm")
        return readings.mapNotNull { extract(m.groupValues[1] + rest.format(it)) }.minByOrNull { it.activityStartAt!! }
            ?.let { it.copy(needsReview = "Time read as the next $clock o'clock; check am or pm. " + it.needsReview) }
    }
}

/**
 * Session-only conversation memory for naming and invitations. Holds names and timestamps in memory; [clear] wipes it.
 * - Someone says "Hey <Name>" and a different grouped voice speaks within [suggestWindowMs] -> suggest that name.
 *   Any voice may address (the owner is often not recognized on a short phrase); the owner still applies it only by tapping.
 * - A named voice invites at a time, then the owner accepts within [acceptWindowMs] -> owner event for Review.
 */
class SessionNaming(private val suggestWindowMs: Long = 5_000, private val acceptWindowMs: Long = 30_000) {
    data class Suggestion(val voiceKey: String, val name: String)
    data class Invitation(val voiceKey: String, val name: String, val clock: String, val at: Instant)
    private data class Addressed(val name: String, val by: String?, val at: Instant)
    private var addressed: Addressed? = null
    private var lastNamedVoice: Pair<String, Instant>? = null // name, at
    private var invitation: Invitation? = null

    /** Anyone spoke ([voiceKey] = their session voice, if known). Remembers an addressed name. */
    fun spoke(text: String, voiceKey: String?, at: Instant) {
        VoiceInvitations.addressed(text)?.let { addressed = Addressed(it, voiceKey, at) }
    }
    fun ownerSpoke(text: String, at: Instant) = spoke(text, null, at)
    /** A grouped voice spoke. Returns a name suggestion when the owner just addressed someone and this voice has no name. */
    fun voiceSpoke(voiceKey: String, currentName: String?, at: Instant): Suggestion? {
        if (currentName != null) { lastNamedVoice = currentName to at; return null }
        val found = addressed ?: return null
        if (found.by == voiceKey) return null // the person who said the name is not the one being named
        if (Duration.between(found.at, at).toMillis() !in 0..suggestWindowMs) return null
        addressed = null
        return Suggestion(voiceKey, found.name)
    }
    fun invited(voiceKey: String, name: String, clock: String, at: Instant) { invitation = Invitation(voiceKey, name, clock, at) }
    /** The named voice the owner is answering, if one spoke within the accept window. */
    fun recentNamedVoice(at: Instant): String? = lastNamedVoice?.takeIf { Duration.between(it.second, at).toMillis() in 0..acceptWindowMs }?.first
    /** Owner's short acceptance of the latest named invitation; consumes it. */
    fun accepted(text: String, at: Instant): Invitation? {
        if (!VoiceInvitations.isAcceptance(text)) return null
        val found = invitation?.takeIf { Duration.between(it.at, at).toMillis() in 0..acceptWindowMs } ?: return null
        invitation = null
        return found
    }
    fun clear() { addressed = null; lastNamedVoice = null; invitation = null }
}
