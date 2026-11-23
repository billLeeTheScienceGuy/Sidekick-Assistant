package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/**
 * A flood of speech faster than real time (20 copies of an 11 s clip back to back) must never stop listening: phrases
 * wait in a line sized by free memory and are all transcribed (faster recognition catches up); nothing is skipped. (Before the fix, a third waiting phrase stopped
 * listening with "Transcription fell behind".)
 */
class BusyRecognizerTest {
    @Test fun aFloodOfSpeechNeverStopsListening() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SidekickApplication
        val clip = WavAudio.decode(InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").readBytes())
        val heard = mutableListOf<String>()
        val processor = LiveSpeechProcessor(app.speech, fasterAvailable = { app.speech.fasterAvailable() }) { text, _, _ -> if (text.isNotBlank()) heard += text }
        com.sidekick.app.speech.NativeVad.open(com.sidekick.app.speech.VadModel.install(context))
        try {
            val worker = launch(Dispatchers.Default) { processor.consume() }
            repeat(20) {
                for (i in clip.indices step 4096) {
                    val frame = clip.copyOfRange(i, minOf(i + 4096, clip.size))
                    try { processor.accept(frame) } finally { frame.fill(0f) }
                }
            }
            processor.finish()
            withTimeout(600_000) { worker.join() }
            assertTrue("some speech was transcribed", heard.isNotEmpty())
            assertEquals("nothing skipped or dropped", 0, processor.droppedCount)
            // The clip says "country" twice: 20 copies give about 40 when nothing is skipped (allowing a few recognizer slips).
            val countries = Regex("countr", RegexOption.IGNORE_CASE).findAll(heard.joinToString(" ")).count()
            assertTrue("all copies were transcribed ($countries of about 40)", countries >= 30)
            android.util.Log.i("SidekickListen", "flood: transcribed ${processor.transcriptionCount}, dropped ${processor.droppedCount}, faster ${processor.fasterCount}")
        } finally { com.sidekick.app.speech.NativeVad.close() }
    }
}
