package com.sidekick.app.reminders

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Reads one reminder aloud with the phone's text-to-speech voice, then releases it. Korean text uses a Korean voice. */
object ReminderVoice {
    private var current: TextToSpeech? = null

    @Synchronized fun speak(context: Context, text: String) {
        current?.shutdown()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            val voice = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { voice.shutdown(); return@TextToSpeech }
            voice.language = if (text.any { it in '가'..'힣' }) Locale.KOREAN else Locale.getDefault()
            voice.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            voice.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { release(voice) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { release(voice) }
            })
            voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sidekick-reminder")
        }
        current = tts
    }

    @Synchronized private fun release(voice: TextToSpeech) {
        voice.shutdown(); if (current === voice) current = null
    }
}
