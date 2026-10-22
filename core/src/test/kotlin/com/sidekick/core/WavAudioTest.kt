package com.sidekick.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class WavAudioTest {
    private fun wav(rate: Int = 16_000): ByteArray {
        val bytes = ByteArray(44 + 3200)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(bytes.size - 8); b.put("WAVEfmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(rate); b.putInt(rate * 2)
        b.putShort(2); b.putShort(16); b.put("data".toByteArray()); b.putInt(3200)
        b.putShort(32767); b.putShort(-32768)
        return bytes
    }
    @Test fun `decodes PCM with correct amplitude`() {
        val samples = WavAudio.decode(wav())
        assertEquals(1600, samples.size)
        assertEquals(32767 / 32768f, samples[0])
        assertEquals(-1f, samples[1])
    }
    @Test fun `rejects unsupported sample rate and truncated files`() {
        assertFails { WavAudio.decode(wav(44_100)) }
        assertFails { WavAudio.decode(wav().copyOf(50)) }
    }
    @Test fun `rejects arbitrary data`() { assertFails { WavAudio.decode(ByteArray(100)) } }
}
