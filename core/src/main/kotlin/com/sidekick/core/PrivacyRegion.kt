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
    const val policy = "Start only when everyone included agrees to live transcription. Stop if someone joins without agreeing or withdraws consent. Location and speaker matching cannot verify consent."
    const val limits = "A phone location cannot establish where every participant is or which rules apply. Private settings, workplaces, cross-border calls and other laws may impose additional duties. Discarding audio does not remove these concerns."
    const val checked = "Sources checked November 8, 2026; this is limited educational guidance, not legal authorization."
    const val federal = "https://uscode.house.gov/us/usc/119-118/t18/s2511/"
    const val wisconsin = "https://dwd.wisconsin.gov/dvr/policy-guidance/legal/recording-conversations.htm"
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
            "Wisconsin DWD guidance discusses participant-consent exceptions. That agency page was reviewed in May 2014; verify the current statute and circumstances. SideKick still asks for everyone's agreement.", wisconsin)
        region?.country == "US" && region.area.uppercase(java.util.Locale.ROOT) in setOf("IL", "ILLINOIS") -> RegionGuidance("Illinois hint",
            "Illinois law addresses surreptitious recording of private conversations and consent of the other parties. Exceptions and context matter. SideKick still asks for everyone's agreement.", illinois)
        region?.country == "US" -> RegionGuidance("United States hint · state not covered",
            "Federal law contains participant-consent exceptions, subject to restrictions. This does not settle state law or the rules for your conversation. SideKick still asks for everyone's agreement.", federal)
        else -> RegionGuidance("Region unknown or not covered", "SideKick has no verified local-law summary for this region. Use everyone's agreement and check the rules for all participants.", null)
    }
}
