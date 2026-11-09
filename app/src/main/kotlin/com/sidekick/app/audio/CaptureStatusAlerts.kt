package com.sidekick.app.audio

import android.app.*
import android.content.Context
import android.content.Intent
import com.sidekick.app.MainActivity

/** A stopped foreground notification must not be the only indication of lost capture. */
object CaptureStatusAlerts {
    private const val CHANNEL = "listening-interruptions"
    const val TAG = "capture-interruption"
    const val ID = 42
    fun clear(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(TAG, ID) }
    @Suppress("MissingPermission")
    fun stopped(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Listening interruptions", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(TAG, ID, Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("SideKick listening stopped")
                .setContentText("Open SideKick to check the interruption and start again when ready.")
                .setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setOnlyAlertOnce(true).build())
        } catch (_: SecurityException) { }
    }
}
