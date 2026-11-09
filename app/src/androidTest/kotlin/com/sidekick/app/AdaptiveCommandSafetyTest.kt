package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AdaptiveCommandSafetyTest {
    @Test fun shortPaddedAndLongNegativeCommandsKeepTheirMeaningAfterTheRoutingChange() = runBlocking<Unit> {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        var language = SpeechLanguage.FRENCH
        val speech = SpeechEngine(context, { language }, { SpeechQuality.ADAPTIVE })
        val now = Instant.parse("2026-11-09T14:00:00Z"); val zone = ZoneId.of("America/New_York")
        try {
            for ((selected, voice) in listOf(SpeechLanguage.FRENCH to "fr-FR-DeniseNeural", SpeechLanguage.GERMAN to "de-DE-KatjaNeural", SpeechLanguage.RUSSIAN to "ru-RU-SvetlanaNeural")) {
                language = selected
                for (index in 0..3) {
                    val pcm = i.context.assets.open("${language.code}-$voice-$index.wav").use { WavAudio.decode(it.readBytes()) }
                    try {
                        val short = pcm.size < speech.shortBoostLimit()
                        val text = speech.transcribe(pcm.copyOf(), retainModel = true, audioContext = speech.liveContext(short), shortPhrase = short)
                        println("COMMAND_SAFETY ${language.code} $index $text")
                        when (index) {
                            0 -> assertEquals(text, 18, ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).single().activityStartAt!!.atZone(zone).hour)
                            1 -> assertNotNull(text, EventChange.parse(text))
                            2 -> assertNotNull(text, EventChange.parse(text))
                            3 -> {
                                assertNull(text, EventChange.parse(text))
                                val padded = FloatArray(16_000) + pcm + FloatArray(48_000)
                                val span = SpeakerAudio.speechSpan(padded)!!
                                assertFalse("Quiet padding is not longer speech", SpeechQuality.ADAPTIVE.usesOmni(language, false, span.last - span.first + 1))
                                val paddingText = speech.transcribe(padded, retainModel = true, shortPhrase = false)
                                assertNull(paddingText, EventChange.parse(paddingText))
                                val copies = ((96_000 + pcm.size + 3200 - 1) / (pcm.size + 3200)).coerceAtLeast(2)
                                val repeated = FloatArray(copies * (pcm.size + 3200))
                                repeat(copies) { pcm.copyInto(repeated, it * (pcm.size + 3200)) }
                                val longSpan = SpeakerAudio.speechSpan(repeated)!!
                                assertTrue(SpeechQuality.ADAPTIVE.usesOmni(language, false, longSpan.last - longSpan.first + 1))
                                val longText = speech.transcribe(repeated, retainModel = true, shortPhrase = false)
                                println("COMMAND_SAFETY_LONG ${language.code} $longText")
                                assertNull(longText, EventChange.parse(longText))
                            }
                        }
                    } finally { pcm.fill(0f) }
                }
            }
        } finally { speech.release() }
    }
}
