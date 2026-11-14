package com.sidekick.core

/**
 * Per-voice Auto-detect memory. After 3 phrases in a row from one voice needed Omnilingual (French, German, Arabic,
 * Russian…), the next phrases go straight to it, skipping SenseVoice and Parakeet (~1.3 s of ~6.5 s per phrase on a
 * Pixel 7a). Every 4th phrase takes the full route again, so a switch to English or Korean is noticed within 3 phrases.
 */
data class RouteMemory(val omnilingualRun: Int = 0, val sinceFullCheck: Int = 0) {
    val skipToOmnilingual: Boolean get() = omnilingualRun >= 3 && sinceFullCheck < 3
    fun after(viaOmnilingual: Boolean): RouteMemory = when {
        !viaOmnilingual -> RouteMemory()
        skipToOmnilingual -> copy(omnilingualRun = omnilingualRun + 1, sinceFullCheck = sinceFullCheck + 1)
        else -> RouteMemory(omnilingualRun + 1, 0)
    }
}
