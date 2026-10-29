package com.sidekick.app.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.media.*
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.sidekick.app.SidekickApplication
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.*
import kotlin.math.sqrt

/** On-demand Android dictation adapter. Continuous capture uses ListeningService instead. */
class SidekickRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    @Volatile private var stop = false
    @SuppressLint("MissingPermission")
    override fun onStartListening(recognizerIntent: Intent, callback: Callback) {
        if (work?.isActive == true || ListeningService.state.value.recording || ListeningService.state.value.processing) {
            callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return
        }
        stop = false
        work = scope.launch {
            val buffer = FloatArray(240_000)
            val frame = ShortArray(2048)
            var audio: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 16_000))
                check(audio.state == AudioRecord.STATE_INITIALIZED)
                audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                callback.readyForSpeech(Bundle())
                var count = 0
                var began = false
                var lastSpeech = SystemClock.elapsedRealtime()
                val started = lastSpeech
                while (!stop && count < buffer.size) {
                    ensureActive()
                    val read = audio.read(frame, 0, minOf(frame.size, buffer.size - count))
                    check(read > 0)
                    val level = sqrt((0 until read).sumOf { frame[it].toDouble() * frame[it] } / read) / 32768
                    for (i in 0 until read) buffer[count + i] = frame[i] / 32768f
                    count += read; frame.fill(0)
                    val now = SystemClock.elapsedRealtime()
                    if (level > .01) {
                        lastSpeech = now
                        if (!began) { began = true; callback.beginningOfSpeech() }
                    }
                    if (began && now - lastSpeech > 1500 || !began && now - started > 5000) break
                }
                audio.stop(); audio.release(); audio = null
                if (!began) { callback.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); return@launch }
                callback.endOfSpeech()
                val text = (application as SidekickApplication).speech.transcribe(buffer.copyOf(count))
                if (text.isBlank()) callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                else callback.results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { runCatching { callback.error(SpeechRecognizer.ERROR_AUDIO) } }
            finally { runCatching { audio?.stop() }; audio?.release(); frame.fill(0); buffer.fill(0f) }
        }
    }
    override fun onStopListening(callback: Callback) { stop = true }
    override fun onCancel(callback: Callback) { stop = true; work?.cancel() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
