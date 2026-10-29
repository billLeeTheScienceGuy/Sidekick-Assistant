package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShortVoiceReferencesTest {
    @Test fun variedShortSpeechNeedsCurrentEvidenceAndForeignVoicesDoNotBorrowTheReferenceBank() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context); val engine = SpeakerEngine(context, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val long = pcm.copyOfRange(0, 80000)
        try {
            val reference = engine.embedding(long)
            try { profile.save(listOf(reference, reference, reference)) } finally { reference.fill(0f) }
            var matched = 0
            for (start in listOf(0, 16000, 32000, 48000, 64000)) {
                assertEquals(Speaker.USER, engine.identify(long, 1000))
                val short = pcm.copyOfRange(start, start + 33600)
                try {
                    val result = engine.identify(short, 40_000)
                    println("SHORT_REFERENCE start=$start; result=$result; ${engine.lastDiagnostic}")
                    if (result == Speaker.USER) matched++
                } finally { short.fill(0f) }
            }
            assertTrue("At least the clean own-speaker short phrase should match", matched > 0)
            for (file in listOf("quality-party.wav", "command-0-1.wav", "command-1-1.wav", "ko.wav", "zh.wav", "fr.wav", "de.wav", "ru.wav")) {
                assertEquals(Speaker.USER, engine.identify(long, 1000))
                val other = instrumentation.context.assets.open(file).use { WavAudio.decode(it.readBytes()) }
                val short = other.copyOfRange(0, minOf(other.size, 33600))
                try {
                    assertNotEquals("$file: ${engine.lastDiagnostic}", Speaker.USER, engine.identify(short, 40_000))
                } finally { other.fill(0f); short.fill(0f) }
            }
            assertEquals(Speaker.USER, engine.identify(long, 1000))
            val short = pcm.copyOfRange(0, 33600)
            try { assertEquals(Speaker.UNKNOWN, engine.identify(short, 61_001)) } finally { short.fill(0f) }
        } finally { engine.close(); profile.delete(); pcm.fill(0f); long.fill(0f) }
    }
}
