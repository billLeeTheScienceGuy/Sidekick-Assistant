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

class KoreanNumberReadingTest {
    @Test fun fullContextBaseReadsEightAndOnlyTheCapturedInvalidClockIsReplaced() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val speech = SpeechEngine(context)
        val original = "저는 내일 오후 18시에 파티에 갈 거예요."
        try {
            for (index in listOf(4, 5)) {
                val pcm = i.context.assets.open("ko-ko-KR-SunHiNeural-$index.wav").use { WavAudio.decode(it.readBytes()) }
                val second = speech.transcribe(pcm, selectedLanguage = SpeechLanguage.KOREAN, selectedQuality = SpeechQuality.ACCURATE, audioContext = 0, retainModel = true)
                val repaired = KoreanClockRepair.select(original, second)
                println("KOREAN_NUMBER publicFixture=$index; base=$second; clock=${KoreanClockRepair.clockText(repaired)}")
                assertNotEquals(second, original, repaired)
                assertFalse(KoreanClockRepair.needsRetry(repaired))
                val zone = ZoneId.of("America/New_York")
                val event = ActivityTiming.extract(Transcript(repaired, Instant.parse("2026-10-28T14:00:00Z"), zone, Speaker.OTHER, true), true).single()
                assertEquals(second, 20, event.activityStartAt!!.atZone(zone).hour)
                assertTrue(pcm.all { it == 0f })
            }
        } finally { speech.release() }
    }
}
