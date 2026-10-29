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
        // Inexact by design; Android may defer delivery in Doze.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(requireNotNull(item.dueAt), System.currentTimeMillis() + 1000), pending(item))
    }
    override fun cancel(item: CommitmentEntity) { alarms.cancel(pending(item)) }
}
