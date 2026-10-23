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
    val note: String = "Microphone is off",
)

/** Explicitly enabled continuous listening. Only bounded RAM buffers hold audio. */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Job? = null
    @Volatile private var finish = false
    @Volatile private var discard = false
    private val app get() = application as SidekickApplication

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FINISH -> { finish = true; if (capture == null) stopSelf(); return START_NOT_STICKY }
            CANCEL -> { discard = true; capture?.cancel(); if (capture == null) stopSelf(); return START_NOT_STICKY }
        }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (capture != null) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ListeningState(note = "Microphone permission is required")
            stopSelf(); return START_NOT_STICKY
        }
        finish = false; discard = false
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Listening and transcription", NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, notification(true))
            mutableState.value = ListeningState(processing = true, note = "Preparing local speech detection…")
            capture = scope.launch { runSession() }
        } catch (_: Exception) {
            mutableState.value = ListeningState(note = "Android could not start listening. Open the app and try again.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(recording: Boolean): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getService(this, 3, Intent(this, ListeningService::class.java).setAction(FINISH), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (recording) "SideKick · Listening" else "SideKick · Finishing transcript")
            .setContentText("On-device only. No audio files. Tap to review.")
            .setContentIntent(open).setOngoing(true)
            .apply { if (recording) addAction(Notification.Action.Builder(null, "Finish", done).build()) }
            .addAction(Notification.Action.Builder(null, "Discard session", cancel).build()).build()
    }

    @Suppress("MissingPermission")
    private suspend fun runSession() {
        var recorder: AudioRecord? = null
        var processor: LiveSpeechProcessor? = null
        val raw = ShortArray(4096)
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
            app.temporary.beginSession()
            coroutineScope {
                val pipeline = LiveSpeechProcessor(app.speech) { text, startSample, latency ->
                    ensureActive()
                    if (!discard) app.temporary.appendLive(text, started.plusMillis(startSample * 1000 / 16000), zone)
                    mutableState.update { it.copy(chunks = it.chunks + 1, lastLatencyMs = latency,
                        note = if (it.recording) "Listening · live text is ready for review" else "Finishing remaining speech…") }
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
                        try { pipeline.accept(samples) } finally { samples.fill(0f); raw.fill(0) }
                        filled = 0
                        mutableState.update { it.copy(level = level, seconds = ((SystemClock.elapsedRealtime() - startedClock) / 1000).toInt()) }
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
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (it.chunks == 0) "No speech detected. Try speaking closer to the microphone." else "Session finished. Review and approve commitments below.") }
        } catch (e: CancellationException) {
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (discard) "Session discarded" else "Listening interrupted. Review text captured so far.") }
            throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = e.message ?: "Listening failed. Review the text captured so far, then try again.") }
        } finally {
            runCatching { recorder?.stop() }; recorder?.release()
            processor?.close(); raw.fill(0)
            NativeVad.close()
            if (wake.isHeld) wake.release()
            if (discard) app.temporary.discardSession()
            stopSelf()
        }
    }

    override fun onTimeout(startId: Int) { capture?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        private const val CHANNEL = "listening"
        const val FINISH = "com.sidekick.FINISH"
        const val CANCEL = "com.sidekick.CANCEL"
        private val mutableState = MutableStateFlow(ListeningState())
        val state = mutableState.asStateFlow()
    }
}
