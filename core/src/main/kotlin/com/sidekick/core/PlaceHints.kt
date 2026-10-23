package com.sidekick.core

data class PlaceHint(val name: String?, val address: String?)

/** Keep literal venue wording; never turn an ambiguous venue name into a fabricated address. */
object PlaceHints {
    private val street = Regex("\\b\\d{1,6}\\s+(?:[A-Za-z0-9.'-]+\\s+){1,6}(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Way|Court|Ct|Parkway|Pkwy)\\b\\.?", RegexOption.IGNORE_CASE)
    private val venue = Regex("\\b(?:at|venue is|location is|held at)\\s+(?!\\d)([^.!?\\n,]+)", RegexOption.IGNORE_CASE)
    private val timing = Regex("\\s+(?:at\\s+\\d|on\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|tomorrow\\b|tonight\\b|today\\b|from\\s+\\d|until\\b|till\\b|and\\b|it\\b)", RegexOption.IGNORE_CASE)

    fun extract(text: String): PlaceHint {
        val address = street.findAll(text).lastOrNull()?.value?.trimEnd('.')
        val name = venue.findAll(text).mapNotNull { match ->
            var raw = match.groupValues[1].trim()
            if (raw.firstOrNull()?.isDigit() == true || Regex("^(?:noon|midnight|home|work|least|most|about|around)\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return@mapNotNull null
            raw = timing.split(raw, limit = 2).first().trim().trimEnd('.', ',')
            raw.takeIf { it.length in 2..120 && it.split(' ').size <= 12 }
        }.lastOrNull()
        return PlaceHint(name, address)
    }
}
