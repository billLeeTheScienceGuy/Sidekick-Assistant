package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.core.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises production routing, model switching and actual public/synthetic audio. */
class AdaptiveRecognitionTest {
    @Test fun productionAdaptiveModelsRecognizeEventsAndKeepNegativeCommandsSafe() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        var language = SpeechLanguage.ARABIC
        val speech = SpeechEngine(context, { language }, { SpeechQuality.ADAPTIVE })
        val now = Instant.parse("2026-10-28T14:00:00Z"); val zone = ZoneId.of("America/New_York")
        suspend fun decode(file: String): String {
            val pcm = i.context.assets.open(file).use { WavAudio.decode(it.readBytes()) }
            val short = pcm.size < speech.shortBoostLimit()
            val text = speech.transcribe(pcm, retainModel = true, audioContext = speech.liveContext(short), shortPhrase = short)
            assertTrue(pcm.all { it == 0f }); assertTrue(text.isNotBlank())
            println("ADAPTIVE language=${language.code}; output=$text")
            return text
        }
        try {
            val cases = listOf(
                Triple(SpeechLanguage.ARABIC, "ar-ar-SA-HamedNeural-0.wav", 18),
                Triple(SpeechLanguage.CHINESE, "zh-zh-CN-XiaoxiaoNeural-0.wav", 18),
                Triple(SpeechLanguage.KOREAN, "ko-ko-KR-SunHiNeural-4.wav", 20),
                Triple(SpeechLanguage.FRENCH, "fr-fr-FR-DeniseNeural-0.wav", 18),
                Triple(SpeechLanguage.GERMAN, "de-de-DE-KatjaNeural-0.wav", 18),
                Triple(SpeechLanguage.RUSSIAN, "ru-ru-RU-SvetlanaNeural-0.wav", 18),
                Triple(SpeechLanguage.ENGLISH, "en-en-US-AriaNeural-0.wav", 18),
            )
            for ((selected, file, hour) in cases) {
                language = selected; val text = decode(file)
                val event = ActivityTiming.extract(Transcript(text, now, zone, Speaker.OTHER, true), true).single()
                assertEquals(text, hour, event.activityStartAt!!.atZone(zone).hour)
            }
            for ((selected, file) in listOf(SpeechLanguage.ARABIC to "ar-ar-SA-HamedNeural-3.wav", SpeechLanguage.CHINESE to "zh-zh-CN-XiaoxiaoNeural-3.wav", SpeechLanguage.KOREAN to "ko-ko-KR-SunHiNeural-3.wav", SpeechLanguage.GERMAN to "de-de-DE-KatjaNeural-3.wav")) {
                language = selected; val text = decode(file); assertNull(text, EventChange.parse(text))
            }
            for (selected in listOf(SpeechLanguage.FRENCH, SpeechLanguage.GERMAN)) {
                language = selected; decode("${selected.code}.wav")
            }
        } finally { speech.release() }
    }
}
