package com.sidekick.app

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Plays "another app using the microphone": the test copy opens the microphone in the foreground for -e seconds
 * (default 12), then lets go, so the installed SideKick's hand-back can be watched from outside. Opt-in: -e micTakeover true.
 */
class MicTakeoverTest {
    @Test fun holdTheMicrophone() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("micTakeover") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        val activity = ActivityScenario.launch(MainActivity::class.java) // in front, so Android gives it the microphone
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 64_000)
        try {
            record.startRecording()
            val buffer = ShortArray(4096)
            val until = android.os.SystemClock.elapsedRealtime() + (args.getString("seconds")?.toLong() ?: 12) * 1000
            while (android.os.SystemClock.elapsedRealtime() < until) record.read(buffer, 0, buffer.size) // audio discarded
            buffer.fill(0)
        } finally { record.stop(); record.release(); activity.close() }
    }
}
