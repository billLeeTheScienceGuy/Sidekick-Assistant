package com.sidekick.app.assistant

import com.sidekick.app.R
import com.sidekick.app.tr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import android.widget.*
import com.sidekick.app.MainActivity
import com.sidekick.app.SidekickApplication
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SidekickSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = SidekickSession(this)
}

private class SidekickSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusView: TextView? = null
    private var observing: Job? = null
    private fun status() = when {
        ListeningService.state.value.recording -> tr(R.string.listening_on_this_phone)
        ListeningService.state.value.processing -> tr(R.string.finishing_speech_on_this_phone)
        else -> tr(R.string.open_sidekick_to_start_a_consenting)
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        observing?.cancel()
        observing = scope.launch {
            ListeningService.state.map { it.recording to it.processing }.distinctUntilChanged().collect { statusView?.text = status() }
        }
    }
    override fun onHide() { observing?.cancel(); super.onHide() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onCreateContentView(): View = LinearLayout(appContext).apply {
        orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32); setBackgroundColor(android.graphics.Color.WHITE)
        addView(TextView(appContext).apply { text = tr(R.string.sidekick_your_local_assistant); textSize = 22f; setTextColor(android.graphics.Color.BLACK) })
        addView(TextView(appContext).apply {
            statusView = this; text = status()
            setTextColor(android.graphics.Color.BLACK)
        })
        addView(Button(appContext).apply { text = tr(R.string.open_sidekick); setOnClickListener {
            startAssistantActivity(Intent(appContext, MainActivity::class.java)); finish()
        } })
        addView(Button(appContext).apply { text = tr(R.string.stop_listening); setOnClickListener {
            (appContext.applicationContext as SidekickApplication).assistant.disarm()
            appContext.startService(Intent(appContext, ListeningService::class.java).setAction(ListeningService.FINISH)); finish()
        } })
    }
}
