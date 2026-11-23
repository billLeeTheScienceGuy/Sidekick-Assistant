package com.sidekick.app

import android.app.*
import android.content.Context
import android.content.Intent
import com.sidekick.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scam warnings on this phone. Conversations are scored over the last minutes (ScamMonitor); each message or shared
 * text is checked on its own (ScamCheck). "Stop now" warnings use their own channel with a strong vibration. A call
 * with warning signs gets a short summary afterwards. Only which signs were seen is kept, never the words.
 */
class LocalRiskAlerts(private val context: Context, private val enabled: () -> Boolean) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    private val current = MutableStateFlow<List<RiskAdvisory>>(emptyList())
    val alerts = current.asStateFlow()
    private val monitor = ScamMonitor()
    private var shownLevel = ScamLevel.NONE
    private var shownSignals = emptySet<ScamSignal>()
    private var callStart: Long? = null
    private fun now() = android.os.SystemClock.elapsedRealtime()
    init {
        manager.createNotificationChannel(NotificationChannel(CAUTION, tr(R.string.scam_warnings), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(STOP, tr(R.string.scam_warnings_stop_now), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
        })
    }

    /** Conversation text: warns when the level rises or a new strong sign appears. */
    @Synchronized fun inspect(text: String, inCall: Boolean = false) {
        if (!enabled()) return
        val a = monitor.add(text, now(), inCall)
        if (a.level == ScamLevel.NONE) { shownLevel = ScamLevel.NONE; shownSignals = emptySet(); return }
        val newStrong = a.signals.any { it.strong && it !in shownSignals }
        if (a.level <= shownLevel && !newStrong) return
        shownLevel = a.level; shownSignals = a.signals.toSet()
        show("conversation", a)
    }

    /** One message or shared text, on its own. */
    @Synchronized fun inspectMessage(text: String) {
        if (!enabled()) return
        val a = ScamCheck.message(text)
        if (a.level != ScamLevel.NONE) show("message", a, prefix = tr(R.string.message))
    }

    @Synchronized fun callStarted() { callStart = now() }
    /** After a call: a short summary if warning signs were heard during it. */
    @Synchronized fun callEnded() {
        val start = callStart ?: return
        callStart = null
        if (!enabled()) return
        val a = monitor.since(start)
        if (a.signals.isEmpty()) return
        val count = a.signals.size
        show("call", a.copy(level = maxOf(a.level, ScamLevel.CAUTION)), prefix = tr(R.string.after_your_call),
            title = trn(R.plurals.warning_signs_after_call, count, count))
    }

    /** "SideKick, is this a scam?" */
    @Synchronized fun answer(language: String): String = ScamQuestion.answer(if (enabled()) monitor.assessment(now()) else null, language)

    private fun show(id: String, a: ScamAssessment, prefix: String = "", title: String = prefix + a.title) {
        val explanation = tr(R.string.n_1_s_warning_signs_2_s, a.step, a.reasons)
        current.value = (current.value.filterNot { it.id == id } + RiskAdvisory(id, title, explanation)).takeLast(4)
        try {
            if (manager.areNotificationsEnabled()) manager.notify("risk:$id", 31,
                Notification.Builder(context, if (a.level == ScamLevel.STOP) STOP else CAUTION).setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(tr(R.string.sidekick_1_s, title)).setContentText(a.step)
                    .setStyle(Notification.BigTextStyle().bigText(explanation))
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* The in-app warning remains without notification permission. */ }
    }
    @Synchronized fun dismiss(id: String) { current.value = current.value.filterNot { it.id == id }; manager.cancel("risk:$id", 31) }
    @Synchronized fun clear() {
        listOf("conversation", "message", "call").forEach { manager.cancel("risk:$it", 31) }
        current.value = emptyList(); monitor.clear(); shownLevel = ScamLevel.NONE; shownSignals = emptySet()
    }
    private companion object { const val CAUTION = "risk-advisories"; const val STOP = "scam-stop" }
}
