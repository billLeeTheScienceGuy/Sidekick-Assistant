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
    val diarizationMs: Long = 0, val speakerParts: Int = 0,
    val recognizer: String? = null,
    val note: String = "Microphone is off",
    /** Which consent rule applies and what it is doing right now (see RecordingConsent in core). */
    val consent: String = "",
    /** True while other people's words are being dropped because notice has not been given. */
    val noticeNeeded: Boolean = false,
    /** Phrases from other voices discarded on arrival this session. */
    val droppedChunks: Int = 0,
)

/** Explicitly enabled continuous listening. Only bounded RAM buffers hold audio. */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Job? = null
    @Volatile private var finish = false
    @Volatile private var discard = false
    private var enrollment = false
    private var enrollmentTarget: com.sidekick.core.SpeechLanguage? = null
    @Volatile private var gate: com.sidekick.core.ConsentGate? = null
    private var noticeVoice: android.speech.tts.TextToSpeech? = null
    private val app get() = application as SidekickApplication

    private fun refreshConsent() {
        val g = gate ?: return
        val now = SystemClock.elapsedRealtime()
        mutableState.update { it.copy(consent = g.status(now) + " · " + g.place.reason,
            noticeNeeded = g.place.rule == com.sidekick.core.RecordingJurisdiction.Rule.ALL_PARTY && g.noticeAtMs == null) }
    }

    /** The legal-minimum notice for all-party places: an audible line, then others' words are kept. */
    private fun announce() {
        val g = gate ?: return
        noticeVoice?.shutdown()
        noticeVoice = android.speech.tts.TextToSpeech(this) { status ->
            val tts = noticeVoice
            if (status == android.speech.tts.TextToSpeech.SUCCESS && tts != null) {
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { g.noticeGiven(SystemClock.elapsedRealtime()); refreshConsent() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) {
                        mutableState.update { it.copy(note = "Couldn't play the notice. Tell everyone you're taking notes, then tap \"I told everyone\".") }
                    }
                })
                tts.speak(NOTICE, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "sidekick-notice")
            } else mutableState.update { it.copy(note = "No text-to-speech voice. Tell everyone you're taking notes, then tap \"I told everyone\".") }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FINISH -> { app.assistant.disarm(); finish = true; if (capture == null) stopSelf(); return START_NOT_STICKY }
            CANCEL -> { app.assistant.disarm(); discard = true; capture?.cancel(); if (capture == null) stopSelf(); return START_NOT_STICKY }
            ANNOUNCE -> { announce(); return START_NOT_STICKY }
            NOTICE_GIVEN -> { gate?.noticeGiven(SystemClock.elapsedRealtime()); refreshConsent(); return START_NOT_STICKY }
        }
        val recovering = intent == null || intent.action == RESUME
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ListeningState(note = "Microphone permission is required")
            app.assistant.disarm("Microphone permission is required")
            if (recovering) CaptureStatusAlerts.stopped(this)
            stopSelf(); return START_NOT_STICKY
        }
        if (recovering && !app.assistant.canRecover()) { stopSelf(); return START_NOT_STICKY }
        if (capture != null) return if (app.assistant.armed && !enrollment) START_STICKY else START_NOT_STICKY
        finish = false; discard = false
        enrollment = intent?.getBooleanExtra(ENROLL, false) == true
        enrollmentTarget = intent?.getStringExtra(ENROLL_LANGUAGE)?.let { code -> com.sidekick.core.SpeechLanguage.entries.firstOrNull { it.code == code && it != com.sidekick.core.SpeechLanguage.AUTO } }
        if (!enrollment && !recovering) app.assistant.rememberUserStart()
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Listening and transcription", NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, notification(true))
            CaptureStatusAlerts.clear(this)
            mutableState.value = ListeningState(processing = true, enrolling = enrollment, note = "Preparing local speech detection…")
            capture = scope.launch { runSession() }
        } catch (_: Exception) {
            CaptureStatusAlerts.stopped(this)
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
            .apply { if (recording && !enrollment) addAction(Notification.Action.Builder(null, "Announce",
                PendingIntent.getService(this@ListeningService, 4, Intent(this@ListeningService, ListeningService::class.java).setAction(ANNOUNCE), PendingIntent.FLAG_IMMUTABLE)).build()) }
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
        // Auto-detect has no single language, so setup records the profile the user picked (English by default).
        val profileTarget = if (setupLanguage == com.sidekick.core.SpeechLanguage.AUTO) enrollmentTarget ?: com.sidekick.core.SpeechLanguage.ENGLISH else setupLanguage
        val speaker = SpeakerEngine(this, app.voiceProfile)
        // Until the location lookup answers, the strictest rule applies.
        val consent = com.sidekick.core.ConsentGate(com.sidekick.core.RecordingJurisdiction.unknown("Checking location…"))
        if (!enrollment) gate = consent
        val turns = com.sidekick.app.speaker.SpeakerTurnEngine(this, app.voiceProfile, speaker, { app.guidance.diarization.value }) { consent.place.groupsOtherVoices }
        val enrollmentSamples = mutableListOf<FloatArray>()
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SideKick:LiveNotes")
        wake.setReferenceCounted(false)
        try {
            check(com.sidekick.app.speech.ModelPacks.missing(this, setupLanguage, app.guidance.speechQuality.value).isEmpty()) { "Install the selected speech model pack in Get started, or choose English Fast." }
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
                if (!enrollment) launch {
                    refreshConsent()
                    while (isActive) { // offline satellite fix + bundled borders; re-checked every 10 minutes
                        consent.moveTo(JurisdictionLocator.locate(this@ListeningService))
                        refreshConsent()
                        delay(10 * 60_000L)
                    }
                }
                var identified = Speaker.UNKNOWN
                var voiceTrack = "spk_session_unassigned0"
                var voiceLabel = "Unassigned voice"
                var continuation = false
                var changedVoice = true
                val pipeline = LiveSpeechProcessor(app.speech, onBoundary = { continuation = it && !changedVoice }, contextKey = { if (identified == Speaker.USER) voiceTrack else null }, voiceKey = { voiceTrack },
                    partition = { samples ->
                        val parts = if (enrollment) listOf(com.sidekick.core.SpeakerPart(0, samples.size, null, false)) else turns.partition(samples)
                        ensureActive()
                        mutableState.update { it.copy(diarizationMs = turns.lastSeparationMs, speakerParts = parts.size) }
                        parts
                    }, onPart = { samples, part ->
                        if (!enrollment) {
                            val attributed = turns.classify(samples, part)
                            changedVoice = voiceTrack != attributed.key || identified != attributed.speaker
                            identified = attributed.speaker; voiceTrack = attributed.key; voiceLabel = attributed.label
                            if (identified == Speaker.USER) { consent.ownerSpoke(SystemClock.elapsedRealtime()); refreshConsent() }
                            mutableState.update { it.copy(userVoiceChunks = it.userVoiceChunks + if (identified == Speaker.USER) 1 else 0,
                                voiceDiagnostics = (it.voiceDiagnostics + attributed.diagnostic).takeLast(20),
                                otherVoiceChunks = it.otherVoiceChunks + if (identified == Speaker.OTHER) 1 else 0,
                                unknownVoiceChunks = it.unknownVoiceChunks + if (identified == Speaker.UNKNOWN) 1 else 0,
                                voiceStatus = when (identified) {
                                    Speaker.USER -> "Likely your voice · $voiceLabel"
                                    Speaker.OTHER -> "$voiceLabel · information only"
                                    Speaker.UNKNOWN -> "$voiceLabel · identity unassigned"
                                }) }
                            // Speech the consent rule does not allow is dropped before recognition: never transcribed.
                            val kept = consent.keep(identified, SystemClock.elapsedRealtime())
                            // For phone tests (scripts/phone-tests): voice scores and the consent decision only; never words or location.
                            android.util.Log.i("SidekickVoice", "speaker=$identified kept=$kept rule=${consent.place.rule} notice=${consent.noticeAtMs != null} · ${attributed.diagnostic}")
                            if (!kept) {
                                mutableState.update { it.copy(droppedChunks = it.droppedChunks + 1) }
                                return@LiveSpeechProcessor false
                            }
                        }
                        true
                    }, inspect = { samples ->
                    if (enrollment) {
                        if (!discard && enrollmentSamples.size < 3 && samples.size < 48_000) {
                            mutableState.update { it.copy(note = String.format(java.util.Locale.US, "That phrase was only %.1f s. Read a full sentence of at least four seconds, then pause (%d/3 kept).", samples.size / 16000.0, enrollmentSamples.size)) }
                        } else if (!discard && enrollmentSamples.size < 3) {
                            try {
                                enrollmentSamples += speaker.enrollmentEmbedding(samples)
                                mutableState.update { it.copy(voiceSamples = enrollmentSamples.size, note = "Voice setup: ${enrollmentSamples.size}/3 checked samples") }
                                if (enrollmentSamples.size == 3) finish = true
                            } catch (e: IllegalArgumentException) {
                                mutableState.update { it.copy(note = "${e.message} Try another sentence (${enrollmentSamples.size}/3 kept).") }
                            }
                        }
                        false
                    } else {
                        true
                    }
                }) { text, startSample, latency ->
                    ensureActive()
                    if (!discard) {
                        app.risk.inspect(app.temporary.text.value.takeLast(1000) + " " + text)
                        app.temporary.appendAttributedLive(text, started.plusMillis(startSample * 1000 / 16000), zone, identified, continuation, voiceTrack, voiceLabel, recognitionReliable = !app.speech.lastUncertain, timeChoices = app.speech.lastTimeChoices)
                        app.automation.process()
                    }
                    mutableState.update { it.copy(chunks = it.chunks + 1, lastLatencyMs = latency,
                        recognizer = app.speech.lastBackend,
                        recognitionDiagnostics = (it.recognitionDiagnostics + listOfNotNull(app.speech.lastClockDiagnostic, app.speech.lastRetryDiagnostic)).takeLast(8),
                        note = if (it.recording) { if (app.speech.lastUncertain) "Listening · unclear phrase kept as text only" else if (app.guidance.handsOffMode.value) "Listening · saving events automatically" else "Listening · live text is ready for review" } else "Finishing remaining speech…") }
                }
                processor = pipeline
                val worker = launch { pipeline.consume() }
                if (!finish) audio.startRecording()
                check(finish || audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start." }
                mutableState.update { it.copy(recording = !finish, processing = false, note = "Listening · waiting for speech") }
                var filled = 0
                val captureHealth = com.sidekick.core.CaptureHealth(SystemClock.elapsedRealtime())
                val audioManager = getSystemService(AudioManager::class.java)
                var privacyCheckAt = 0L
                var renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                while (!finish) {
                    ensureActive()
                    if (enrollment && SystemClock.elapsedRealtime() - startedClock >= 45_000) { finish = true; break }
                    if (SystemClock.elapsedRealtime() >= renewAt) {
                        wake.acquire(10 * 60_000L)
                        renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                    }
                    if (SystemClock.elapsedRealtime() >= privacyCheckAt) {
                        privacyCheckAt = SystemClock.elapsedRealtime() + 250
                        check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone access was removed. Listening stopped." }
                        check(!audioManager.isMicrophoneMute && (Build.VERSION.SDK_INT < 29 || audio.activeRecordingConfiguration?.isClientSilenced != true)) {
                            "Android muted the microphone. Listening stopped; check other audio apps and microphone privacy settings."
                        }
                    }
                    val read = audio.read(raw, filled, raw.size - filled, AudioRecord.READ_NON_BLOCKING)
                    captureHealth.check(read, SystemClock.elapsedRealtime())
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
                app.voiceProfile.save(enrollmentSamples, profileTarget)
            }
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (enrollment) {
                    if (enrollmentSamples.size == 3) "Voice setup complete. Your ${profileTarget.label} voice profile is saved."
                    else "Voice setup incomplete. Speak in longer phrases and try again; any previous profile is unchanged."
                } else if (it.chunks == 0) "No speech detected. Try speaking closer to the microphone." else if (app.guidance.handsOffMode.value) app.automation.status.value else "Session finished. Review and approve commitments below.") }
        } catch (e: CancellationException) {
            if (!discard && !finish) CaptureStatusAlerts.stopped(this)
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (discard) "Session discarded" else "Listening interrupted. Review text captured so far.") }
            throw e
        } catch (e: Exception) {
            CaptureStatusAlerts.stopped(this)
            if (!enrollment) app.assistant.disarm("Listening paused after an error. Start again when ready.")
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = e.message ?: "Listening failed. Review the text captured so far, then try again.") }
        } finally {
            runCatching { recorder?.stop() }; recorder?.release()
            processor?.close(); raw.fill(0); turns.close(); speaker.close(); enrollmentSamples.forEach { it.fill(0f) }
            NativeVad.close()
            if (wake.isHeld) wake.release()
            if (discard && !enrollment) app.temporary.discardSession()
            stopSelf()
        }
    }

    override fun onTimeout(startId: Int) { CaptureStatusAlerts.stopped(this); app.assistant.disarm("Android stopped listening"); capture?.cancel(); stopSelf() }
    override fun onDestroy() { noticeVoice?.shutdown(); noticeVoice = null; gate = null; scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        private const val CHANNEL = "listening"
        const val FINISH = "com.sidekick.FINISH"
        const val CANCEL = "com.sidekick.CANCEL"
        const val ENROLL = "com.sidekick.ENROLL"
        const val ENROLL_LANGUAGE = "com.sidekick.ENROLL_LANGUAGE"
        const val ANNOUNCE = "com.sidekick.ANNOUNCE"
        const val NOTICE_GIVEN = "com.sidekick.NOTICE_GIVEN"
        const val NOTICE = "Just so you know, SideKick is taking notes on this conversation."
        const val RESUME = "com.sidekick.RESUME_ASSISTANT"
        private val mutableState = MutableStateFlow(ListeningState())
        val state = mutableState.asStateFlow()
    }
}
