package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpeakerPaddingTest {
    @Test fun paddedSameVoiceMatchesButQuietShortAndDifferentVoicesCannotBorrowIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context)
        val engine = SpeakerEngine(context, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val long = pcm.copyOfRange(0, 80000)
        val short = pcm.copyOfRange(0, 32000)
        val padded = FloatArray(4096) + long + FloatArray(16384)
        val paddedShort = FloatArray(4096) + short + FloatArray(16384)
        val other = instrumentation.context.assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
        try {
            val reference = engine.embedding(long)
            try { profile.save(listOf(reference, reference, reference)) } finally { reference.fill(0f) }
            assertEquals(Speaker.UNKNOWN, engine.identify(paddedShort, 1000))
            assertEquals(engine.lastDiagnostic, Speaker.USER, engine.identify(padded, 2000))
            println("PADDING_SPEAKER ${engine.lastDiagnostic}")
            assertNotEquals(Speaker.USER, engine.identify(other, 3000))
            assertEquals(Speaker.UNKNOWN, engine.identify(paddedShort, 4000))
            assertEquals(Speaker.UNKNOWN, engine.identify(FloatArray(64000), 5000))
            assertEquals(Speaker.USER, engine.identify(long, 6000))
            val mixed = long + other
            try { assertNotEquals(Speaker.USER, engine.identify(mixed, 7000)) } finally { mixed.fill(0f) }
        } finally {
            profile.delete(); engine.close()
            listOf(pcm, long, short, padded, paddedShort, other).forEach { it.fill(0f) }
        }
    }
}
