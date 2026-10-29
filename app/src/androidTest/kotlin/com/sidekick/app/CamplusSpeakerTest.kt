package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CamplusSpeakerTest {
    @Test fun modelSwitchingPreservesProfilesAndShortHeldOutSpeechRejectsOtherVoices() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context); val previous = profile.model.value
        val engine = SpeakerEngine(context, profile)
        val pcm = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val long = pcm.copyOfRange(0, 80000)
        var legacy: FloatArray? = null
        try {
            profile.delete(); profile.selectModel(SpeakerModel.RESNET)
            legacy = engine.embedding(long)
            profile.save(listOf(legacy, legacy, legacy))
            profile.selectModel(SpeakerModel.CAMPPLUS)
            assertFalse(profile.ready.value); assertNull(profile.snapshot())
            val reference = engine.embedding(long)
            assertEquals(192, reference.size)
            try { profile.save(listOf(reference, reference, reference)) } finally { reference.fill(0f) }
            assertTrue(VoiceProfile(context).ready.value)
            assertEquals(SpeakerModel.CAMPPLUS, VoiceProfile(context).model.value)
            assertEquals(Speaker.USER, engine.identify(long, 1000))
            val short = pcm.copyOfRange(96000, 129600)
            try {
                val result = engine.identify(short, 4000)
                println("CAMPLUS_HELDOUT ${engine.lastDiagnostic}")
                assertEquals(engine.lastDiagnostic, Speaker.USER, result)
            } finally { short.fill(0f) }
            for (file in listOf("quality-party.wav", "command-0-1.wav", "command-1-1.wav", "ko.wav", "zh.wav", "fr.wav", "de.wav", "ru.wav")) {
                assertEquals(Speaker.USER, engine.identify(long, 1000))
                val audio = instrumentation.context.assets.open(file).use { WavAudio.decode(it.readBytes()) }
                val other = audio.copyOfRange(0, minOf(audio.size, 33600))
                try { assertNotEquals(file, Speaker.USER, engine.identify(other, 4000)) }
                finally { audio.fill(0f); other.fill(0f) }
            }
            try { profile.save(listOf(legacy, legacy, legacy)); fail("Mixed models must be rejected") }
            catch (_: IllegalArgumentException) { }
            profile.selectModel(SpeakerModel.RESNET)
            assertTrue(profile.ready.value); assertArrayEquals(legacy, profile.snapshot(), .000001f)
            assertEquals(Speaker.USER, engine.identify(long, 1000))
            profile.selectModel(SpeakerModel.CAMPPLUS)
            profile.setEnabled(false); assertEquals(Speaker.UNKNOWN, engine.identify(long, 2000))
            profile.delete(); assertFalse(VoiceProfile(context).ready.value)
            profile.selectModel(SpeakerModel.RESNET); assertFalse(profile.ready.value)
        } finally {
            profile.delete(); profile.selectModel(previous); profile.setEnabled(true)
            legacy?.fill(0f); engine.close(); pcm.fill(0f); long.fill(0f)
        }
    }
}
