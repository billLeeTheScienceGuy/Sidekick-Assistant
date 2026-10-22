package com.sidekick.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Small, bounded importer for PCM16 mono 16 kHz WAV; never guess compressed formats. */
object WavAudio {
    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size in 44..1_100_000) { "Use a WAV recording of at most 30 seconds." }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE") { "Choose a PCM WAV file, not MP3 or M4A." }
        var validFormat = false
        var data: Pair<Int, Int>? = null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = buffer.getInt(offset + 4)
            require(size >= 0 && size <= bytes.size - offset - 8) { "Truncated WAV file." }
            when (tag(offset)) {
                "fmt " -> {
                    require(size >= 16)
                    validFormat = buffer.getShort(offset + 8).toInt() == 1 && buffer.getShort(offset + 10).toInt() == 1 &&
                        buffer.getInt(offset + 12) == 16_000 && buffer.getShort(offset + 22).toInt() == 16
                }
                "data" -> { require(data == null) { "Multiple audio chunks are not supported." }; data = offset + 8 to size }
            }
            offset += 8 + size + size % 2
        }
        require(validFormat) { "Use 16-bit PCM, 16 kHz, mono WAV audio." }
        val (start, size) = requireNotNull(data) { "No audio found in WAV file." }
        require(size % 2 == 0 && size / 2 in 1600..480_000) { "Audio must be between 0.1 and 30 seconds." }
        return FloatArray(size / 2) { buffer.getShort(start + it * 2) / 32768f }
    }
}
