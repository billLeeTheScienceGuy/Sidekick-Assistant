package com.sidekick.app.audio

import com.sidekick.app.R
import com.sidekick.app.tr

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
    val voiceStatus: String = tr(R.string.speaker_unassigned),
    val userVoiceChunks: Int = 0, val otherVoiceChunks: Int = 0, val unknownVoiceChunks: Int = 0,
    val voiceDiagnostics: List<String> = emptyList(),
    val recognitionDiagnostics: List<String> = emptyList(),
    val diarizationMs: Long = 0, val speakerParts: Int = 0,
    val recognizer: String? = null,
    val note: String = tr(R.string.microphone_is_off),
    /** Which consent rule applies and what it is doing right now (see RecordingConsent in core). */
    val consent: String = "",
    /** One line for the home screen: the rule where you are, set once listening has checked the location. */
    val rulesLabel: String = "",
    /** True while other people's words are being dropped because notice has not been given. */
    val noticeNeeded: Boolean = false,
    /** Phrases from other voices discarded on arrival this session. */
    val droppedChunks: Int = 0,
    /** Label of the owner's attestation button (TX/WA: "I told everyone (incl. voice analysis)"). */
    val attestLabel: String = tr(R.string.i_told_everyone_incl_voice_analysis),
    /** A grouped voice may be named for this session right now (ConsentGate.namingAllowed). */
    val namingAllowed: Boolean = false,
    /** A notice is currently in effect (for "Announce again"). */
    val noticeActive: Boolean = false,
    /** A phone call is active: strictest rule for the call. */
    val callActive: Boolean = false,
    /** The latest answer to "SideKick, what's my plan today?" (shown on screen; spoken only when it won't interrupt). */
    val answer: String = "",
    /** Identifies this listening session's notice records (no audio, no voice data). */
    val sessionId: String? = null,
)

