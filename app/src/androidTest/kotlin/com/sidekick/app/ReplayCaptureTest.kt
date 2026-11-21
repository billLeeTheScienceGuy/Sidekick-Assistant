package com.sidekick.app

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Opt-in replay capture for owner-matching evaluation (benchmark package only): records the phone microphone the same
 * way ListeningService does (MIC, 16 kHz mono) while the laptop plays public voice clips out loud
 * (scripts/laptop-eval/replay.ps1). The WAV stays in this test package's cache until the laptop pulls and deletes it.
 *
 * adb shell am instrument -w -e class com.sidekick.app.ReplayCaptureTest -e replayCapture true -e seconds 900 \
 *   com.sidekick.app.benchmark.test/androidx.test.runner.AndroidJUnitRunner
 */
class ReplayCaptureTest {
    @Test fun recordReplayedVoices() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("replayCapture") == "true")
        val i = InstrumentationRegistry.getInstrumentation(); val app = i.targetContext
        check(app.packageName.endsWith(".benchmark"))
        val seconds = arguments.getString("seconds")?.toIntOrNull()?.coerceIn(10, 1800) ?: 60
        i.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        // The microphone only delivers audio to a foreground app.
        // Wake the screen and keep it on for this window only (no system setting changes).
        i.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val pcm = ShortArray(seconds * 16_000)
        try {
            val size = maxOf(AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192)
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            try {
                record.startRecording()
                println("REPLAY_CAPTURE started")
                var filled = 0
                while (filled < pcm.size) {
                    val n = record.read(pcm, filled, minOf(4096, pcm.size - filled))
                    check(n >= 0) { "Microphone read failed: $n" }
                    filled += n
                }
            } finally { runCatching { record.stop() }; record.release() }
        } finally { activity.close() }
        check(pcm.any { kotlin.math.abs(it.toInt()) > 64 }) { "The microphone delivered only silence. Is microphone access turned off?" }
        // The playlist never pauses longer than 1.5 s: 10 s of digital silence means the app lost the microphone.
        val silentSeconds = (0 until pcm.size / 16_000).map { s -> (s * 16_000 until (s + 1) * 16_000).all { pcm[it].toInt() == 0 } }
        val lost = silentSeconds.windowed(10).indexOfFirst { window -> window.all { it } }
        check(lost < 0 || lost > seconds - 25) { "The microphone went silent at ${lost}s (app left the foreground or the screen locked)." }
        val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray()).put("fmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { bytes.putShort(it) }
        File(app.cacheDir, "replay.wav").writeBytes(bytes.array())
        pcm.fill(0)
        println("REPLAY_CAPTURE saved ${pcm.size / 16_000}s")
    }
}
