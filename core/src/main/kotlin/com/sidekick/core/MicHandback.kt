package com.sidekick.core

/**
 * When Android gives the microphone to another app (a call, a voice recorder, the camera), SideKick's recording is
 * silenced. Instead of stopping at once, listening waits and carries on when the microphone comes back; it stops only
 * if the microphone stays taken for [limitMs] (10 minutes).
 */
class MicHandback(private val limitMs: Long = 10 * 60_000L) {
    enum class State { LISTENING, TAKEN, RETURNED, GIVE_UP }
    var takenSinceMs: Long? = null
        private set

    fun update(silenced: Boolean, nowMs: Long): State {
        val since = takenSinceMs
        return when {
            silenced && since == null -> { takenSinceMs = nowMs; State.TAKEN }
            silenced && nowMs - since!! >= limitMs -> State.GIVE_UP
            silenced -> State.TAKEN
            since != null -> { takenSinceMs = null; State.RETURNED }
            else -> State.LISTENING
        }
    }
}
