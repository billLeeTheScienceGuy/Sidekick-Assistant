package com.sidekick.app.speech

/** Owned by one listening session; separate from the serialized Whisper worker. */
object NativeVad {
    init { System.loadLibrary("sidekick_speech") }
    external fun open(path: String)
    external fun probabilities(pcm: FloatArray): FloatArray
    external fun close()
}
