package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Real microphone, Start the supported way (from the open app), screen turned off by the test.
 * Opt-in: -e batteryTest true. idleCpu: -e mode legacy|new -e minutes 5 → files/evaluation/cpu-<mode>.txt.
 */
class BatteryListeningTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SidekickApplication
    private fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
    private fun cpuTicks(): Long = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ').let { it[11].toLong() + it[12].toLong() }

    private suspend fun startListening(): ActivityScenario<MainActivity> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("batteryTest") == "true")
        assumeTrue(app.packageName.endsWith(".benchmark"))
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        val activity = ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity { it.startForegroundService(Intent(it, ListeningService::class.java)) }
        withTimeout(30_000) { ListeningService.state.first { it.recording } }
        return activity
    }

    private suspend fun stopListening(activity: ActivityScenario<MainActivity>) {
        shell("input keyevent KEYCODE_WAKEUP")
        app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.FINISH))
        withTimeout(30_000) { while (ListeningService.state.value.recording || ListeningService.state.value.processing) delay(200) }
        activity.close()
    }

    @Test fun idleCpu() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("mode") ?: "new"
        val minutes = args.getString("minutes")?.toLong() ?: 5
        val switches = app.getSharedPreferences("test-switches", android.content.Context.MODE_PRIVATE)
        switches.edit().putBoolean("legacy_capture", mode == "legacy").remove("rest_quiet_ms").commit()
        val oldPause = app.guidance.pauseWhenResting.value
        app.guidance.setPauseWhenResting(false)
        val activity = startListening()
        try {
            delay(20_000) // models settle
            shell("input keyevent KEYCODE_SLEEP"); delay(5_000)
            val t0 = SystemClock.elapsedRealtime(); val c0 = cpuTicks(); val chunks0 = ListeningService.state.value.chunks
            delay(minutes * 60_000)
            val seconds = (SystemClock.elapsedRealtime() - t0) / 1000.0; val used = (cpuTicks() - c0) / 100.0
            val state = ListeningService.state.value
            assertTrue("listening stopped: ${state.note}", state.recording)
            val line = String.format(java.util.Locale.US, "%s: %.2f CPU-s in %.0f s = %.3f CPU-s per minute (phrases %d, still recording %s)",
                mode, used, seconds, used / seconds * 60, state.chunks - chunks0, state.recording)
            File(File(app.filesDir, "evaluation").apply { mkdirs() }, "cpu-$mode.txt").writeText(line + "\n")
            android.util.Log.i("SidekickBattery", line)
        } finally { stopListening(activity); switches.edit().clear().commit(); app.guidance.setPauseWhenResting(oldPause) }
    }

    @Test fun restingPausesAndResumes() = runBlocking<Unit> {
        val switches = app.getSharedPreferences("test-switches", android.content.Context.MODE_PRIVATE)
        switches.edit().putLong("rest_quiet_ms", 20_000).putBoolean("legacy_capture", false).commit()
        val oldPause = app.guidance.pauseWhenResting.value
        app.guidance.setPauseWhenResting(true)
        val activity = startListening()
        try {
            shell("input keyevent KEYCODE_SLEEP")
            // 20 s without speech, screen off, not carried: the microphone turns off.
            withTimeout(120_000) { ListeningService.state.first { it.note.startsWith("Paused") } }
            assertTrue(ListeningService.state.value.recording) // the session continues, only the microphone is off
            delay(10_000)
            assertTrue("stays paused while untouched", ListeningService.state.value.note.startsWith("Paused"))
            shell("input keyevent KEYCODE_WAKEUP")
            withTimeout(30_000) { ListeningService.state.first { it.note.contains("resumed") || it.note.startsWith("Listening") } }
            delay(5_000)
            val state = ListeningService.state.value
            assertTrue("microphone restarted and kept recording: ${state.note}", state.recording && !state.note.startsWith("Paused") && !state.note.contains("stopped", true))
        } finally { stopListening(activity); switches.edit().clear().commit(); app.guidance.setPauseWhenResting(oldPause) }
    }
}
