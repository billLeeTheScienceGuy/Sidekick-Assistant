package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningService
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.app.speech.NativeVad
import com.sidekick.app.speech.VadModel
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Long checks are opt-in. No physical microphone is opened by these tests. */
@SdkSuppress(minSdkVersion = 33)
class ContinuousListeningTest {
    private fun application(emulator: Boolean): SidekickApplication {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyContinuousListening") == "true")
        assumeTrue((Build.HARDWARE in setOf("ranchu", "goldfish")) == emulator)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        return app
    }

    @Test fun screenOffMicrophoneSessionSurvivesSixMinutesOfEmulatedSilence() = runBlocking {
        val app = application(emulator = true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val power = app.getSystemService(PowerManager::class.java)
        val wasInteractive = power.isInteractive
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            activity.onActivity { it.startForegroundService(Intent(it, ListeningService::class.java)) }
            withTimeout(20_000) {
                while (!ListeningService.state.value.recording) {
                    check(ListeningService.state.value.processing || ListeningService.state.value.note == "Microphone is off") { ListeningService.state.value.note }
                    delay(100)
                }
            }
            val cpuStart = android.os.Process.getElapsedCpuTime()
            val memoryStart = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
            shell("input keyevent KEYCODE_SLEEP")
            val start = SystemClock.elapsedRealtime()
            for (minute in 1..6) {
                delay(60_000)
                val state = ListeningService.state.value
                assertTrue("Microphone session stopped: ${state.note}", state.recording)
                assertFalse("Screen should remain off", power.isInteractive)
                assertEquals("Digital silence must not trigger Whisper", 0, state.chunks)
                assertFalse(state.speechActive)
                println("CONTINUOUS_EMULATOR minute=$minute; capturedSeconds=${state.seconds}; chunks=${state.chunks}")
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val cpu = android.os.Process.getElapsedCpuTime() - cpuStart
            val memoryEnd = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
            assertTrue(ListeningService.state.value.seconds >= 350)
            println("CONTINUOUS_EMULATOR elapsedMs=$elapsed; processCpuMs=$cpu; pssStartKb=$memoryStart; pssEndKb=$memoryEnd")
            app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.FINISH))
            withTimeout(15_000) { while (ListeningService.state.value.recording || ListeningService.state.value.processing) delay(100) }
            assertTrue(ListeningService.state.value.note.startsWith("No speech detected"))
        } finally {
            if (ListeningService.state.value.recording || ListeningService.state.value.processing)
                runCatching { app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.CANCEL)) }
            shell("input keyevent KEYCODE_WAKEUP")
            activity.close()
            if (!wasInteractive) shell("input keyevent KEYCODE_SLEEP")
        }
    }

    @Test fun realVadResumesAfterRepeatedLongPausesWithoutTranscribingSilence() = runBlocking {
        val app = application(emulator = false)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val audio = instrumentation.context.assets.open("jfk.wav").use { WavAudio.decode(it.readBytes()) }
        val results = mutableListOf<String>()
        val cpuStart = android.os.Process.getElapsedCpuTime()
        val clockStart = SystemClock.elapsedRealtime()
        NativeVad.open(VadModel.install(app))
        val processor = LiveSpeechProcessor(app.speech) { text, _, _ -> results += text }
        try {
            coroutineScope {
                val worker = launch { processor.consume() }
                suspend fun feed(samples: FloatArray) {
                    for (offset in samples.indices step 4096) {
                        val frame = samples.copyOfRange(offset, minOf(offset + 4096, samples.size))
                        try { processor.accept(frame) } finally { frame.fill(0f) }
                        delay(frame.size * 1000L / 16000)
                    }
                }
                feed(FloatArray(16000 * 10))
                assertEquals(0, processor.transcriptionCount)
                repeat(3) { cycle ->
                    val before = processor.transcriptionCount
                    feed(audio)
                    feed(FloatArray(16000 * 5)) // Flush the utterance and let queued transcription complete.
                    assertTrue("Speech must resume processing in each cycle", processor.transcriptionCount > before)
                    val settled = processor.transcriptionCount
                    feed(FloatArray(16000 * 15))
                    assertEquals("Long silence must not invoke Whisper", settled, processor.transcriptionCount)
                    println("CONTINUOUS_PIXEL cycle=${cycle + 1}; transcriptions=$settled")
                }
                processor.finish()
                withTimeout(20_000) { worker.join() }
            }
            assertTrue(results.joinToString(" ").lowercase().contains("country"))
            println("CONTINUOUS_PIXEL elapsedMs=${SystemClock.elapsedRealtime() - clockStart}; processCpuMs=${android.os.Process.getElapsedCpuTime() - cpuStart}; transcriptions=${processor.transcriptionCount}")
        } finally { processor.close(); NativeVad.close(); audio.fill(0f) }
    }
}
