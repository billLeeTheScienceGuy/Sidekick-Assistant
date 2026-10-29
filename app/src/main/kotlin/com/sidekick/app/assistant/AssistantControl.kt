package com.sidekick.app.assistant

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remember an explicitly started session; Stop always clears recovery before stopping. */
class AssistantControl(private val context: Context) {
    private val preferences = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    private val enabledValue = MutableStateFlow(preferences.getBoolean("recover_listening", true))
    val enabled = enabledValue.asStateFlow()
    private val selectedValue = MutableStateFlow(isSelected())
    val selected = selectedValue.asStateFlow()
    private val statusValue = MutableStateFlow("Start listening once to enable session recovery")
    val status = statusValue.asStateFlow()
    val armed: Boolean get() = preferences.getBoolean("session_armed", false)
    fun isSelected(): Boolean = VoiceInteractionService.isActiveService(context, ComponentName(context, SidekickVoiceService::class.java))
    fun refresh() { selectedValue.value = isSelected() }
    fun picker(): Intent = if (Build.VERSION.SDK_INT >= 29 && context.getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_ASSISTANT))
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        else Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("recover_listening", value).commit(); enabledValue.value = value
        if (!value) disarm("Automatic recovery disabled")
    }
    fun rememberUserStart() {
        refresh()
        val recover = enabledValue.value && isSelected()
        preferences.edit().putBoolean("session_armed", recover).commit()
        statusValue.value = if (recover) "Listening recovery enabled until you stop" else "Manual listening session"
    }
    fun disarm(reason: String = "Stopped · listening will not restart") {
        preferences.edit().putBoolean("session_armed", false).commit(); statusValue.value = reason
    }
    fun canRecover(): Boolean = enabledValue.value && armed && isSelected() &&
        context.getSystemService(UserManager::class.java).isUserUnlocked &&
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun recover(): Boolean {
        refresh()
        if (!canRecover()) return false
        if (ListeningService.state.value.recording || ListeningService.state.value.processing) return true
        return try {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ListeningService.RESUME))
            statusValue.value = "Resuming your enabled listening session"; true
        } catch (_: RuntimeException) {
            disarm("Android blocked recovery. Open SideKick and start listening again."); false
        }
    }
    fun roleRemoved() {
        refresh()
        if (!isSelected() && armed) {
            disarm("Default assistant changed · recovery stopped")
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}

class AssistantRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_MY_PACKAGE_REPLACED))
            (context.applicationContext as com.sidekick.app.SidekickApplication).assistant.recover()
    }
}
