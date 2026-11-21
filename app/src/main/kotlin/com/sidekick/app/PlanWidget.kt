package com.sidekick.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sidekick.app.audio.ListeningService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget: the next saved plan in large text and one big Start/Stop listening button. Start opens SideKick so
 * the usual checks (microphone, consent, notice) run; Stop ends the session directly. Shows only the plan title and time.
 */
class PlanWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as SidekickApplication).background.launch {
            try { render(context) } finally { pending.finish() }
        }
    }

    companion object {
        const val START = "com.sidekick.WIDGET_START"

        /** Redraws every SideKick widget (plans changed or listening started/stopped). */
        suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlanWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as SidekickApplication
            val now = System.currentTimeMillis()
            val next = app.database.commitments().all()
                .filter { it.state == "ACCEPTED" && !it.informational && (it.activityStartAt ?: it.dueAt ?: 0) >= now - 15 * 60_000 }
                .minByOrNull { it.activityStartAt ?: it.dueAt!! }
            val text = next?.let { "${it.title}\n${describe(it.activityStartAt ?: it.dueAt!!, now)}" } ?: "No upcoming plans"
            val recording = ListeningService.state.value.recording
            val views = RemoteViews(context.packageName, R.layout.plan_widget).apply {
                setTextViewText(R.id.widget_next, text)
                setTextViewText(R.id.widget_button, if (recording) "Stop listening" else "Start listening")
                val action = if (recording) PendingIntent.getService(context, 2,
                        Intent(context, ListeningService::class.java).setAction(ListeningService.FINISH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    else PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).setAction(START)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                setOnClickPendingIntent(R.id.widget_button, action)
                setOnClickPendingIntent(R.id.widget_next, PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun describe(at: Long, now: Long): String {
            val zone = ZoneId.systemDefault()
            val time = Instant.ofEpochMilli(at).atZone(zone)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val day = when (time.toLocalDate()) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> time.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
            }
            return if (time.hour == 0 && time.minute == 0) day else "$day · ${time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}"
        }
    }
}
