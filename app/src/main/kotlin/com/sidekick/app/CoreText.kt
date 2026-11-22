package com.sidekick.app

/**
 * Some plain-English sentences come from the core module (recording rules, privacy guidance). This swaps the known
 * phrases for the app-language text, so the legal meaning stays the same and nothing new is invented.
 */
internal object CoreText {
    private val phrases: List<Pair<String, Int>> by lazy { listOf(
        "Wisconsin statute 968.31 has an exception when you take part in the conversation or one participant agrees, unless the purpose is criminal or harmful. While listening in Wisconsin, SideKick keeps others' words while you take part (spoke in the last 2 minutes) and groups voices without a notice. Near Illinois, Illinois rules apply." to R.string.core_wi_note,
        "This panel is for reading only. What SideKick keeps while listening follows Conversation rules above, using the phone's own location at that moment. The city you choose here never changes those rules, and a location from a mock-location app gets the strictest rules. Tell people you're taking notes, and stop if someone objects." to R.string.core_policy,
        "Illinois law addresses surreptitious recording of private conversations and consent of the other parties, and separately restricts voiceprints. Exceptions and context matter. While listening in Illinois, SideKick keeps others' words only after a notice and never groups voices." to R.string.core_il_note,
        "A phone location cannot establish where every participant is or which rules apply. Private settings, workplaces, cross-border calls and other laws may impose additional duties. Discarding audio does not remove these concerns." to R.string.core_limits,
        "Federal law contains participant-consent exceptions, subject to restrictions. This does not settle state law or the rules for your conversation. While listening, SideKick applies the rules of the state the phone is in." to R.string.core_us_note,
        "SideKick has no verified local-law summary for this region. While listening, an unknown place or one outside the US gets the strictest rules: a notice first and no voice grouping. Check the rules for all participants." to R.string.core_other_note,
        "Sources checked November 2026; this is limited educational guidance, not legal authorization." to R.string.core_checked,
        "you're in the conversation, others' words kept as information" to R.string.core_in_conversation_others_info,
        "only your words are kept until you announce note-taking" to R.string.core_only_yours_until_announce,
        "Simulated location (mock-location app): strictest rule" to R.string.core_simulated,
        "Outside the bundled US borders: strictest rule" to R.string.core_outside_borders,
        "one-party, others kept while you take part" to R.string.core_oneparty_others_kept,
        "strictest rules, announce to keep others" to R.string.core_strictest_announce,
        "only your words are kept until you speak" to R.string.core_only_yours_until_speak,
        "one-party, voice notice before grouping" to R.string.core_oneparty_voice_notice,
        "I told everyone (incl. voice analysis)" to R.string.i_told_everyone_incl_voice_analysis,
        "United States hint · state not covered" to R.string.core_us_label,
        "Strictest rules everywhere (setting)" to R.string.core_strictest_everywhere,
        "all-party, announce to keep others" to R.string.core_allparty_announce,
        "notice given, conversation kept" to R.string.core_notice_given_kept,
        " · voice grouping after notice" to R.string.core_voice_grouping_after_notice,
        "Location older than 30 minutes" to R.string.core_location_old,
        "Call detected: strictest rule" to R.string.call_detected_strictest_rule,
        "Region unknown or not covered" to R.string.core_other_label,
        "all-party, notice given" to R.string.core_allparty_notice_given,
        "Location lookup failed" to R.string.location_lookup_failed,
        " · no voice grouping" to R.string.core_no_voice_grouping_dot,
        " · notice expired (" to R.string.core_notice_expired,
        "voice notice needed" to R.string.core_voice_notice_needed,
        " · audio discarded" to R.string.core_audio_discarded,
        "conversation ended" to R.string.core_conv_ended,
        "no voice grouping" to R.string.core_no_voice_grouping,
        "Unknown location" to R.string.core_unknown_location,
        "Strictest rules" to R.string.core_strictest_rules,
        "No location fix" to R.string.no_location_fix,
        "Outside the US" to R.string.core_outside_us,
        "all-party rule" to R.string.core_allparty_rule,
        "one-party rule" to R.string.core_oneparty_rule,
        "Wisconsin hint" to R.string.core_wi_label,
        "2 hours passed" to R.string.core_2h,
        "Illinois hint" to R.string.core_il_label,
        "Phone call" to R.string.core_phone_call,
        "(near " to R.string.core_near,
    ) }
    private val within = Regex("Within ([0-9]+) km of (.+?): ")

    fun localize(text: String): String {
        if (AppLanguage.localized() == null || text.isEmpty()) return text
        var result = within.replace(text) { tr(R.string.core_within_km_of, it.groupValues[1], it.groupValues[2]) + " " }
        for ((english, id) in phrases) if (result.contains(english)) result = result.replace(english, tr(id))
        return result
    }
}
