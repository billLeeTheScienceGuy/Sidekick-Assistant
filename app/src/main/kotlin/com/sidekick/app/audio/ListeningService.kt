package com.sidekick.app.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.PowerManager
import com.sidekick.app.MainActivity
import com.sidekick.app.SidekickApplication
import com.sidekick.app.speech.NativeVad
import com.sidekick.app.speech.VadModel
import com.sidekick.app.speaker.SpeakerEngine
import com.sidekick.core.Speaker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

data class ListeningState(
    val recording: Boolean = false, val processing: Boolean = false, val level: Float = 0f,
    val seconds: Int = 0, val chunks: Int = 0, val lastLatencyMs: Long = 0,
    val speechActive: Boolean = false,
    val enrolling: Boolean = false, val voiceSamples: Int = 0,
    val voiceStatus: String = "Speaker unassigned",
    val userVoiceChunks: Int = 0, val otherVoiceChunks: Int = 0, val unknownVoiceChunks: Int = 0,
    val voiceDiagnostics: List<String> = emptyList(),
    val recognitionDiagnostics: List<String> = emptyList(),
    val note: String = "Microphone is off",
)

/** Explicitly enabled continuous listening. Only bounded RAM buffers hold audio. */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Job? = null
    @Volatile private var finish = false
    @Volatile private var discard = false
    private var enrollment = false
    private val app get() = application as SidekickApplication

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FINISH -> { app.assistant.disarm(); finish = true; if (capture == null) stopSelf(); return START_NOT_STICKY }
            CANCEL -> { app.assistant.disarm(); discard = true; capture?.cancel(); if (capture == null) stopSelf(); return START_NOT_STICKY }
        }
        val recovering = intent == null || intent.action == RESUME
        if (recovering && !app.assistant.canRecover()) { stopSelf(); return START_NOT_STICKY }
        if (capture != null) return if (app.assistant.armed && !enrollment) START_STICKY else START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ListeningState(note = "Microphone permission is required")
            app.assistant.disarm("Microphone permission is required")
            stopSelf(); return START_NOT_STICKY
        }
        finish = false; discard = false
        enrollment = intent?.getBooleanExtra(ENROLL, false) == true
        if (!enrollment && !recovering) app.assistant.rememberUserStart()
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Listening and transcription", NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, notification(true))
            mutableState.value = ListeningState(processing = true, enrolling = enrollment, note = "Preparing local speech detection…")
            capture = scope.launch { runSession() }
        } catch (_: Exception) {
            app.assistant.disarm("Android could not resume listening. Open SideKick and start again.")
            mutableState.value = ListeningState(note = "Android could not start listening. Open the app and try again.")
            stopSelf()
        }
        return if (!enrollment && app.assistant.armed) START_STICKY else START_NOT_STICKY
    }

    private fun notification(recording: Boolean): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getService(this, 3, Intent(this, ListeningService::class.java).setAction(FINISH), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (recording) "SideKick · Listening" else "SideKick · Finishing transcript")
            .setContentText(if (app.guidance.handsOffMode.value && !enrollment) "On-device · events save automatically · Undo in event notifications" else "On-device only. No audio files. Tap to review.")
            .setContentIntent(open).setOngoing(true)
            .apply { if (recording) addAction(Notification.Action.Builder(null, "Finish", done).build()) }
            .addAction(Notification.Action.Builder(null, if (app.guidance.handsOffMode.value && !enrollment) "Stop & clear text" else "Discard session", cancel).build()).build()
    }

    @Suppress("MissingPermission")
    private suspend fun runSession() {
        var recorder: AudioRecord? = null
        var processor: LiveSpeechProcessor? = null
        val raw = ShortArray(4096)
        val setupLanguage = app.guidance.speechLanguage.value
        app.voiceProfile.selectLanguage(setupLanguage)
        val setupModel = app.voiceProfile.model.value
        val speaker = SpeakerEngine(this, app.voiceProfile)
        val enrollmentSamples = mutableListOf<FloatArray>()
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SideKick:LiveNotes")
        wake.setReferenceCounted(false)
        try {
            wake.acquire(10 * 60_000L)
            NativeVad.open(VadModel.install(this))
            currentCoroutineContext().ensureActive()
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "This microphone configuration is unavailable." }
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 64_000))
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not initialize." }
            val started = Instant.now()
            val zone = ZoneId.systemDefault()
            val startedClock = SystemClock.elapsedRealtime()
            if (!enrollment) app.temporary.beginSession()
            coroutineScope {
                var identified = Speaker.UNKNOWN
                var continuation = false
                val pipeline = LiveSpeechProcessor(app.speech, onBoundary = { continuation = it }, contextKey = { if (identified == Speaker.USER) "user" else null }, inspect = { samples ->
                    if (enrollment) {
                        if (!discard && enrollmentSamples.size < 3 && samples.size >= 48_000) {
                            enrollmentSamples += speaker.embedding(samples)
                            mutableState.update { it.copy(voiceSamples = enrollmentSamples.size, note = "Voice setup: ${enrollmentSamples.size}/3 samples") }
                            if (enrollmentSamples.size == 3) {
                                finish = true
                            }
                        }
                        false
                    } else {
                        identified = speaker.identify(samples)
                        mutableState.update { it.copy(userVoiceChunks = it.userVoiceChunks + if (identified == Speaker.USER) 1 else 0,
                            voiceDiagnostics = (it.voiceDiagnostics + speaker.lastDiagnostic).takeLast(20),
                            otherVoiceChunks = it.otherVoiceChunks + if (identified == Speaker.OTHER) 1 else 0,
                            unknownVoiceChunks = it.unknownVoiceChunks + if (identified == Speaker.UNKNOWN) 1 else 0,
                            voiceStatus = when (identified) {
                            Speaker.USER -> "Likely your voice"
                            Speaker.OTHER -> "Another voice · information only"
                            Speaker.UNKNOWN -> "Speaker unassigned · information only"
                        }) }
                        true
                    }
                }) { text, startSample, latency ->
                    ensureActive()
                    if (!discard) {
                        app.temporary.appendAttributedLive(text, started.plusMillis(startSample * 1000 / 16000), zone, identified, continuation)
                        app.automation.process()
                    }
                    mutableState.update { it.copy(chunks = it.chunks + 1, lastLatencyMs = latency,
                        recognitionDiagnostics = (it.recognitionDiagnostics + listOfNotNull(app.speech.lastClockDiagnostic)).takeLast(5),
                        note = if (it.recording) { if (app.guidance.handsOffMode.value) "Listening · saving events automatically" else "Listening · live text is ready for review" } else "Finishing remaining speech…") }
                }
                processor = pipeline
                val worker = launch { pipeline.consume() }
                if (!finish) audio.startRecording()
                check(finish || audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start." }
                mutableState.update { it.copy(recording = !finish, processing = false, note = "Listening · waiting for speech") }
                var filled = 0
                var renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                while (!finish) {
                    ensureActive()
                    if (enrollment && SystemClock.elapsedRealtime() - startedClock >= 45_000) { finish = true; break }
                    if (SystemClock.elapsedRealtime() >= renewAt) {
                        wake.acquire(10 * 60_000L)
                        renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                    }
                    if (Build.VERSION.SDK_INT >= 29) check(audio.activeRecordingConfiguration?.isClientSilenced != true) {
                        "Android muted the microphone. Listening stopped; check other audio apps and microphone privacy settings."
                    }
                    val read = audio.read(raw, filled, raw.size - filled, AudioRecord.READ_NON_BLOCKING)
                    check(read >= 0) { "Microphone disconnected. Listening stopped." }
                    filled += read
                    if (filled == raw.size) {
                        val samples = FloatArray(filled) { raw[it] / 32768f }
                        val level = sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat()
                        val detected = try { pipeline.accept(samples) } finally { samples.fill(0f); raw.fill(0) }
                        filled = 0
                        mutableState.update { it.copy(level = level, speechActive = detected, seconds = ((SystemClock.elapsedRealtime() - startedClock) / 1000).toInt()) }
                    }
                    delay(10)
                }
                if (filled > 0) {
                    val tail = FloatArray(filled) { raw[it] / 32768f }
                    try { pipeline.accept(tail) } finally { tail.fill(0f); raw.fill(0) }
                }
                runCatching { audio.stop() }; audio.release(); recorder = null
                mutableState.update { it.copy(recording = false, processing = true, level = 0f, note = "Finishing remaining speech…") }
                if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                else getSystemService(NotificationManager::class.java).notify(1, notification(false))
                pipeline.finish()
                worker.join()
            }
            currentCoroutineContext().ensureActive()
            if (enrollment && !discard && enrollmentSamples.size == 3) {
                check(app.voiceProfile.language.value == setupLanguage && app.voiceProfile.model.value == setupModel) { "Voice settings changed. Repeat setup in the selected language." }
                app.voiceProfile.save(enrollmentSamples)
            }
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (enrollment) {
                    if (enrollmentSamples.size == 3) "Voice setup complete. Your local voice profile is ready."
                    else "Voice setup incomplete. Speak in longer phrases and try again; any previous profile is unchanged."
                } else if (it.chunks == 0) "No speech detected. Try speaking closer to the microphone." else if (app.guidance.handsOffMode.value) app.automation.status.value else "Session finished. Review and approve commitments below.") }
        } catch (e: CancellationException) {
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (discard) "Session discarded" else "Listening interrupted. Review text captured so far.") }
            throw e
        } catch (e: Exception) {
            if (!enrollment) app.assistant.disarm("Listening paused after an error. Start again when ready.")
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = e.message ?: "Listening failed. Review the text captured so far, then try again.") }
        } finally {
            runCatching { recorder?.stop() }; recorder?.release()
            processor?.close(); raw.fill(0); speaker.close(); enrollmentSamples.forEach { it.fill(0f) }
            NativeVad.close()
            if (wake.isHeld) wake.release()
            if (discard && !enrollment) app.temporary.discardSession()
            stopSelf()
        }
    }

    override fun onTimeout(startId: Int) { app.assistant.disarm("Android stopped listening"); capture?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        private const val CHANNEL = "listening"
        const val FINISH = "com.sidekick.FINISH"
        const val CANCEL = "com.sidekick.CANCEL"
        const val ENROLL = "com.sidekick.ENROLL"
        const val RESUME = "com.sidekick.RESUME_ASSISTANT"
        private val mutableState = MutableStateFlow(ListeningState())
        val state = mutableState.asStateFlow()
    }
}
