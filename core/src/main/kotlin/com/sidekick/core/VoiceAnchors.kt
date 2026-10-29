package com.sidekick.core

/** A bounded bank of embeddings from independently accepted long turns. No audio is retained. */
class VoiceAnchors {
    private data class Turn(val at: Long, val mean: FloatArray, val windows: List<FloatArray>) {
        fun wipe() { mean.fill(0f); windows.forEach { it.fill(0f) } }
    }
    private val turns = ArrayDeque<Turn>()
    fun clear() { turns.forEach { it.wipe() }; turns.clear() }
    private fun prune(now: Long) {
        val expired = turns.filter { now - it.at !in 0L..MAX_AGE_MS }
        expired.forEach { it.wipe(); turns.remove(it) }
    }
    fun remember(windows: List<FloatArray>, now: Long) {
        require(windows.size >= 2)
        prune(now)
        val copies = windows.take(6).map(VoiceMatch::normalized)
        require(copies.all { it.size == copies.first().size })
        val mean = VoiceMatch.normalized(FloatArray(copies.first().size) { i -> copies.sumOf { it[i].toDouble() }.toFloat() })
        turns.addLast(Turn(now, mean, copies))
        while (turns.size > 2) turns.removeFirst().wipe()
    }
    data class Support(val score: Float, val ageMs: Long, val meanScore: Float, val windowScore: Float)
    fun support(vector: FloatArray, now: Long): Support? {
        prune(now)
        return turns.map { turn ->
            val scores = turn.windows.map { VoiceMatch.similarity(it, vector) }.sortedDescending()
            // One favorable window is insufficient; preserve the existing mean-based path too.
            val meanScore = VoiceMatch.similarity(turn.mean, vector)
            Support(maxOf(meanScore, scores[1]), now - turn.at, meanScore, scores[1])
        }.maxByOrNull { it.score }
    }
    fun ageMs(now: Long): Long? { prune(now); return turns.lastOrNull()?.let { now - it.at } }
    companion object { const val MAX_AGE_MS = 60_000L }
}
