package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Changes only the emulator's mute state. Never opens a physical microphone. */
class CaptureFaultTest {
    @Test fun globalMicrophoneMuteStopsCaptureAndDoesNotResumeWithoutAStart() = runBlocking<Unit> {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("runCaptureFaults") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as SidekickApplication
        check(app.packageName.endsWith(".benchmark"))
        i.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        i.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val audio = app.getSystemService(AudioManager::class.java)
        val previous = audio.isMicrophoneMute
        i.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            audio.isMicrophoneMute = false
            activity.onActivity { it.startForegroundService(Intent(it, ListeningService::class.java)) }
            withTimeout(20_000) { while (!ListeningService.state.value.recording) { delay(50) } }
            audio.isMicrophoneMute = true
            assertTrue(audio.isMicrophoneMute)
            withTimeout(8_000) { while (ListeningService.state.value.recording || ListeningService.state.value.processing) delay(50) }
            assertTrue(ListeningService.state.value.note, ListeningService.state.value.note.contains("muted", true))
            assertFalse(app.assistant.armed)
            val notification = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                .single { it.tag == com.sidekick.app.audio.CaptureStatusAlerts.TAG }
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.notification.visibility)
            assertEquals("SideKick listening stopped", notification.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            audio.isMicrophoneMute = false
            delay(1_000)
            assertFalse("Removing mute must not silently re-arm a stopped session", ListeningService.state.value.recording)
        } finally {
            app.assistant.disarm(); app.stopService(Intent(app, ListeningService::class.java))
            audio.isMicrophoneMute = previous; i.uiAutomation.dropShellPermissionIdentity(); activity.close()
            com.sidekick.app.audio.CaptureStatusAlerts.clear(app)
        }
    }
}