/** Explicitly enabled continuous listening. Only bounded RAM buffers hold audio. */
class ListeningService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Job? = null
    @Volatile private var finish = false
    @Volatile private var discard = false
    private var enrollment = false
    private var enrollmentTarget: com.sidekick.core.SpeechLanguage? = null
    private var enrollmentSetting = com.sidekick.app.speaker.VoiceSetting.QUIET
    /** Setup of the optional SideKick voice check: five phrases that start with "SideKick". */
    private var wakeEnrollment = false
    @Volatile private var gate: com.sidekick.core.ConsentGate? = null
    @Volatile private var turnEngine: com.sidekick.app.speaker.SpeakerTurnEngine? = null
    @Volatile private var sessionId: String? = null
    private var noticeVoice: android.speech.tts.TextToSpeech? = null
    /** While the spoken notice plays (plus a short tail), microphone input is replaced by silence: SideKick never transcribes itself. */
    @Volatile private var muteUntil = 0L
    @Volatile private var noticePlaying = false
    @Volatile private var answerPlaying = false
    private var answerVoice: android.speech.tts.TextToSpeech? = null
    /** When SideKick last started speaking (notice or answer). */
    @Volatile private var playStartedAt = 0L
    /**
     * Microphone input is muted while SideKick speaks. Safety limit: if Android's speech engine never reports that it
     * finished (90 s for the longest notice), listening is unmuted anyway, so SideKick can never stay deaf.
     */
    private fun speakingNotice(now: Long): Boolean {
        if ((noticePlaying || answerPlaying) && playStartedAt > 0 && now - playStartedAt > 90_000) {
            android.util.Log.w("SidekickListen", "speech engine never finished: microphone unmuted")
            noticePlaying = false; answerPlaying = false; playStartedAt = 0
        }
        return noticePlaying || answerPlaying || now < muteUntil
    }

    /** Reads an answer aloud; the microphone is muted meanwhile so SideKick never writes down its own voice. */
    private fun speakAnswer(text: String, language: String) {
        answerVoice?.shutdown()
        answerPlaying = true; playStartedAt = SystemClock.elapsedRealtime()
        answerVoice = android.speech.tts.TextToSpeech(this) { status ->
            val tts = answerVoice
            if (status != android.speech.tts.TextToSpeech.SUCCESS || tts == null) { answerPlaying = false; return@TextToSpeech }
            tts.language = if (language == "en") java.util.Locale.US else java.util.Locale.forLanguageTag(language)
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { answerPlaying = false; muteUntil = SystemClock.elapsedRealtime() + 700 }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { answerPlaying = false; muteUntil = SystemClock.elapsedRealtime() + 700 }
            })
            if (tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "sidekick-answer") != android.speech.tts.TextToSpeech.SUCCESS) answerPlaying = false
        }
    }

    /**
     * "SideKick, what's my plan today?" (any app language): answered from saved plans. The question must start with
     * SideKick's name, so the same words said to someone else never trigger it. Spoken for the owner's voice when no call
     * is active; an unassigned voice gets the answer on screen only; another person's voice asking is ignored.
     */
    private fun answerQuestion(question: com.sidekick.core.PlanQuestion.Question, speaker: Speaker) {
        if (speaker == Speaker.OTHER) return
        app.guidance.setAskedQuestion()
        scope.launch {
            val plans = app.database.commitments().all().filter { it.state == "ACCEPTED" || it.state == "DONE" }
                .map { com.sidekick.core.PlanQuestion.Plan(it.title, it.activityStartAt ?: it.dueAt, it.informational) }
            val text = if (question is com.sidekick.core.PlanQuestion.Scam) app.risk.answer(question.language)
                else com.sidekick.core.PlanQuestion.answer(question, plans, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
            val speak = speaker == Speaker.USER && !mutableState.value.callActive
            mutableState.update { it.copy(answer = text) }
            if (speak) mainHandler.post { speakAnswer(text, question.language) }
        }
    }
    /** Microphone frame levels (numbers only) measured while the notice plays: was it loud enough to count? */
    private val noticeLevels = java.util.concurrent.ConcurrentLinkedQueue<Float>()
    private val autoAnnouncer = com.sidekick.core.AutoAnnouncer()
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val app get() = application as SidekickApplication

    private fun refreshConsent() {
        val g = gate ?: return
        val now = SystemClock.elapsedRealtime()
        // Voice grouping is never allowed here (STRICT, or NOTICE before notice): forget every other voice at once.
        if (!g.groupingAllowed()) forgetOthers()
        mutableState.update { it.copy(consent = com.sidekick.app.CoreText.localize(g.status(now) + " · " + g.place.reason), rulesLabel = com.sidekick.app.CoreText.localize(g.homeLabel()), noticeNeeded = g.noticeNeeded(),
            attestLabel = com.sidekick.app.CoreText.localize(g.attestLabel()), namingAllowed = g.namingAllowed(now), noticeActive = g.noticeAtMs != null) }
    }

    /** SessionVoices, session names, naming context and recent anchors: memory only, cleared together. */
    private fun forgetOthers() {
        val engine = turnEngine ?: return
        if (engine.groupedVoices > 0 || app.temporary.nameSuggestion.value != null || app.temporary.speakerTurns.value.any { it.voiceKey != null }) {
            engine.forgetOthers(); app.temporary.forgetVoiceNames()
        }
    }

    /** Persist what a notice covered for this session: rules, state codes, text version, method. No audio or voice data. */
    private fun recordNotice(g: com.sidekick.core.ConsentGate, method: com.sidekick.core.NoticeMethod,
        volumePercent: Int? = null, level: Float? = null, audible: Boolean? = null) {
        val session = sessionId ?: return
        val record = g.record(System.currentTimeMillis(), method)
        scope.launch {
            runCatching {
                app.database.noticeRecords().put(com.sidekick.app.data.NoticeRecordEntity(sessionId = session, noticeAt = record.atEpochMs,
                    stateCodes = record.stateCodes.joinToString(","), recordingRule = record.recording.name, biometricRule = record.biometric.name,
                    textVersion = record.textVersion, includesVoice = record.includesVoice, method = record.method.name,
                    volumePercent = volumePercent, measuredLevel = level?.toDouble(), audible = audible))
            }
        }
    }

    /**
     * Spoken notice: the base line plus the voice-analysis line, in every state. The media volume is raised to at least
     * half for the notice and put back afterwards; the microphone measures how loud it was. A muted phone or a notice
     * that was barely picked up does not count. [auto]: played by "Announce automatically when a new conversation starts".
     */
    private fun announce(auto: Boolean = false) {
        val g = gate ?: return
        val method = if (auto) com.sidekick.core.NoticeMethod.SPOKEN_AUTO else com.sidekick.core.NoticeMethod.SPOKEN
        fun refuse(note: String) {
            autoAnnouncer.failed(); recordNotice(g, method, null, null, false)
            mutableState.update { it.copy(note = note) }
        }
        if (!speakerOutput()) {
            refuse(tr(R.string.notice_not_played_sound_would_go)); return
        }
        checkCovered { covered ->
            if (covered) refuse(tr(R.string.phone_seems_covered_or_in_a))
            else speakNotice(auto)
        }
    }

    /** Media goes to the phone's own speaker (no headphones, Bluetooth audio or USB headset connected). */
    private fun speakerOutput(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return false
        val elsewhere = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID) +
            (if (Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) else emptySet())
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in elsewhere }
    }

    /** One proximity reading (pocket, bag, face-down); no sensor or no answer within 800 ms counts as not covered. */
    private fun checkCovered(done: (Boolean) -> Unit) {
        val sensors = getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
        if (sensors == null || sensor == null) { done(false); return }
        var finished = false
        lateinit var listener: android.hardware.SensorEventListener
        val timeout = Runnable { if (!finished) { finished = true; sensors.unregisterListener(listener); done(false) } }
        listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (finished) return
                finished = true; sensors.unregisterListener(this); mainHandler.removeCallbacks(timeout)
                val covered = com.sidekick.core.NoticeAudibility.covered(event.values[0], sensor.maximumRange)
                android.util.Log.i("SidekickNotice", "proximity=${event.values[0]} max=${sensor.maximumRange} covered=$covered")
                done(covered)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        sensors.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, mainHandler)
        mainHandler.postDelayed(timeout, 800)
    }

    private fun speakNotice(auto: Boolean) {
        val g = gate ?: return
        val includesVoice = true // the voice-analysis line is spoken in every state
        val text = com.sidekick.core.ConsentNotice.text(includesVoice)
        val method = if (auto) com.sidekick.core.NoticeMethod.SPOKEN_AUTO else com.sidekick.core.NoticeMethod.SPOKEN
        val audio = getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        val original = audio.getStreamVolume(stream)
        val target = com.sidekick.core.NoticeAudibility.targetVolume(original, max)
        if (target > original) runCatching { audio.setStreamVolume(stream, target, 0) }
        val volume = audio.getStreamVolume(stream)
        fun restore() { if (volume != original) runCatching { audio.setStreamVolume(stream, original, 0) } }
        val percent = com.sidekick.core.NoticeAudibility.percent(volume, max)
        if (audio.isStreamMute(stream) || volume < target) {
            restore(); autoAnnouncer.failed()
            recordNotice(g, method, percent, null, false)
            mutableState.update { it.copy(note = tr(R.string.notice_too_quiet_raise_the_volume)) }
            return
        }
        noticeLevels.clear()
        noticeVoice?.shutdown()
        noticeVoice = android.speech.tts.TextToSpeech(this) { status ->
            val tts = noticeVoice
            if (status == android.speech.tts.TextToSpeech.SUCCESS && tts != null) {
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) { noticePlaying = true; playStartedAt = SystemClock.elapsedRealtime() }
                    override fun onDone(id: String?) {
                        noticePlaying = false; muteUntil = SystemClock.elapsedRealtime() + 700
                        restore()
                        val levels = noticeLevels.toList(); noticeLevels.clear()
                        val loudness = com.sidekick.core.NoticeAudibility.loudness(levels)
                        val audible = com.sidekick.core.NoticeAudibility.audible(levels)
                        // Numbers only: for calibrating the audibility threshold. Never words or location.
                        android.util.Log.i("SidekickNotice", "volume=$percent% level=${"%.4f".format(java.util.Locale.US, loudness)} frames=${levels.size} audible=$audible auto=$auto")
                        recordNotice(g, method, percent, loudness, audible)
                        if (audible) { g.noticeGiven(SystemClock.elapsedRealtime(), includesVoice); refreshConsent() }
                        else {
                            autoAnnouncer.failed()
                            mutableState.update { it.copy(note = tr(R.string.notice_too_quiet_to_count_uncover)) }
                        }
                    }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) {
                        noticePlaying = false; muteUntil = SystemClock.elapsedRealtime() + 700
                        restore(); autoAnnouncer.failed()
                        mutableState.update { it.copy(note = tr(R.string.couldn_t_play_the_notice_tell)) }
                    }
                })
                noticePlaying = true; playStartedAt = SystemClock.elapsedRealtime() // mute before audio starts; onDone/onError release it
                val params = android.os.Bundle().apply { putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, stream) }
                if (tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "sidekick-notice") != android.speech.tts.TextToSpeech.SUCCESS) {
                    noticePlaying = false; restore(); autoAnnouncer.failed()
                }
            } else {
                restore(); autoAnnouncer.failed()
                mutableState.update { it.copy(note = tr(R.string.no_text_to_speech_voice_tell)) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FINISH -> { app.assistant.disarm(); finish = true; if (capture == null) stopSelf(); return START_NOT_STICKY }
            CANCEL -> { app.assistant.disarm(); discard = true; capture?.cancel(); if (capture == null) stopSelf(); return START_NOT_STICKY }
            ANNOUNCE -> { announce(); return START_NOT_STICKY }
            NOTICE_GIVEN -> {
                // "I told everyone": in TX/WA the button reads "(incl. voice analysis)" and covers both notices.
                gate?.let { g -> g.noticeGiven(SystemClock.elapsedRealtime()); recordNotice(g, com.sidekick.core.NoticeMethod.ATTESTED) }
                refreshConsent(); return START_NOT_STICKY
            }
            NAME_VOICE -> {
                val g = gate; val engine = turnEngine
                val key = intent.getStringExtra(VOICE_KEY); val name = intent.getStringExtra(VOICE_NAME)
                if (g != null && engine != null && key != null && g.namingAllowed(SystemClock.elapsedRealtime()) && engine.nameVoice(key, name)) {
                    app.temporary.voiceNamed(key, name?.let(com.sidekick.core.SessionVoices::cleanName), tr(R.string.voice))
                } else mutableState.update { it.copy(note = tr(R.string.voices_can_t_be_named_right)) }
                return START_NOT_STICKY
            }
        }
        val recovering = intent == null || intent.action == RESUME
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ListeningState(note = tr(R.string.microphone_permission_is_required))
            app.assistant.disarm(tr(R.string.microphone_permission_is_required))
            if (recovering) CaptureStatusAlerts.stopped(this)
            stopSelf(); return START_NOT_STICKY
        }
        if (recovering && !app.assistant.canRecover()) { stopSelf(); return START_NOT_STICKY }
        if (capture != null) return if (app.assistant.armed && !enrollment) START_STICKY else START_NOT_STICKY
        finish = false; discard = false
        enrollment = intent?.getBooleanExtra(ENROLL, false) == true
        enrollmentTarget = intent?.getStringExtra(ENROLL_LANGUAGE)?.let { code -> com.sidekick.core.SpeechLanguage.entries.firstOrNull { it.code == code && it != com.sidekick.core.SpeechLanguage.AUTO } }
        enrollmentSetting = com.sidekick.app.speaker.VoiceSetting.entries.firstOrNull { it.name == intent?.getStringExtra(ENROLL_SETTING) } ?: com.sidekick.app.speaker.VoiceSetting.QUIET
        wakeEnrollment = enrollment && intent?.getBooleanExtra(ENROLL_WAKE, false) == true
        if (!enrollment && !recovering) app.assistant.rememberUserStart()
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, tr(R.string.listening_and_transcription), NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, notification(true))
            CaptureStatusAlerts.clear(this)
            mutableState.value = ListeningState(processing = true, enrolling = enrollment, note = tr(R.string.preparing_local_speech_detection))
            capture = scope.launch {
                // A recoverable error restarts listening 5 s later inside the same service (it keeps its microphone
                // permission while in the foreground), up to 5 times an hour; otherwise the session ends here.
                try {
                    while (runSession() && !finish && !discard) {
                        mutableState.update { it.copy(processing = true, note = tr(R.string.restarting_listening_after_a_problem)) }
                        delay(retry.delayMs)
                        if (finish || discard) break // Finish or Stop pressed while waiting
                    }
                } finally { stopSelf() }
            }
        } catch (e: Exception) {
            android.util.Log.w("SidekickListen", "could not start${if (recovering) " (resume)" else ""}: ${e.javaClass.simpleName}")
            if (recovering) {
                // Android refused the microphone for a resume started in the background (right after an update or a
                // restart, before SideKick's assistant service is connected again). Recovery stays enabled: that
                // connection tries again and is allowed.
                mutableState.value = ListeningState(note = tr(R.string.waiting_to_resume_listening))
            } else {
                CaptureStatusAlerts.stopped(this)
                app.assistant.disarm("Android could not start listening. Open SideKick and start again.")
                mutableState.value = ListeningState(note = "Android could not start listening. Open the app and try again.")
            }
            stopSelf()
        }
        return if (!enrollment && app.assistant.armed) START_STICKY else START_NOT_STICKY
    }

    /** Shows a pause reason in the listening notification (null: back to normal). */
    private fun pausedNotification(status: String?) =
        runCatching { getSystemService(NotificationManager::class.java).notify(1, notification(true, status)) }

    private fun notification(recording: Boolean, paused: String? = null): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getService(this, 3, Intent(this, ListeningService::class.java).setAction(FINISH), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (paused != null) tr(R.string.sidekick_paused) else if (recording) tr(R.string.sidekick_listening) else tr(R.string.sidekick_finishing_transcript))
            .setContentText(paused ?: if (app.guidance.handsOffMode.value && !enrollment) tr(R.string.on_device_plans_save_automatically) else tr(R.string.on_device_no_audio_saved))
            .setContentIntent(open).setOngoing(true)
            .apply { if (recording) addAction(Notification.Action.Builder(null, tr(R.string.finish), done).build()) }
            .apply { if (recording && !enrollment) addAction(Notification.Action.Builder(null, tr(R.string.announce),
                PendingIntent.getService(this@ListeningService, 4, Intent(this@ListeningService, ListeningService::class.java).setAction(ANNOUNCE), PendingIntent.FLAG_IMMUTABLE)).build()) }
            .addAction(Notification.Action.Builder(null, if (app.guidance.handsOffMode.value && !enrollment) tr(R.string.stop_clear_text) else tr(R.string.discard_session), cancel).build()).build()
    }

    private val retry = com.sidekick.core.ListeningRetry()

    /** Runs one listening session. Returns true when it ended with an error that should be retried. */
    @Suppress("MissingPermission")
    private suspend fun runSession(): Boolean {
        var retryWanted = false
        var recorder: AudioRecord? = null
        var processor: LiveSpeechProcessor? = null
        val raw = ShortArray(16_384) // up to 1 s per read; fed to the detector in quarter-second slices
        val setupLanguage = app.guidance.speechLanguage.value
        app.voiceProfile.selectLanguage(setupLanguage)
        val setupModel = app.voiceProfile.model.value
        // Auto-detect has no single language, so setup records the profile the user picked (English by default).
        val profileTarget = if (setupLanguage == com.sidekick.core.SpeechLanguage.AUTO) enrollmentTarget ?: com.sidekick.core.SpeechLanguage.ENGLISH else setupLanguage
        // Until the location lookup answers, the strictest rule applies.
        val strictest = app.guidance.strictestEverywhere.value
        val consent = com.sidekick.core.ConsentGate(
            if (strictest) com.sidekick.core.RecordingJurisdiction.strictestEverywhere() else com.sidekick.core.RecordingJurisdiction.unknown(tr(R.string.checking_location)),
            voiceNoticeEverywhere = app.guidance.voiceLineEverywhere.value)
        val speaker = SpeakerEngine(this, app.voiceProfile) {
            if (consent.strictOwnerMatch()) com.sidekick.core.VoiceMatch.Thresholds.BEFORE_NOTICE else com.sidekick.core.VoiceMatch.Thresholds.STANDARD
        }
        val turns = com.sidekick.app.speaker.SpeakerTurnEngine(this, app.voiceProfile, speaker, { app.guidance.diarization.value }) { consent.groupingAllowed() }
        if (!enrollment) {
            gate = consent; turnEngine = turns
            sessionId = java.util.UUID.randomUUID().toString()
            mutableState.update { it.copy(sessionId = sessionId) }
            if (app.voiceProfile.ready.value) app.voiceProfile.markUsed()
        }
        val enrollmentSamples = mutableListOf<FloatArray>()
        val wakeSamples = mutableListOf<FloatArray>()
        var wakeTries = 0
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SideKick:LiveNotes")
        wake.setReferenceCounted(false)
        try {
            check(com.sidekick.app.speech.ModelPacks.missing(this, setupLanguage, app.guidance.speechQuality.value).isEmpty()) { tr(R.string.install_the_selected_speech_model_pack) }
            wake.acquire(10 * 60_000L)
            NativeVad.open(VadModel.install(this))
            currentCoroutineContext().ensureActive()
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { tr(R.string.this_microphone_configuration_is_unavailable) }
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 256_000)) // 8 s of buffer: no audio lost if processing is briefly delayed
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { tr(R.string.microphone_could_not_initialize) }
            val started = Instant.now()
            val zone = ZoneId.systemDefault()
            val startedClock = SystemClock.elapsedRealtime()
            if (!enrollment) app.temporary.beginSession()
            coroutineScope {
                // The latest located place; a phone call overrides it with the strictest rule until the call ends.
                var located = consent.place
                var inCall = false
                // The location and call watchers run on different threads: one lock keeps "in a call" and "where" consistent.
                val placeLock = Any()
                // Location, settings and call watchers end with the session (they would otherwise keep this scope open).
                val watchers = mutableListOf<Job>()
                var follower: Job? = null
                fun applyPlace(place: com.sidekick.core.RecordingJurisdiction.Place) {
                    synchronized(placeLock) {
                        located = place
                        if (!inCall) consent.moveTo(place)
                    }
                    refreshConsent()
                }
                if (!enrollment) {
                    refreshConsent()
                    // "Strictest rules everywhere": no location requests at all.
                    if (!strictest) follower = launch {
                        try { JurisdictionLocator.follow(this@ListeningService) { place -> applyPlace(place) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { applyPlace(com.sidekick.core.RecordingJurisdiction.unknown(tr(R.string.location_lookup_failed))) }
                    }
                    follower?.let(watchers::add)
                    watchers += launch { app.guidance.autoAnnounce.collect { autoAnnouncer.enabled = it } }
                    watchers += launch { app.guidance.voiceLineEverywhere.collect { consent.voiceNoticeEverywhere = it; refreshConsent() } }
                    watchers += launch { app.guidance.strictestEverywhere.collect { on ->
                        // Turned on mid-session: stop all location requests and apply the strictest rule.
                        if (on) { follower?.cancel(); applyPlace(com.sidekick.core.RecordingJurisdiction.strictestEverywhere()) }
                    } }
                    watchers += launch {
                        // Phone calls: the other person may be in another state.
                        val manager = getSystemService(AudioManager::class.java)
                        fun checkCall() {
                            val changed = synchronized(placeLock) {
                                val calling = manager.mode == AudioManager.MODE_IN_CALL || manager.mode == AudioManager.MODE_IN_COMMUNICATION
                                if (calling == inCall) null else {
                                    inCall = calling
                                    if (calling) consent.callStarted() else consent.moveTo(located)
                                    calling
                                }
                            }
                            if (changed != null) {
                                mutableState.update { it.copy(callActive = changed) }; refreshConsent()
                                if (changed) app.risk.callStarted() else app.risk.callEnded()
                            }
                        }
                        // Android 12+: told the moment a call starts or ends, instead of asking every second. The helper
                        // thread is shut down with the session.
                        val callEvents = if (Build.VERSION.SDK_INT >= 31) java.util.concurrent.Executors.newSingleThreadExecutor() else null
                        val modeListener = if (Build.VERSION.SDK_INT >= 31 && callEvents != null) AudioManager.OnModeChangedListener { checkCall() }.also {
                            runCatching { manager.addOnModeChangedListener(callEvents, it) }
                        } else null
                        try {
                            while (isActive) {
                                checkCall()
                                // A notice covers one conversation: 30 min without other voices, or 2 hours at most.
                                val nowMs = SystemClock.elapsedRealtime()
                                consent.expireIfStale(nowMs)?.let { reason ->
                                    refreshConsent()
                                    // 2-hour cap in an ongoing meeting: renew right away if the setting is on.
                                    if (autoAnnouncer.expired(reason, nowMs, consent.othersLastMs)) mainHandler.post { announce(auto = true) }
                                }
                                // With call events, only the notice timer needs this loop: every 30 s (minutes-scale limits).
                                delay(if (modeListener != null) 30_000 else 1_000)
                            }
                        } finally {
                            if (modeListener != null && Build.VERSION.SDK_INT >= 31) runCatching { manager.removeOnModeChangedListener(modeListener) }
                            callEvents?.shutdown()
                        }
                    }
                }
                var identified = Speaker.UNKNOWN
                // Optional SideKick voice check: how the start of an unassigned phrase matches the owner's "SideKick".
                var wakeScore: Float? = null
                var voiceTrack = "spk_session_unassigned0"
                var voiceLabel = tr(R.string.unassigned_voice)
                var voiceName: String? = null
                var voiceGrouped = false
                var continuation = false
                var changedVoice = true
                // Idea 4: text of a chunk held back until the next chunk decides whether it was the owner (memory only).
                class HeldText(val text: String, val at: Instant, val continuation: Boolean, val reliable: Boolean, val choices: List<String>)
                var holdNext = false
                var heldText: HeldText? = null
                fun releaseHeld(owner: Boolean, track: String, label: String) {
                    val h = heldText ?: return
                    heldText = null
                    if (discard) return
                    app.temporary.appendAttributedLive(h.text, h.at, zone, if (owner) Speaker.USER else Speaker.UNKNOWN, h.continuation,
                        if (owner) track else "spk_session_unassigned_held", if (owner) label else tr(R.string.unassigned_voice), recognitionReliable = h.reliable, timeChoices = h.choices)
                    if (owner) mutableState.update { it.copy(userVoiceChunks = it.userVoiceChunks + 1, unknownVoiceChunks = (it.unknownVoiceChunks - 1).coerceAtLeast(0)) }
                    // Saved plans are processed with the next chunk's text (or at session end).
                }
                val activityManager = getSystemService(android.app.ActivityManager::class.java)
                val pipeline = LiveSpeechProcessor(app.speech,
                    memory = { android.app.ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.let { it.availMem to it.lowMemory } },
                    thermal = { if (Build.VERSION.SDK_INT >= 29) getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0 },
                    fasterAvailable = { app.speech.fasterAvailable() },
                    onBoundary ={ continuation = it && !changedVoice }, contextKey = { if (identified == Speaker.USER) voiceTrack else null }, voiceKey = { voiceTrack },
                    partition = { samples ->
                        val parts = if (enrollment) listOf(com.sidekick.core.SpeakerPart(0, samples.size, null, false)) else turns.partition(samples)
                        ensureActive()
                        mutableState.update { it.copy(diarizationMs = turns.lastSeparationMs, speakerParts = parts.size) }
                        parts
                    }, onPart = { samples, part ->
                        if (!enrollment) {
                            val attributed = turns.classify(samples, part)
                            attributed.heldWasOwner?.let { releaseHeld(it, attributed.key, attributed.display) }
                            holdNext = attributed.held
                            changedVoice = voiceTrack != attributed.key || identified != attributed.speaker
                            identified = attributed.speaker; voiceTrack = attributed.key; voiceLabel = attributed.display
                            // Questions are short: only unassigned phrases up to 8 s, and only with the check on.
                            wakeScore = if (identified == Speaker.UNKNOWN && samples.size <= 128_000) runCatching { speaker.wakeScore(samples) }.getOrNull() else null
                            voiceName = attributed.name; voiceGrouped = attributed.grouped
                            if (identified == Speaker.USER) { consent.ownerSpoke(SystemClock.elapsedRealtime()); refreshConsent() }
                            mutableState.update { it.copy(userVoiceChunks = it.userVoiceChunks + if (identified == Speaker.USER) 1 else 0,
                                voiceDiagnostics = (it.voiceDiagnostics + attributed.diagnostic).takeLast(20),
                                otherVoiceChunks = it.otherVoiceChunks + if (identified == Speaker.OTHER) 1 else 0,
                                unknownVoiceChunks = it.unknownVoiceChunks + if (identified == Speaker.UNKNOWN) 1 else 0,
                                voiceStatus = when (identified) {
                                    Speaker.USER -> tr(R.string.likely_your_voice_1_s, voiceLabel)
                                    Speaker.OTHER -> tr(R.string.n_1_s_information_only, voiceLabel)
                                    Speaker.UNKNOWN -> tr(R.string.n_1_s_identity_unassigned, voiceLabel)
                                }) }
                            // Speech the consent rule does not allow is dropped before recognition: never transcribed.
                            // New conversation and no notice in effect: with the setting on, announce now and drop this
                            // phrase, which was said before the notice.
                            // Only a voice classified as someone else: the owner's own short phrases are often "unassigned".
                            val autoNow = identified == Speaker.OTHER && !speakingNotice(SystemClock.elapsedRealtime()) &&
                                autoAnnouncer.otherVoice(SystemClock.elapsedRealtime(), consent.noticeAtMs != null)
                            if (autoNow) mainHandler.post { announce(auto = true) }
                            val kept = !autoNow && consent.keep(identified, SystemClock.elapsedRealtime())
                            // For phone tests and scripts/phone-tests/voice_tuning_report.py: voice scores and the consent decision only;
                            // never words, location, voice labels or names.
                            android.util.Log.i("SidekickVoice", "speaker=$identified kept=$kept rule=${consent.place.recording} biometric=${consent.place.biometric} " +
                                "notice=${consent.noticeAtMs != null} grouping=${consent.groupingAllowed()} call=${mutableState.value.callActive} · ${attributed.scores}" + (wakeScore?.let { String.format(java.util.Locale.US, " · sidekick %.2f", it) } ?: ""))
                            if (!kept) {
                                mutableState.update { it.copy(droppedChunks = it.droppedChunks + 1) }
                                return@LiveSpeechProcessor false
                            }
                        }
                        true
                    }, inspect = { samples ->
                    if (wakeEnrollment) {
                        val total = com.sidekick.core.WakeVoice.SAMPLES
                        if (!discard && wakeSamples.size < total && wakeTries < com.sidekick.core.WakeVoice.MAX_TRIES) {
                            val vector = runCatching { speaker.wakeEmbedding(samples) }.getOrNull()
                            if (vector == null) mutableState.update { it.copy(note = tr(R.string.didn_t_catch_sidekick_say_it, wakeSamples.size, total)) }
                            else {
                                wakeTries++
                                wakeSamples += vector
                                // One sample unlike the others is dropped; the good ones are kept.
                                val odd = com.sidekick.core.WakeVoice.outlier(wakeSamples)
                                if (odd != null) wakeSamples.removeAt(odd).fill(0f)
                                when {
                                    wakeSamples.size == total -> finish = true
                                    wakeTries >= com.sidekick.core.WakeVoice.MAX_TRIES -> finish = true
                                    else -> mutableState.update { it.copy(voiceSamples = wakeSamples.size, note =
                                        (if (odd != null) tr(R.string.one_sounded_different_from_the_others) else tr(R.string.got_it)) +
                                        tr(R.string.now_say_the_next_one_1, wakeSamples.size, total)) }
                                }
                            }
                        }
                        false
                    } else if (enrollment) {
                        if (!discard && enrollmentSamples.size < 3 && samples.size < 48_000) {
                            mutableState.update { it.copy(note = String.format(java.util.Locale.US, "That phrase was only %.1f s. Read a full sentence of at least four seconds, then pause (%d/3 kept).", samples.size / 16000.0, enrollmentSamples.size)) }
                        } else if (!discard && enrollmentSamples.size < 3) {
                            try {
                                enrollmentSamples += speaker.enrollmentEmbedding(samples)
                                mutableState.update { it.copy(voiceSamples = enrollmentSamples.size, note = tr(R.string.voice_setup_1_s_3_checked, enrollmentSamples.size)) }
                                if (enrollmentSamples.size == 3) finish = true
                            } catch (e: IllegalArgumentException) {
                                mutableState.update { it.copy(note = tr(R.string.n_1_s_try_another_sentence_2, e.message, enrollmentSamples.size)) }
                            }
                        }
                        false
                    } else {
                        true
                    }
                }) { text, startSample, latency ->
                    ensureActive()
                    // Understood languages follow the language setting: one language, or Auto-detect's chosen languages.
                    val questionLanguages = app.guidance.speechLanguage.value.let { l ->
                        if (l == com.sidekick.core.SpeechLanguage.AUTO) app.guidance.detectLanguages.value.map { it.code }.toSet() else setOf(l.code) }
                    val question = if (!discard && !enrollment) com.sidekick.core.PlanQuestion.parse(text, questionLanguages) else null
                    if (question != null) {
                        // A question to SideKick is answered, not saved as conversation text or a plan.
                        answerQuestion(question, com.sidekick.core.WakeVoice.asker(identified, wakeScore))
                    } else if (!discard && holdNext && text.isNotBlank()) {
                        app.risk.inspect(app.temporary.text.value.takeLast(1000) + " " + text)
                        heldText = HeldText(text, started.plusMillis(startSample * 1000 / 16000), continuation, !app.speech.lastUncertain, app.speech.lastTimeChoices)
                    } else if (!discard) {
                        app.risk.inspect(app.temporary.text.value.takeLast(1000) + " " + text)
                        app.temporary.appendAttributedLive(text, started.plusMillis(startSample * 1000 / 16000), zone, identified, continuation, voiceTrack, voiceLabel, recognitionReliable = !app.speech.lastUncertain, timeChoices = app.speech.lastTimeChoices,
                            speakerName = voiceName, grouped = voiceGrouped && consent.groupingAllowed())
                        app.automation.process()
                    }
                    mutableState.update { it.copy(chunks = it.chunks + 1, lastLatencyMs = latency,
                        recognizer = app.speech.lastBackend,
                        recognitionDiagnostics = (it.recognitionDiagnostics + listOfNotNull(app.speech.lastClockDiagnostic, app.speech.lastRetryDiagnostic)).takeLast(8),
                        note = if (it.recording) { if (app.speech.lastUncertain) tr(R.string.listening_unclear_phrase_kept_as_text) else if (app.guidance.handsOffMode.value) tr(R.string.listening_saving_events_automatically) else tr(R.string.listening_live_text_is_ready_for) } else tr(R.string.finishing_remaining_speech)) }
                }
                processor = pipeline
                val worker = launch { pipeline.consume() }
                if (!finish) audio.startRecording()
                check(finish || audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { tr(R.string.microphone_did_not_start) }
                mutableState.update { it.copy(recording = !finish, processing = false, note = tr(R.string.listening_waiting_for_speech)) }
                var filled = 0
                var reportedDrops = 0
                var target = 4096
                var blockStart = 0L
                var blockMuted = false
                var lastLevel = 0f
                /** Feeds raw[0 until count] to the detector in quarter-second slices; true if any slice had speech. */
                fun feed(count: Int, mute: Boolean): Boolean {
                    var any = false
                    var at = 0
                    while (at < count) {
                        val n = minOf(4096, count - at)
                        val samples = FloatArray(n) { raw[at + it] / 32768f }
                        val level = sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat()
                        lastLevel = level
                        if (noticePlaying) noticeLevels.add(level)
                        if (mute) samples.fill(0f)
                        try { any = pipeline.accept(samples) || any } finally { samples.fill(0f) }
                        at += n
                    }
                    raw.fill(0, 0, count)
                    return any
                }
                var captureHealth = com.sidekick.core.CaptureHealth(SystemClock.elapsedRealtime())
                val audioManager = getSystemService(AudioManager::class.java)
                val power = getSystemService(android.os.PowerManager::class.java)
                var privacyCheckAt = 0L
                val handback = com.sidekick.core.MicHandback()
                var mutedPause = false
                var renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                // Optional "pause when resting": screen on, unlock or carrying motion count as activity and resume.
                // Test switches (phone tests only, never shown): the old capture loop for battery comparisons, and a
                // short resting window so the pause can be tested in minutes.
                // Only the test copies (.benchmark, .compacttest) read them; the normal app always uses the defaults.
                val testBuild = packageName.endsWith(".benchmark") || packageName.endsWith(".compacttest")
                val switches = getSharedPreferences("test-switches", MODE_PRIVATE)
                val legacy = testBuild && switches.getBoolean("legacy_capture", false)
                pipeline.quietGate = !legacy; pipeline.batchVad = !legacy
                pipeline.lowPriority = !legacy && (!testBuild || switches.getBoolean("background_priority", true))
                val rest = com.sidekick.core.RestingPause(SystemClock.elapsedRealtime(), if (testBuild) switches.getLong("rest_quiet_ms", 30 * 60_000L) else 30 * 60_000L)
                val woke = java.util.concurrent.atomic.AtomicBoolean(false)
                val wakeReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: android.content.Context?, i: Intent?) { woke.set(true) }
                }
                registerReceiver(wakeReceiver, android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) })
                val sensors = getSystemService(android.hardware.SensorManager::class.java)
                val carry = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION)
                val carried = object : android.hardware.TriggerEventListener() {
                    override fun onTrigger(event: android.hardware.TriggerEvent?) { woke.set(true); carry?.let { sensors.requestTriggerSensor(this, it) } }
                }
                if (!enrollment && carry != null) sensors.requestTriggerSensor(carried, carry)
                try {
                while (!finish) {
                    ensureActive()
                    val nowMs = SystemClock.elapsedRealtime()
                    if (woke.getAndSet(false) || power.isInteractive) rest.activity(nowMs)
                    if (!enrollment && rest.shouldPause(nowMs, app.guidance.pauseWhenResting.value, mutableState.value.callActive)) {
                        // Microphone fully off and the processor free to sleep until the phone is used or carried.
                        if (filled > 0) { feed(filled, speakingNotice(SystemClock.elapsedRealtime()) || muteUntil > blockStart); filled = 0 }
                        pipeline.flushPending(); audio.stop(); rest.pause()
                        if (wake.isHeld) wake.release()
                        mutableState.update { it.copy(note = tr(R.string.paused_phone_resting_pick_it_up), level = 0f, speechActive = false) }
                        pausedNotification(tr(R.string.the_phone_is_resting_and_it))
                        while (!finish && !woke.get() && !power.isInteractive && app.guidance.pauseWhenResting.value) delay(1_000)
                        woke.set(false)
                        if (finish) break
                        wake.acquire(10 * 60_000L); renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                        audio.startRecording(); captureHealth = com.sidekick.core.CaptureHealth(SystemClock.elapsedRealtime())
                        rest.resume(SystemClock.elapsedRealtime())
                        check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { tr(R.string.microphone_did_not_restart_after_the) }
                        mutableState.update { it.copy(note = tr(R.string.listening_resumed)) }; pausedNotification(null)
                        continue
                    }
                    if (enrollment && SystemClock.elapsedRealtime() - startedClock >= if (wakeEnrollment) 120_000 else 45_000) { finish = true; break }
                    if (SystemClock.elapsedRealtime() >= renewAt) {
                        wake.acquire(10 * 60_000L)
                        renewAt = SystemClock.elapsedRealtime() + 5 * 60_000L
                    }
                    if (SystemClock.elapsedRealtime() >= privacyCheckAt) {
                        // Every 2 s (was 4 times a second): revoking the permission ends the app anyway, and a muted
                        // microphone only delivers silence, so at most 2 s of silence is read before stopping.
                        privacyCheckAt = SystemClock.elapsedRealtime() + if (legacy) 250 else 2_000
                        check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { tr(R.string.microphone_access_was_removed_listening_stopped) }
                        // Muted microphone (Android also sets this when you press mute during a call): pause, with no time
                        // limit, and carry on when it is unmuted. Muted audio is silence, so nothing is heard meanwhile.
                        val muted = audioManager.isMicrophoneMute
                        if (muted != mutedPause) {
                            mutedPause = muted
                            if (muted) {
                                mutableState.update { it.copy(note = tr(R.string.paused_mic_muted_resumes_when_unmuted), level = 0f, speechActive = false) }
                                pausedNotification(tr(R.string.the_microphone_is_muted_sidekick_carries))
                            } else { mutableState.update { it.copy(note = tr(R.string.listening_the_microphone_is_back)) }; pausedNotification(null) }
                        }
                        // Another app took the microphone (a call, a recorder, the camera): wait and carry on when it is
                        // free; stop only if it stays taken for 10 minutes. Silenced audio is silence, so nothing is heard.
                        val silenced = !muted && Build.VERSION.SDK_INT >= 29 && audio.activeRecordingConfiguration?.isClientSilenced == true
                        when (handback.update(silenced, SystemClock.elapsedRealtime())) {
                            com.sidekick.core.MicHandback.State.TAKEN -> if (mutableState.value.note != tr(R.string.paused_another_app_is_using_the)) {
                                mutableState.update { it.copy(note = tr(R.string.paused_another_app_is_using_the), level = 0f, speechActive = false) }
                                pausedNotification(tr(R.string.another_app_is_using_the_microphone))
                            }
                            com.sidekick.core.MicHandback.State.RETURNED -> { mutableState.update { it.copy(note = tr(R.string.listening_the_microphone_is_back)) }; pausedNotification(null) }
                            com.sidekick.core.MicHandback.State.GIVE_UP ->
                                error(tr(R.string.mic_kept_by_other_app))
                            com.sidekick.core.MicHandback.State.LISTENING -> Unit
                        }
                    }
                    // Waits for audio instead of checking every 10 ms: 1 s at a time (one wake-up a second; Android buffers 8 s,
                    // so nothing is lost), or a quarter-second around SideKick's own speech so it is muted precisely.
                    if (filled == 0) {
                        blockStart = SystemClock.elapsedRealtime()
                        target = if (legacy || noticePlaying || answerPlaying || blockStart < muteUntil + 1_000) 4096 else raw.size
                        blockMuted = speakingNotice(blockStart)
                    }
                    val read = audio.read(raw, filled, target - filled, if (legacy) AudioRecord.READ_NON_BLOCKING else AudioRecord.READ_BLOCKING)
                    captureHealth.check(read, SystemClock.elapsedRealtime())
                    if (read > 0) filled += read
                    if (filled >= target) {
                        // Muted if SideKick was speaking when this block started, is speaking now, or stopped during it.
                        val mute = blockMuted || speakingNotice(SystemClock.elapsedRealtime()) || muteUntil > blockStart
                        val detected = feed(filled, mute)
                        if (detected) rest.activity(SystemClock.elapsedRealtime())
                        filled = 0
                        // The level meter and timer are only for the screen: skipped while it is off.
                        if (legacy || power.isInteractive || detected != mutableState.value.speechActive)
                            mutableState.update { it.copy(level = lastLevel, speechActive = detected, seconds = ((SystemClock.elapsedRealtime() - startedClock) / 1000).toInt()) }
                    }
                    // Tell the user while SideKick is catching up, or if audio ever had to be dropped (memory low).
                    val backlogNote = when {
                        pipeline.droppedCount > reportedDrops -> { reportedDrops = pipeline.droppedCount
                            tr(R.string.phone_memory_was_low_1_s, pipeline.droppedCount) }
                        pipeline.catchingUp && !mutableState.value.note.startsWith(tr(R.string.catching_up_faster_recognition_for_a, 0).substringBefore("0")) ->
                            tr(R.string.catching_up_faster_recognition_for_a, pipeline.waitingSeconds)
                        else -> null
                    }
                    if (backlogNote != null) mutableState.update { it.copy(note = backlogNote) }
                    if (legacy) delay(10) else if (read <= 0) delay(20) // new: only when the microphone delivered nothing
                }
                } finally {
                    runCatching { unregisterReceiver(wakeReceiver) }
                    if (carry != null) runCatching { sensors.cancelTriggerSensor(carried, carry) }
                }
                if (filled > 0) feed(filled, speakingNotice(SystemClock.elapsedRealtime()) || muteUntil > blockStart)
                runCatching { audio.stop() }; audio.release(); recorder = null
                mutableState.update { it.copy(recording = false, processing = true, level = 0f, note = tr(R.string.finishing_remaining_speech)) }
                if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                else getSystemService(NotificationManager::class.java).notify(1, notification(false))
                pipeline.finish()
                worker.join()
                if (heldText != null) { releaseHeld(false, "", ""); app.automation.process() }
                watchers.forEach { it.cancel() }
            }
            currentCoroutineContext().ensureActive()
            var wakeSaved = false
            if (wakeEnrollment && !discard && wakeSamples.size == com.sidekick.core.WakeVoice.SAMPLES) {
                check(app.voiceProfile.consent.value?.valid == true) { tr(R.string.voice_setup_needs_your_consent_first) }
                check(app.voiceProfile.model.value == setupModel) { tr(R.string.voice_settings_changed_repeat_setup_in) }
                wakeSaved = runCatching { app.voiceProfile.saveWake(wakeSamples) }.isSuccess
            }
            if (enrollment && !wakeEnrollment && !discard && enrollmentSamples.size == 3) {
                check(app.voiceProfile.consent.value?.valid == true) { tr(R.string.voice_setup_needs_your_consent_first) }
                check(app.voiceProfile.language.value == setupLanguage && app.voiceProfile.model.value == setupModel) { tr(R.string.voice_settings_changed_repeat_setup_in) }
                app.voiceProfile.save(enrollmentSamples, profileTarget, enrollmentSetting)
            }
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (wakeEnrollment) {
                    if (wakeSaved) tr(R.string.sidekick_voice_check_is_set_up)
                    else if (wakeSamples.size == com.sidekick.core.WakeVoice.SAMPLES) tr(R.string.your_sidekick_samples_differed_too_much)
                    else if (wakeTries >= com.sidekick.core.WakeVoice.MAX_TRIES) tr(R.string.your_sidekick_samples_kept_differing_try)
                    else tr(R.string.sidekick_voice_check_setup_stopped_nothing)
                } else if (enrollment) {
                    if (enrollmentSamples.size == 3) tr(R.string.voice_setup_complete_your_1_s, profileTarget.label, enrollmentSetting.label.lowercase())
                    else tr(R.string.voice_setup_incomplete_speak_in_longer)
                } else if (it.chunks == 0) tr(R.string.no_speech_detected_try_speaking_closer) else if (app.guidance.handsOffMode.value) app.automation.status.value else tr(R.string.session_finished_review_and_approve_commitments)) }
        } catch (e: CancellationException) {
            if (!discard && !finish) android.util.Log.w("SidekickListen", "listening cancelled by Android or the app")
            if (!discard && !finish) CaptureStatusAlerts.stopped(this)
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (discard) tr(R.string.session_discarded) else tr(R.string.listening_interrupted_review_text_captured_so)) }
            throw e
        } catch (e: Exception) {
            // The stop reason only (our own message, never words or audio), so an interruption can be diagnosed later.
            retryWanted = !enrollment && !discard && !finish && retry.allow(e.message, SystemClock.elapsedRealtime())
            android.util.Log.w("SidekickListen", "listening ${if (retryWanted) "restarting after" else "stopped"}: ${e.javaClass.simpleName}: ${e.message}")
            if (!retryWanted) {
                CaptureStatusAlerts.stopped(this)
                if (!enrollment) app.assistant.disarm(tr(R.string.listening_paused_after_an_error_start))
            }
            mutableState.update { it.copy(recording = false, processing = retryWanted, level = 0f,
                note = if (retryWanted) tr(R.string.restarting_listening_after_a_problem) else e.message ?: tr(R.string.listening_failed_review_the_text_captured)) }
        } finally {
            runCatching { recorder?.stop() }; recorder?.release()
            // Learning across sessions: only after a normal session (not voice setup, not discarded).
            if (!enrollment && !discard) runCatching { turns.learnInto(app.voiceProfile) }
            processor?.close(); raw.fill(0); turns.close(); speaker.close(); enrollmentSamples.forEach { it.fill(0f) }; wakeSamples.forEach { it.fill(0f) }
            NativeVad.close()
            if (wake.isHeld) wake.release()
            if (!enrollment) { app.temporary.forgetVoiceNames(); turnEngine = null }
            if (discard && !enrollment) {
                app.temporary.discardSession()
                // The session's notice records go with the session's data.
                sessionId?.let { id -> runCatching { kotlinx.coroutines.runBlocking { app.database.noticeRecords().deleteSession(id) } } }
            }
        }
        return retryWanted
    }

    override fun onTimeout(startId: Int) { CaptureStatusAlerts.stopped(this); app.assistant.disarm(tr(R.string.android_stopped_listening)); capture?.cancel(); stopSelf() }
    override fun onDestroy() { noticeVoice?.shutdown(); noticeVoice = null; answerVoice?.shutdown(); answerVoice = null; gate = null; scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        private const val CHANNEL = "listening"
        const val FINISH = "com.sidekick.FINISH"
        const val CANCEL = "com.sidekick.CANCEL"
        const val ENROLL = "com.sidekick.ENROLL"
        const val ENROLL_LANGUAGE = "com.sidekick.ENROLL_LANGUAGE"
        const val ENROLL_SETTING = "com.sidekick.ENROLL_SETTING"
        const val ENROLL_WAKE = "com.sidekick.ENROLL_WAKE"
        const val ANNOUNCE = "com.sidekick.ANNOUNCE"
        const val NOTICE_GIVEN = "com.sidekick.NOTICE_GIVEN"
        const val NOTICE = com.sidekick.core.ConsentNotice.BASE
        const val NAME_VOICE = "com.sidekick.NAME_VOICE"
        const val VOICE_KEY = "com.sidekick.VOICE_KEY"
        const val VOICE_NAME = "com.sidekick.VOICE_NAME"
        const val RESUME = "com.sidekick.RESUME_ASSISTANT"
        private val mutableState = MutableStateFlow(ListeningState())
        val state = mutableState.asStateFlow()
    }
}
