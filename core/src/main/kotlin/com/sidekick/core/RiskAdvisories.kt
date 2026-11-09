package com.sidekick.core

data class RiskAdvisory(val id: String, val title: String, val explanation: String)

/** Small English red-flag grammar, not fraud classification. Never returns sensitive source text. */
object RiskAdvisories {
    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
    private val defensive = rx("\\b(?:never|avoid|do not|don't|should not|must not)\\s+(?:pay|send|share|give|read|install|download|allow|grant)\\b")
    private val gift = rx("\\bgift[ -]?cards?\\b")
    private val payment = rx("\\b(?:pay|payment|fine|tax|fee|debt|unlock)\\b")
    private val disclose = rx("\\b(?:send|read|give|share|text|tell)\\b.{0,80}\\b(?:numbers?|codes?|pins?)\\b")
    private val secret = rx("\\b(?:send|read|tell|give|share|text)\\b.{0,60}\\b(?:(?:verification|one[ -]time|security|login|authentication)\\s+codes?|passwords?|pins?)\\b")
    private val remote = rx("\\b(?:install|download)\\b.{0,70}\\b(?:anydesk|teamviewer)\\b|\\b(?:allow|grant|give)\\b.{0,60}\\bremote access\\b")
    private val transfer = rx("\\b(?:transfer|move|send)\\b.{0,60}\\b(?:money|funds)\\b.{0,80}\\b(?:safe|secure|protect)\\b.{0,30}\\baccount\\b")
    fun inspect(text: String): List<RiskAdvisory> {
        val sentences = Sentences.split(text.takeLast(4000)).filterNot { defensive.containsMatchIn(it) }
        val result = linkedMapOf<String, RiskAdvisory>()
        for (sentence in sentences) {
            if (gift.containsMatchIn(sentence) && (payment.containsMatchIn(sentence) || disclose.containsMatchIn(sentence)))
                result["gift-payment"] = RiskAdvisory("gift-payment", "Gift-card payment request", "Gift-card payment or code-sharing language appeared. Pause and verify the request through a trusted contact method.")
            if (secret.containsMatchIn(sentence)) result["secret-request"] = RiskAdvisory("secret-request", "Private code or password request", "A request to disclose an authentication code, password or PIN appeared. Verify independently before sharing private credentials.")
            if (remote.containsMatchIn(sentence)) result["remote-access"] = RiskAdvisory("remote-access", "Remote-access request", "A request for remote-access software or access appeared. Verify who is requesting it through a trusted contact method.")
            if (transfer.containsMatchIn(sentence)) result["safe-account"] = RiskAdvisory("safe-account", "Money-transfer warning sign", "Moving money to a supposedly safe account was mentioned. Pause and verify independently with the institution.")
        }
        return result.values.toList()
    }
}
