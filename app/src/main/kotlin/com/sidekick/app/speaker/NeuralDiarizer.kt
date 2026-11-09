package com.sidekick.app.speaker

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.security.MessageDigest

/** Bounded local neural segmentation/clustering. Native speaker IDs are local to each call. */
class NeuralDiarizer(private val context: Context, private val model: () -> SpeakerModel) {
    data class Segment(val start: Float, val end: Float, val speaker: Int)
    private var engine: OfflineSpeakerDiarization? = null
    private var selected: SpeakerModel? = null
    private fun instance(): OfflineSpeakerDiarization {
        val current = model()
        if (selected != current) { engine?.release(); engine = null; selected = current }
        engine?.let { return it }
        val actual = context.assets.open("models/diarization.int8.onnx").use { source ->
            val sha = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val count = source.read(buffer); if (count < 0) break; sha.update(buffer, 0, count) }
            sha.digest().joinToString("") { "%02x".format(it) }
        }
        check(actual == HASH) { "Diarization model checksum mismatch" }
        return OfflineSpeakerDiarization(context.assets, OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig("models/diarization.int8.onnx"), numThreads = 1),
            embedding = SpeakerEmbeddingExtractorConfig("models/${current.fileName}", numThreads = 1),
            clustering = FastClusteringConfig(numClusters = -1, threshold = .5f),
            minDurationOn = .3f, minDurationOff = .3f
        )).also { check(it.sampleRate() == 16000); engine = it }
    }
    fun process(samples: FloatArray): List<Segment> {
        require(samples.size in 1600..160_000 && samples.all { it.isFinite() && it in -1f..1f })
        return instance().process(samples).map { Segment(it.start, it.end, it.speaker) }
    }
    fun close() { engine?.release(); engine = null; selected = null }
    companion object { const val HASH = "d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d" }
}
