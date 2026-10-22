package com.sidekick.app.speech

/** One process-wide inference operation at a time, serialized by SpeechEngine. */
object NativeSpeech {
    init { System.loadLibrary("sidekick_speech") }
    external fun prepare()
    external fun cancel()
    external fun transcribe(path: String, pcm: FloatArray): ByteArray
}
