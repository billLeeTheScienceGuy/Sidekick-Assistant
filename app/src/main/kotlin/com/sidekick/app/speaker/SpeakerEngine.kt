package com.sidekick.app.speaker

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.sidekick.core.Speaker
import com.sidekick.core.VoiceMatch
import java.io.File
import java.security.MessageDigest

/** Called only on the single speech worker, before Whisper wipes each PCM chunk. */
class SpeakerEngine(private val context: Context, private val profile: VoiceProfile,
    /** Owner-match thresholds; stricter before notice in all-party places (ConsentGate.strictOwnerMatch). */
    private val thresholds: () -> VoiceMatch.Thresholds = { VoiceMatch.Thresholds.STANDARD }) {
    var lastDiagnostic: String = "No speech analyzed"
        private set
    private var extractor: SpeakerEmbeddingExtractor? = null
    private var extractorModel: SpeakerModel? = null
    private val anchors = com.sidekick.core.VoiceAnchors()
    /** True when the last [identify] accepted the owner from a long turn against the saved profile (not a short phrase). */
    var lastLongOwner: Boolean = false
        private set
    /** The last long turn matched the quiet-room profile very confidently (learning across sessions may use it). */
    var lastVeryConfident: Boolean = false
        private set
    private val cohorts = mutableMapOf<SpeakerModel, com.sidekick.core.StrangerCohort?>()
    private fun cohort(model: SpeakerModel): com.sidekick.core.StrangerCohort? = cohorts.getOrPut(model) {
        runCatching { context.assets.open(model.cohortAsset).use { com.sidekick.core.StrangerCohort.read(it.readBytes()) } }
            .getOrNull()?.takeIf { it.dimension == model.dimension }
    }
    private fun strict(base: VoiceMatch.Thresholds) = base == VoiceMatch.Thresholds.BEFORE_NOTICE || !base.shortPhrases
    /**
     * The place picks standard or before-notice strictness; the selected model's tuned rule applies only when its
     * stranger cohort loaded (the tuned rule relies on it). Otherwise the original fixed thresholds stay.
     */
    fun rules(): Pair<VoiceMatch.Thresholds, com.sidekick.core.StrangerCohort?> {
        val base = thresholds(); val model = profile.model.value
        val cohort = cohort(model) ?: return base to null
        return (if (strict(base)) model.beforeNotice else model.standard) to cohort
    }
    private var profileLanguage = profile.language.value
    private fun clearRecent() = anchors.clear()
    fun newTurn() { clearRecent() }
    private fun extractor(): SpeakerEmbeddingExtractor {
        val selected = profile.model.value
        if (extractorModel != selected) { extractor?.release(); extractor = null; clearRecent(); extractorModel = selected }
        extractor?.let { return it }
        val model = File(context.noBackupFilesDir, selected.fileName)
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!model.exists() || hash(model) != selected.hash) {
            val temp = File(context.noBackupFilesDir, "speaker-model.download")
            try {
                context.assets.open("models/${selected.fileName}").use { input -> temp.outputStream().use(input::copyTo) }
                check(hash(temp) == selected.hash) { "Speaker model checksum mismatch" }
                check(temp.renameTo(model)) { "Could not install speaker model" }
            } finally { temp.delete() }
        }
        return SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model.absolutePath, numThreads = 1)).also {
            check(it.dim() == selected.dimension); extractor = it
        }
    }
    fun embedding(samples: FloatArray): FloatArray {
        val span = com.sidekick.core.SpeakerAudio.speechSpan(samples) ?: throw IllegalArgumentException("No clear speech for voice setup.")
        val speech = samples.copyOfRange(span.first, span.last + 1)
        return try { computeEmbedding(speech) } finally { speech.fill(0f) }
    }
    /** Setup must work with independent short windows, not only a whole-utterance average. */
    fun enrollmentEmbedding(samples: FloatArray): FloatArray {
        val span = com.sidekick.core.SpeakerAudio.speechSpan(samples)
            ?: throw IllegalArgumentException("No clear speech. Speak closer to the phone.")
        val speech = samples.copyOfRange(span.first, span.last + 1)
        val vectors = mutableListOf<FloatArray>()
        var reference: FloatArray? = null
        var success = false
        try {
            require(speech.size >= 64_000) { "Use a full sentence with at least four seconds of speech, then pause." }
            val candidate = computeEmbedding(speech); reference = candidate
            val width = minOf(48_000, speech.size / 2)
            for (start in listOf(0, speech.size - width)) {
                val range = com.sidekick.core.SpeakerAudio.matchingWindow(speech, start, width)
                    ?: throw IllegalArgumentException("Too much silence. Read a full sentence naturally.")
                val window = speech.copyOfRange(range.first, range.last + 1)
                try { vectors += computeEmbedding(window) } finally { window.fill(0f) }
            }
            require(VoiceMatch.identifyTurn(candidate, vectors) == Speaker.USER) {
                "Voice sample did not match consistently. Keep the phone at your usual distance and speak normally."
            }
            success = true
            return candidate
        } finally {
            if (!success) reference?.fill(0f)
            speech.fill(0f); vectors.forEach { it.fill(0f) }
        }
    }
    private fun computeEmbedding(samples: FloatArray): FloatArray {
        require(samples.size >= 24_000) { "Speak in longer phrases for voice setup." }
        require(samples.count { kotlin.math.abs(it) > .005f } >= samples.size / 10) { "Speak louder, closer to the microphone." }
        require(samples.count { kotlin.math.abs(it) >= .99f } < samples.size / 100) { "Microphone was too loud; move farther away." }
        val engine = extractor()
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, 16000); stream.inputFinished()
            check(engine.isReady(stream)) { "Not enough speech for voice setup." }
            VoiceMatch.normalized(engine.compute(stream))
        } finally { stream.release() }
    }
    /** [session]: this session's confirmed owner turns, for the session-adapted profile (idea 2). */
    fun identify(samples: FloatArray, nowMs: Long = android.os.SystemClock.elapsedRealtime(), session: com.sidekick.core.OwnerSession? = null): Speaker {
        lastLongOwner = false; lastVeryConfident = false
        val span = com.sidekick.core.SpeakerAudio.speechSpan(samples)
        if (span == null) {
            clearRecent(); lastDiagnostic = "No clear speech after excluding quiet edges"; return Speaker.UNKNOWN
        }
        val speech = samples.copyOfRange(span.first, span.last + 1)
        return try {
            identifySpeech(speech, nowMs, session).also {
                lastDiagnostic = String.format(java.util.Locale.US, "%.1fs captured · %s", samples.size / 16000.0, lastDiagnostic.replaceFirst("s:", "s speech:"))
            }
        } finally { speech.fill(0f) }
    }
    private fun identifySpeech(samples: FloatArray, nowMs: Long, session: com.sidekick.core.OwnerSession?): Speaker {
        if (profileLanguage != profile.language.value) { clearRecent(); profileLanguage = profile.language.value }
        val seconds = "%.1f".format(java.util.Locale.US, samples.size / 16000.0)
        val labelled = profile.matchingReferences()
        val references = labelled.map { it.second }
        val quiet = labelled.map { it.first == VoiceSetting.QUIET }
        if (references.isEmpty()) {
            clearRecent()
            lastDiagnostic = if (profile.consentNeeded.value) "${seconds}s: voice consent needed; matching off"
                else if (profile.enabled.value) "${seconds}s: no enrolled profile" else "${seconds}s: voice matching is off"
            return Speaker.UNKNOWN
        }
        val (t, cohort) = rules()
        val mode = (if (strict(thresholds())) "before-notice" else "standard") + if (cohort != null) " tuned" else ""
        try {
            if (samples.size < 48_000) {
                val age = anchors.ageMs(nowMs)
                if (samples.size < 24_000 || age == null) {
                    lastDiagnostic = if (samples.size < 24_000) "${seconds}s: too short; need at least 1.5 seconds"
                        else "${seconds}s: no recent confident long phrase; speaker unassigned"
                    clearRecent()
                    return Speaker.UNKNOWN
                }
                return try {
                    // identify() already trimmed the utterance; preserve this validated window.
                    val vector = computeEmbedding(samples)
                    try {
                        val score = references.maxOf { VoiceMatch.similarity(it, vector) }
                        val support = anchors.support(vector, nowMs) ?: return Speaker.UNKNOWN
                        val recentScore = support.score
                        val result = VoiceMatch.classifyShortScores(score, recentScore, samples.size * 1000L / 16000L, support.ageMs, profile.model.value.shortThreshold, t)
                        lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; short profile %.2f; recent mean %.2f; two-window support %.2f; anchor age %ds; thresholds %s",
                            seconds, result.name, score, support.meanScore, support.windowScore, support.ageMs / 1000, mode)
                        if (result != Speaker.USER && (score < profile.model.value.shortThreshold || recentScore < profile.model.value.shortThreshold - .15f)) clearRecent()
                        // Borderline evidence stays unassigned but does not erase a trusted turn.
                        // Short phrases never add embeddings or extend the anchor's lifetime.
                        result
                    } finally { vector.fill(0f) }
                } catch (e: IllegalArgumentException) {
                    clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN
                }
            }
            // Speaker windows are independent of the ASR window and overlap to expose turn changes.
            val width = if (samples.size < 64_000) samples.size / 2 else 48_000
            val starts = (0..samples.size - width step 32_000).toMutableSet().apply { add(samples.size - width) }.sorted()
            val vectors = mutableListOf<FloatArray>()
            return try {
                for (start in starts) {
                    val range = com.sidekick.core.SpeakerAudio.matchingWindow(samples, start, width)
                    if (range == null) { clearRecent(); lastDiagnostic = "${seconds}s: not enough voiced speech in a matching window"; return Speaker.UNKNOWN }
                    val window = samples.copyOfRange(range.first, range.last + 1)
                    // Re-trimming each window can shrink a long turn below 1.5 seconds at a pause.
                    try { vectors += computeEmbedding(window) } finally { window.fill(0f) }
                }
                var result = VoiceMatch.identifyProfiles(references, vectors, t, cohort, quiet)
                lastLongOwner = result == Speaker.USER
                if (result == Speaker.USER && thresholds() != VoiceMatch.Thresholds.BEFORE_NOTICE) {
                    val c = com.sidekick.core.VoiceprintPolicy.LEARN_CONFIDENT
                    val confident = t.copy(both = c, balancedMin = c, balancedMean = c, quietOffset = 0f)
                    lastVeryConfident = labelled.any { (setting, reference) -> setting == VoiceSetting.QUIET && VoiceMatch.identifyTurn(reference, vectors, confident) == Speaker.USER }
                }
                // Idea 2: the quiet profile nudged toward how the owner sounded earlier in this session. Never confirms
                // a turn for the session itself and never overrides "someone else".
                var adapted = false
                val adapt = t.adapt
                if (result == Speaker.UNKNOWN && adapt != null && session != null) {
                    val mean = session.mean(nowMs)
                    val quietRef = labelled.firstOrNull { it.first == VoiceSetting.QUIET }?.second ?: references.first()
                    if (mean != null && mean.size == quietRef.size) {
                        val profileNow = adapt.profile(quietRef, mean)
                        try {
                            val rule = adapt.rule(t)
                            if (VoiceMatch.identifyTurn(profileNow, vectors, rule) == Speaker.USER && VoiceMatch.aboveStrangers(profileNow, vectors, rule, cohort)) {
                                result = Speaker.USER; adapted = true
                            }
                        } finally { profileNow.fill(0f); mean.fill(0f) }
                    }
                }
                val reference = references.firstOrNull { VoiceMatch.identifyTurn(it, vectors, t) == Speaker.USER }
                    ?: references.maxBy { candidate -> vectors.sumOf { VoiceMatch.similarity(candidate, it).toDouble() } }
                if (result == Speaker.USER) anchors.remember(vectors, nowMs) else clearRecent()
                val scores = vectors.joinToString(" / ") { String.format(java.util.Locale.US, "%.2f", VoiceMatch.similarity(reference, it)) }
                val consistency = vectors.zipWithNext { a, b -> VoiceMatch.similarity(a, b) }.minOrNull() ?: 0f
                lastDiagnostic = String.format(java.util.Locale.US, "%ss: %s; %d speaker windows; profile %s; minimum consistency %.2f; %d enrolled profiles; thresholds %s",
                    seconds, result.name, vectors.size, scores, consistency, references.size, mode) + if (adapted) "; session-adapted profile" else ""
                result
            }
                catch (e: IllegalArgumentException) { clearRecent(); lastDiagnostic = "${seconds}s: ${e.message}"; Speaker.UNKNOWN }
                finally { vectors.forEach { it.fill(0f) } }
        } finally { references.forEach { it.fill(0f) } }
    }
    /**
     * Session voice carry for a chunk the profile left unassigned: [vector] is its whole-speech signature. Owner only
     * when it matches this session's profile-confirmed owner turns and the profile is not far off ([OwnerSession]).
     */
    fun carry(session: com.sidekick.core.OwnerSession, vector: FloatArray, nowMs: Long): com.sidekick.core.OwnerSession.Carry {
        val references = profile.matchingSnapshots()
        return try {
            if (references.isEmpty() || references.any { it.size != vector.size })
                com.sidekick.core.OwnerSession.Carry(false, Float.NaN, Float.NaN, "no saved profile")
            else session.carry(vector, references.maxOf { VoiceMatch.similarity(it, vector) }, nowMs, rules().first)
        } finally { references.forEach { it.fill(0f) } }
    }
    fun close() { clearRecent(); extractor?.release(); extractor = null; extractorModel = null }
    companion object { const val HASH = "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012" }
}
