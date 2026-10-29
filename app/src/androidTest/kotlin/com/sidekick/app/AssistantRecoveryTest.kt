package com.sidekick.app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.assistant.SidekickVoiceService
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AssistantRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SidekickApplication
    private fun emulatorOnly() {
        assumeTrue(app.packageName.endsWith(".benchmark"))
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.PRODUCT.startsWith("sdk_"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyAssistant") == "true")
        assertTrue("Select the benchmark assistant on the emulator first", app.assistant.isSelected())
    }
    @Test fun registeredAssistantUsesSystemBindingPermissionAndDoesNotArmBySelection() {
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val info = app.packageManager.getServiceInfo(ComponentName(app, SidekickVoiceService::class.java), android.content.pm.PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_VOICE_INTERACTION", info.permission)
        assertTrue(info.exported); assertTrue(info.metaData.containsKey("android.voice_interaction"))
        app.assistant.disarm()
        assertFalse(app.assistant.armed); assertFalse(app.assistant.recover())
        assertFalse(ListeningService.state.value.recording)
    }
    @Test fun enabledAssistantCanResumeAndExplicitStopPreventsRearming() = runBlocking {
        emulatorOnly()
        val previous = app.assistant.enabled.value
        try {
            app.assistant.disarm(); app.assistant.setEnabled(true)
            assertFalse(app.assistant.recover())
            app.assistant.rememberUserStart()
            assertTrue(app.assistant.armed); assertTrue(app.assistant.recover())
            withTimeout(30_000) { while (!ListeningService.state.value.recording) delay(100) }
            app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.FINISH))
            withTimeout(15_000) { while (ListeningService.state.value.recording || ListeningService.state.value.processing || app.assistant.armed) delay(100) }
            assertFalse(app.assistant.recover())
            assertFalse(com.sidekick.app.assistant.AssistantControl(app).armed)
        } finally {
            app.assistant.disarm(); app.stopService(Intent(app, ListeningService::class.java)); app.assistant.setEnabled(previous)
        }
    }
    @Test fun recognitionAdapterRejectsCompetingCaptureAndTimesOutOnSilence() = runBlocking {
        emulatorOnly()
        var recognizer: SpeechRecognizer? = null
        suspend fun recognizeError(): Int {
            val result = CompletableDeferred<Int>()
            instrumentation.runOnMainSync {
                recognizer = SpeechRecognizer.createSpeechRecognizer(app, ComponentName(app, com.sidekick.app.assistant.SidekickRecognitionService::class.java)).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) { result.complete(error) }
                        override fun onResults(results: Bundle?) { result.complete(-1) }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                }
            }
            return try { withTimeout(15_000) { result.await() } }
            finally { instrumentation.runOnMainSync { recognizer?.destroy(); recognizer = null } }
        }
        try {
            app.assistant.setEnabled(true); app.assistant.rememberUserStart(); assertTrue(app.assistant.recover())
            withTimeout(30_000) { while (!ListeningService.state.value.recording) delay(100) }
            assertEquals(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, recognizeError())
            app.startService(Intent(app, ListeningService::class.java).setAction(ListeningService.FINISH))
            withTimeout(15_000) { while (ListeningService.state.value.recording || ListeningService.state.value.processing) delay(100) }
            assertEquals(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, recognizeError())
        } finally { app.assistant.disarm(); app.stopService(Intent(app, ListeningService::class.java)) }
    }
    /** Host-only recovery trial, leaves the emulator's silent virtual mic active for a process-kill check. */
    @Test fun prepareEmulatorForSystemRecoveryTrial() = runBlocking {
        emulatorOnly()
        assumeTrue(InstrumentationRegistry.getArguments().getString("leaveAssistantArmed") == "true")
        app.assistant.setEnabled(true); app.assistant.rememberUserStart()
        assertTrue(app.assistant.recover())
        withTimeout(30_000) { while (!ListeningService.state.value.recording) delay(100) }
    }
}
