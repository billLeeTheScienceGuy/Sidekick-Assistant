package com.sidekick.app

import android.app.*
import android.content.Context
import android.content.Intent
import com.sidekick.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalRiskAlerts(private val context: Context, private val enabled: () -> Boolean) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    private val current = MutableStateFlow<List<RiskAdvisory>>(emptyList())
    val alerts = current.asStateFlow()
    private val lastAlert = mutableMapOf<String, Long>()
    init { manager.createNotificationChannel(NotificationChannel("risk-advisories", "Local risk advisories", NotificationManager.IMPORTANCE_DEFAULT)) }
    @Synchronized fun inspect(text: String) {
        if (!enabled()) return
        for (risk in RiskAdvisories.inspect(text)) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastAlert[risk.id]?.let { now - it < 900_000 } == true) continue
            lastAlert[risk.id] = now
            current.value = (current.value.filterNot { it.id == risk.id } + risk).takeLast(4)
            try {
                if (manager.areNotificationsEnabled()) manager.notify("risk:${risk.id}", 31,
                    Notification.Builder(context, "risk-advisories").setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("SideKick · ${risk.title}").setContentText(risk.explanation)
                        .setStyle(Notification.BigTextStyle().bigText(risk.explanation))
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
            } catch (_: SecurityException) { /* The in-app advisory remains available without notification permission. */ }
        }
    }
    @Synchronized fun dismiss(id: String) { current.value = current.value.filterNot { it.id == id }; manager.cancel("risk:$id", 31) }
    @Synchronized fun clear() {
        listOf("gift-payment", "secret-request", "remote-access", "safe-account").forEach { manager.cancel("risk:$it", 31) }
        current.value = emptyList(); lastAlert.clear()
    }
}
