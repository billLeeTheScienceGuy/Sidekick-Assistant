package com.sidekick.core

import kotlin.test.*

class SpeechContextTest {
    @Test fun `short phrase borrows bounded same-user context but not attribution`() {
        val context = SpeechContext()
        context.prepare(FloatArray(160_000) { .1f }, 0, "user")
        val short = FloatArray(32_000) { .2f }
        val result = context.prepare(short, 200_000, "user")
        assertEquals(96_000, result.size)
        assertEquals(.1f, result.first()); assertEquals(.2f, result.last())
    }
    @Test fun `unknown changed expired and overlapping audio are handled without leakage`() {
        val context = SpeechContext()
        val short = FloatArray(32_000)
        context.prepare(FloatArray(80_000), 0, "user")
        assertSame(short, context.prepare(short, 100_000, null))
        assertSame(short, context.prepare(short, 140_000, "user"))
        context.prepare(FloatArray(80_000), 0, "user")
        assertSame(short, context.prepare(short, 400_001, "user"))
        context.prepare(FloatArray(80_000), 0, "user")
        assertEquals(80_000, context.prepare(short, 64_000, "user").size)
        context.clear()
        assertSame(short, context.prepare(short, 100_000, "user"))
    }
}
