package com.sidekick.core

/** Preserve source wording while avoiding sentence breaks inside spoken clock notation. */
object Sentences {
    private val boundary = Regex("(?<=[.!?])\\s+|(?<=[。！？])\\s*|\\n+")
    private val meridiem = Regex("\\b[ap]\\.m\\.$", RegexOption.IGNORE_CASE)
    private val continuation = Regex("^(?:at|on|in|until|till|and|to|from|for)\\b", RegexOption.IGNORE_CASE)
    fun split(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (match in boundary.findAll(text)) {
            val prefix = text.substring(start, match.range.first)
            if (!match.value.contains('\n') && meridiem.containsMatchIn(prefix) &&
                continuation.containsMatchIn(text.substring(match.range.last + 1))) continue
            prefix.trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = match.range.last + 1
        }
        text.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
        return result.flatMap { sentence ->
            // ASR may omit the full stop between an explicit time correction and its venue update.
            val tail = Regex("\\s+(?=(?:the|my) (?:party|meeting|appointment|concert|dinner|class|birthday|wedding) (?:will be|is) at\\b)", RegexOption.IGNORE_CASE).find(sentence)
            if (tail != null && EventChange.parse(sentence.substring(0, tail.range.first)) != null &&
                sentence.substring(tail.range.last + 1).contains("instead of", true))
                listOf(sentence.substring(0, tail.range.first).trim(), sentence.substring(tail.range.last + 1).trim())
            else listOf(sentence)
        }
    }
}
