package com.sidekick.app.reminders

import android.app.*
import android.content.*
import android.net.Uri
import com.sidekick.app.data.CommitmentEntity

interface ReminderAlarms {
    fun arm(item: CommitmentEntity)
    fun cancel(item: CommitmentEntity)
}

internal class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private fun pending(item: CommitmentEntity) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderCoordinator.DELIVER)
            .setData(Uri.parse("sidekick://reminder/${item.id}")).putExtra("dueAt", item.dueAt ?: 0L),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun arm(item: CommitmentEntity) {
        val at = maxOf(requireNotNull(item.dueAt), System.currentTimeMillis() + 1000)
        // Exact, even in deep power saving (reminder apps may use exact alarms). Without that permission, Android may
        // hold an inexact alarm back by several minutes, so that is only the fallback.
        val exact = android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(item)) }
            .onFailure { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(item)) }
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(item))
    }
    override fun cancel(item: CommitmentEntity) { alarms.cancel(pending(item)) }
}
