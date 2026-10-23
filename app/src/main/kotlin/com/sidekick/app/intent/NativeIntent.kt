package com.sidekick.app.intent

object NativeIntent {
    init { System.loadLibrary("sidekick_intent") }
    external fun prepare()
    external fun cancel()
    external fun extract(path: String, prompt: ByteArray, grammar: ByteArray): ByteArray
}
