package com.sidekick.core

/** A coarse hint, never a determination of applicable law or permission to listen. */
data class PrivacyRegion(val country: String, val area: String = "") {
    init { require(country.matches(Regex("[A-Z]{2}"))); require(area.length <= 80) }
}
data class CachedPrivacyRegion(val region: PrivacyRegion, val checkedAt: Long) {
    fun fresh(now: Long) = now - checkedAt in 0 until 86_400_000L
}
data class RegionGuidance(val label: String, val note: String, val source: String?)

object PrivacyRegions {
    const val policy = "This panel is for reading only. What SideKick keeps while listening follows Conversation rules above, using the phone's own location at that moment. The city you choose here never changes those rules, and a location from a mock-location app gets the strictest rules. Tell people you're taking notes, and stop if someone objects."
    const val limits = "A phone location cannot establish where every participant is or which rules apply. Private settings, workplaces, cross-border calls and other laws may impose additional duties. Discarding audio does not remove these concerns."
    const val checked = "Sources checked November 2026; this is limited educational guidance, not legal authorization."
    const val federal = "https://uscode.house.gov/us/usc/119-118/t18/s2511/"
    const val wisconsin = "https://docs.legis.wisconsin.gov/statutes/statutes/968/31"
    const val illinois = "https://ilga.gov/documents/legislation/ilcs/documents/072000050K14-2.htm"

    /** Explicit jurisdiction only: never infer a state from a city, timezone or ambiguous IL country code. */
    fun fromCity(city: String): PrivacyRegion? {
        val parts = city.split(',').map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val us = parts.last() in setOf("US", "USA", "UNITED STATES", "UNITED STATES OF AMERICA")
        val area = if (us) parts.dropLast(1).lastOrNull() else parts.last()
        return when {
            area == "WISCONSIN" || area == "WI" -> PrivacyRegion("US", "WI")
            area == "ILLINOIS" || area == "IL" && us -> PrivacyRegion("US", "IL")
            us -> PrivacyRegion("US")
            else -> null
        }
    }

    fun guidance(region: PrivacyRegion?): RegionGuidance = when {
        region?.country == "US" && region.area.uppercase(java.util.Locale.ROOT) in setOf("WI", "WISCONSIN") -> RegionGuidance("Wisconsin hint",
            "Wisconsin statute 968.31 has an exception when you take part in the conversation or one participant agrees, unless the purpose is criminal or harmful. While listening in Wisconsin, SideKick keeps others' words while you take part (spoke in the last 2 minutes) and groups voices without a notice. Near Illinois, Illinois rules apply.", wisconsin)
        region?.country == "US" && region.area.uppercase(java.util.Locale.ROOT) in setOf("IL", "ILLINOIS") -> RegionGuidance("Illinois hint",
            "Illinois law addresses surreptitious recording of private conversations and consent of the other parties, and separately restricts voiceprints. Exceptions and context matter. While listening in Illinois, SideKick keeps others' words only after a notice and never groups voices.", illinois)
        region?.country == "US" -> RegionGuidance("United States hint · state not covered",
            "Federal law contains participant-consent exceptions, subject to restrictions. This does not settle state law or the rules for your conversation. While listening, SideKick applies the rules of the state the phone is in.", federal)
        else -> RegionGuidance("Region unknown or not covered", "SideKick has no verified local-law summary for this region. While listening, an unknown place or one outside the US gets the strictest rules: a notice first and no voice grouping. Check the rules for all participants.", null)
    }
}
