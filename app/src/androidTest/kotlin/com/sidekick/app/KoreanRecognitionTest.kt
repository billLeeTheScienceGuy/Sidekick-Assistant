package com.sidekick.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.app.reminders.ReminderCoordinator
import com.sidekick.app.speech.*
import com.sidekick.core.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class KoreanRecognitionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun adaptiveKoreanRecognizesBothVoicesAndPreservesNegativeCommands() = runBlocking {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val speech = SpeechEngine(context)
        val fixtures = instrumentation.context.assets.open("korean-commands.json").bufferedReader().use { JSONArray(it.readText()) }
        fun normal(text: String) = text.replace("6", "여섯").replace("7", "일곱").filter { it.isLetterOrDigit() }
        try {
            for (i in 0 until fixtures.length()) {
                val fixture = fixtures.getJSONObject(i)
                val audio = instrumentation.context.assets.open(fixture.getString("file")).use { WavAudio.decode(it.readBytes()) }
                val text = speech.transcribe(audio, retainModel = true, selectedLanguage = SpeechLanguage.KOREAN, selectedQuality = SpeechQuality.ADAPTIVE)
                assertEquals(fixture.getString("file"), normal(fixture.getString("reference")), normal(text))
                if (i % 4 == 1) assertNotNull(text, EventChange.parse(text))
                if (i % 4 == 2) assertTrue(text, EventChange.parse(text)?.cancel == true)
                if (i % 4 == 3) assertNull(text, EventChange.parse(text))
            }
        } finally { speech.release() }
    }
    @Test fun pacedVadToKoreanActionsWorksForBothVoicesWithPauseBoundaries() = runBlocking {
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val settings = GuidanceSettings(context); val previous = settings.handsOffMode.value
        val zone = ZoneId.of("America/New_York")
        settings.setHandsOff(true)
        try {
            for (voice in listOf("SunHiNeural", "InJoonNeural")) {
                val db = Room.inMemoryDatabaseBuilder(context, MemoryDatabase::class.java).build()
                val memory = TemporaryMemory(); val reminders = ReminderCoordinator(context, db.commitments())
                val auto = AutomaticMemory(context, settings, memory, db.commitments(), reminders)
                val speech = SpeechEngine(context, { SpeechLanguage.KOREAN }, { SpeechQuality.ADAPTIVE })
                val now = Instant.now(); var continuation = false; val outputs = mutableListOf<String>()
                NativeVad.open(VadModel.install(context))
                val processor = com.sidekick.app.audio.LiveSpeechProcessor(speech, onBoundary = { continuation = it }) { text, sample, latency ->
                    outputs += text; println("KOREAN_PACED voice=$voice; ms=$latency; output=$text")
                    memory.appendAttributedLive(text, now.plusMillis(sample * 1000 / 16000), zone, Speaker.UNKNOWN, continuation); auto.process()
                    if (text.replace(" ", "").contains("취소하지")) assertEquals("ACCEPTED", db.commitments().all().single().state)
                }
                try {
                    coroutineScope {
                        val worker = launch(Dispatchers.IO) { processor.consume() }
                        for (index in listOf(0, 3, 1, 2)) {
                            val audio = instrumentation.context.assets.open("ko-$voice-$index.wav").use { WavAudio.decode(it.readBytes()) }
                            val padded = audio + FloatArray(24000); audio.fill(0f)
                            try {
                                for (start in padded.indices step 4096) {
                                    val frame = padded.copyOfRange(start, minOf(start + 4096, padded.size))
                                    try { processor.accept(frame) } finally { frame.fill(0f) }
                                    delay(256)
                                }
                            } finally { padded.fill(0f) }
                        }
                        processor.finish(); withTimeout(20_000) { worker.join() }
                    }
                    val item = db.commitments().all().single()
                    assertEquals(outputs.joinToString(" | "), "CANCELLED", item.state)
                    assertEquals(19, Instant.ofEpochMilli(item.activityStartAt!!).atZone(zone).hour)
                    assertNull(item.dueAt); assertTrue(item.informational)
                    assertTrue(memory.text.value.lines().size >= 4)
                } finally { processor.close(); NativeVad.close(); reminders.deleteAll(); db.close() }
            }
        } finally { settings.setHandsOff(previous) }
    }
    @Test fun capturedGarbledPhrasesCannotBorrowAClockAcrossQuietPauses() {
        val memory = TemporaryMemory(); val zone = ZoneId.of("America/New_York"); val now = Instant.now()
        memory.appendAttributedLive("네 우 여섯 시에 회의가 있어요", now, zone, Speaker.UNKNOWN, false)
        memory.appendAttributedLive("화일을 내일 오후 7시 를 벽에 대해서", now.plusSeconds(7), zone, Speaker.UNKNOWN, false)
        memory.appendAttributedLive("베이를 치서 해줘", now.plusSeconds(14), zone, Speaker.UNKNOWN, false)
        assertTrue(memory.drafts.value.isEmpty())
    }
}
