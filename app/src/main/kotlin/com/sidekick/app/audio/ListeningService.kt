package com.sidekick.app.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import com.sidekick.app.MainActivity
import com.sidekick.app.SidekickApplication
import com.sidekick.core.Pixel7aProfile
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class ListeningState(
    val recording: Boolean = false, val processing: Boolean = false, val level: Float = 0f,
    val seconds: Int = 0, val note: String = "Microphone is off", val transcript: String? = null,
    val resultId: String? = null, val capturedAt: Long = 0, val zoneId: String = ZoneId.systemDefault().id,
)

/** One explicitly started, bounded utterance. PCM is never written to disk. */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Job? = null
    @Volatile private var finish = false
    private val speech get() = (application as SidekickApplication).speech

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FINISH -> { finish = true; return START_NOT_STICKY }
            CANCEL -> { capture?.cancel(); stopSelf(); return START_NOT_STICKY }
        }
        if (capture?.isActive == true) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ListeningState(note = "Microphone permission is required")
            stopSelf(); return START_NOT_STICKY
        }
        finish = false
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Recording and transcription", NotificationManager.IMPORTANCE_LOW))
            val notification = notification("Recording • up to 30 seconds", true)
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, notification)
            mutableState.value = ListeningState(recording = true, note = "Listening to your voice…")
            capture = scope.launch { captureAndTranscribe() }
        } catch (e: Exception) {
            mutableState.value = ListeningState(note = "Android could not start recording. Open the app and try again.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(label: String, recording: Boolean): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getService(this, 3, Intent(this, ListeningService::class.java).setAction(FINISH), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SideKick · $label").setContentText("On-device only. Raw audio is discarded.")
            .setContentIntent(open).setOngoing(true)
            .apply { if (recording) addAction(Notification.Action.Builder(null, "Finish", done).build()) }
            .addAction(Notification.Action.Builder(null, "Discard", cancel).build()).build()
    }

    @Suppress("MissingPermission")
    private suspend fun captureAndTranscribe() {
        val rate = Pixel7aProfile.sampleRate
        val samples = FloatArray(rate * Pixel7aProfile.maxUtteranceSeconds)
        val frame = ShortArray(1600)
        val captured = Instant.now().toEpochMilli()
        val zone = ZoneId.systemDefault().id
        var recorder: AudioRecord? = null
        var count = 0
        var audibleSamples = 0
        try {
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
            while (!finish && count < samples.size && android.os.SystemClock.elapsedRealtime() < deadline) {
                currentCoroutineContext().ensureActive()
                val read = audio.read(frame, 0, minOf(frame.size, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                check(read >= 0)
                if (read > 0) {
                    val rms = sqrt((0 until read).sumOf { (frame[it] / 32768.0).let { v -> v * v } } / read)
                    if (rms > 0.008) audibleSamples += read // Silence gate only; not speaker recognition/VAD.
                    for (i in 0 until read) samples[count + i] = frame[i] / 32768f
                    count += read
                    mutableState.value = ListeningState(recording = true, level = rms.toFloat(), seconds = count / rate,
                        note = "Recording… tap Finish when done")
                }
                delay(30)
            }
            audio.stop(); audio.release(); recorder = null
            if (count < rate / 2 || audibleSamples < rate / 5) {
                mutableState.value = ListeningState(note = "No clear audio captured. Try speaking closer to the microphone.")
                return
            }
            mutableState.value = ListeningState(processing = true, note = "Transcribing locally… first use installs the bundled model")
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification("Transcribing locally", false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else getSystemService(NotificationManager::class.java).notify(1, notification("Transcribing locally", false))
            val text = withTimeout(120_000) { speech.transcribe(samples.copyOf(count)) }
            currentCoroutineContext().ensureActive()
            mutableState.value = ListeningState(
                note = if (text.isBlank()) "No speech recognized. Try again in a quieter setting." else "Transcript ready. Review it before saving commitments.",
                transcript = text.takeIf(String::isNotBlank), resultId = UUID.randomUUID().toString(),
                capturedAt = captured, zoneId = zone,
            )
        } catch (e: TimeoutCancellationException) {
            mutableState.value = ListeningState(note = "Transcription took too long. Try a shorter recording.")
        } catch (e: CancellationException) {
            mutableState.value = ListeningState(note = "Recording/transcription discarded")
            throw e
        } catch (e: Exception) {
            mutableState.value = ListeningState(note = "Recording or transcription failed. Check that this build includes its speech model, then try again.")
        } finally {
            runCatching { recorder?.stop() }; recorder?.release()
            frame.fill(0); samples.fill(0f)
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
        fun consumeTranscript(id: String) {
            if (mutableState.value.resultId == id) mutableState.value = mutableState.value.copy(transcript = null)
        }
    }
}
