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
    /** One line for the home screen: the rule where you are, set once listening has checked the location. */
    val rulesLabel: String = "",
    /** True while other people's words are being dropped because notice has not been given. */
    val noticeNeeded: Boolean = false,
    /** Phrases from other voices discarded on arrival this session. */
    val droppedChunks: Int = 0,
    /** Label of the owner's attestation button (TX/WA: "I told everyone (incl. voice analysis)"). */
    val attestLabel: String = "I told everyone (incl. voice analysis)",
    /** A grouped voice may be named for this session right now (ConsentGate.namingAllowed). */
    val namingAllowed: Boolean = false,
    /** A notice is currently in effect (for "Announce again"). */
    val noticeActive: Boolean = false,
    /** A phone call is active: strictest rule for the call. */
    val callActive: Boolean = false,
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
    @Volatile private var gate: com.sidekick.core.ConsentGate? = null
    @Volatile private var turnEngine: com.sidekick.app.speaker.SpeakerTurnEngine? = null
    @Volatile private var sessionId: String? = null
    private var noticeVoice: android.speech.tts.TextToSpeech? = null
    /** While the spoken notice plays (plus a short tail), microphone input is replaced by silence: SideKick never transcribes itself. */
    @Volatile private var muteUntil = 0L
    @Volatile private var noticePlaying = false
    private fun speakingNotice(now: Long) = noticePlaying || now < muteUntil
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
        mutableState.update { it.copy(consent = g.status(now) + " · " + g.place.reason, rulesLabel = g.homeLabel(), noticeNeeded = g.noticeNeeded(),
            attestLabel = g.attestLabel(), namingAllowed = g.namingAllowed(now), noticeActive = g.noticeAtMs != null) }
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
            refuse("Notice not played: sound would go to headphones or Bluetooth. Use the phone speaker, or tell everyone."); return
        }
        checkCovered { covered ->
            if (covered) refuse("Phone seems covered or in a pocket. Take it out and announce again, or tell everyone.")
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
            mutableState.update { it.copy(note = "Notice too quiet: raise the volume or tell everyone, then tap \"I told everyone\".") }
            return
        }
        noticeLevels.clear()
        noticeVoice?.shutdown()
        noticeVoice = android.speech.tts.TextToSpeech(this) { status ->
            val tts = noticeVoice
            if (status == android.speech.tts.TextToSpeech.SUCCESS && tts != null) {
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) { noticePlaying = true }
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
                            mutableState.update { it.copy(note = "Notice too quiet to count. Uncover the phone or raise the volume and announce again, or tell everyone.") }
                        }
                    }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) {
                        noticePlaying = false; muteUntil = SystemClock.elapsedRealtime() + 700
                        restore(); autoAnnouncer.failed()
                        mutableState.update { it.copy(note = "Couldn't play the notice. Tell everyone you're taking notes, then tap \"I told everyone\".") }
                    }
                })
                noticePlaying = true // mute before audio starts; onDone/onError release it
                val params = android.os.Bundle().apply { putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, stream) }
                if (tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "sidekick-notice") != android.speech.tts.TextToSpeech.SUCCESS) {
                    noticePlaying = false; restore(); autoAnnouncer.failed()
                }
            } else {
                restore(); autoAnnouncer.failed()
                mutableState.update { it.copy(note = "No text-to-speech voice. Tell everyone you're taking notes, then tap \"I told everyone\".") }
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
                    app.temporary.voiceNamed(key, name?.let(com.sidekick.core.SessionVoices::cleanName), "Voice")
                } else mutableState.update { it.copy(note = "Voices can't be named right now.") }
                return START_NOT_STICKY
            }
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
        enrollmentSetting = com.sidekick.app.speaker.VoiceSetting.entries.firstOrNull { it.name == intent?.getStringExtra(ENROLL_SETTING) } ?: com.sidekick.app.speaker.VoiceSetting.QUIET
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
        // Until the location lookup answers, the strictest rule applies.
        val strictest = app.guidance.strictestEverywhere.value
        val consent = com.sidekick.core.ConsentGate(
            if (strictest) com.sidekick.core.RecordingJurisdiction.strictestEverywhere() else com.sidekick.core.RecordingJurisdiction.unknown("Checking location…"),
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
                // The latest located place; a phone call overrides it with the strictest rule until the call ends.
                var located = consent.place
                var inCall = false
                // Location, settings and call watchers end with the session (they would otherwise keep this scope open).
                val watchers = mutableListOf<Job>()
                var follower: Job? = null
                fun applyPlace(place: com.sidekick.core.RecordingJurisdiction.Place) {
                    located = place
                    if (!inCall) consent.moveTo(place)
                    refreshConsent()
                }
                if (!enrollment) {
                    refreshConsent()
                    // "Strictest rules everywhere": no location requests at all.
                    if (!strictest) follower = launch {
                        try { JurisdictionLocator.follow(this@ListeningService) { place -> applyPlace(place) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { applyPlace(com.sidekick.core.RecordingJurisdiction.unknown("Location lookup failed")) }
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
                        while (isActive) {
                            val calling = manager.mode == AudioManager.MODE_IN_CALL || manager.mode == AudioManager.MODE_IN_COMMUNICATION
                            // A notice covers one conversation: 30 min without other voices, or 2 hours at most.
                            val nowMs = SystemClock.elapsedRealtime()
                            consent.expireIfStale(nowMs)?.let { reason ->
                                refreshConsent()
                                // 2-hour cap in an ongoing meeting: renew right away if the setting is on.
                                if (autoAnnouncer.expired(reason, nowMs, consent.othersLastMs)) mainHandler.post { announce(auto = true) }
                            }
                            if (calling != inCall) {
                                inCall = calling
                                if (calling) consent.callStarted() else consent.moveTo(located)
                                mutableState.update { it.copy(callActive = calling) }
                                refreshConsent()
                            }
                            delay(1_000)
                        }
                    }
                }
                var identified = Speaker.UNKNOWN
                var voiceTrack = "spk_session_unassigned0"
                var voiceLabel = "Unassigned voice"
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
                        if (owner) track else "spk_session_unassigned_held", if (owner) label else "Unassigned voice", recognitionReliable = h.reliable, timeChoices = h.choices)
                    if (owner) mutableState.update { it.copy(userVoiceChunks = it.userVoiceChunks + 1, unknownVoiceChunks = (it.unknownVoiceChunks - 1).coerceAtLeast(0)) }
                    // Saved plans are processed with the next chunk's text (or at session end).
                }
                val pipeline = LiveSpeechProcessor(app.speech, onBoundary = { continuation = it && !changedVoice }, contextKey = { if (identified == Speaker.USER) voiceTrack else null }, voiceKey = { voiceTrack },
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
                            voiceName = attributed.name; voiceGrouped = attributed.grouped
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
                                "notice=${consent.noticeAtMs != null} grouping=${consent.groupingAllowed()} call=${mutableState.value.callActive} · ${attributed.scores}")
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
                    if (!discard && holdNext && text.isNotBlank()) {
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
                        if (noticePlaying) noticeLevels.add(level)
                        if (speakingNotice(SystemClock.elapsedRealtime())) samples.fill(0f)
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
                if (heldText != null) { releaseHeld(false, "", ""); app.automation.process() }
                watchers.forEach { it.cancel() }
            }
            currentCoroutineContext().ensureActive()
            if (enrollment && !discard && enrollmentSamples.size == 3) {
                check(app.voiceProfile.consent.value?.valid == true) { "Voice setup needs your consent first. Nothing was saved." }
                check(app.voiceProfile.language.value == setupLanguage && app.voiceProfile.model.value == setupModel) { "Voice settings changed. Repeat setup in the selected language." }
                app.voiceProfile.save(enrollmentSamples, profileTarget, enrollmentSetting)
            }
            mutableState.update { it.copy(recording = false, processing = false, level = 0f,
                note = if (enrollment) {
                    if (enrollmentSamples.size == 3) "Voice setup complete. Your ${profileTarget.label} profile (${enrollmentSetting.label.lowercase()}) is saved."
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
            // Learning across sessions: only after a normal session (not voice setup, not discarded).
            if (!enrollment && !discard) runCatching { turns.learnInto(app.voiceProfile) }
            processor?.close(); raw.fill(0); turns.close(); speaker.close(); enrollmentSamples.forEach { it.fill(0f) }
            NativeVad.close()
            if (wake.isHeld) wake.release()
            if (!enrollment) { app.temporary.forgetVoiceNames(); turnEngine = null }
            if (discard && !enrollment) {
                app.temporary.discardSession()
                // The session's notice records go with the session's data.
                sessionId?.let { id -> runCatching { kotlinx.coroutines.runBlocking { app.database.noticeRecords().deleteSession(id) } } }
            }
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
        const val ENROLL_SETTING = "com.sidekick.ENROLL_SETTING"
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
