package com.sidekick.app.assistant

import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import com.sidekick.app.SidekickApplication

/** System-bound assistant. No screen contents, screenshots or hotword model requested. */
class SidekickVoiceService : VoiceInteractionService() {
    private var destroyed = false
    override fun onReady() {
        super.onReady()
        if (destroyed) return
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        (application as SidekickApplication).assistant.recover()
    }
    override fun onShutdown() {
        destroyed = true
        (application as SidekickApplication).assistant.roleRemoved()
        super.onShutdown()
    }
    override fun onDestroy() { destroyed = true; super.onDestroy() }
}
