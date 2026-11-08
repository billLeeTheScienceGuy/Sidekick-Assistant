package com.sidekick.app

import android.content.Intent
import android.provider.CalendarContract
import com.sidekick.app.data.CommitmentEntity

/** Explicit handoff to the user's calendar editor; no account access or background writes. */
object CalendarDraft {
    fun intent(item: CommitmentEntity): Intent {
        require(item.state == "ACCEPTED") { "Choose an active saved event." }
        val start = requireNotNull(item.activityStartAt) { "Choose an activity start first." }
        item.activityEndAt?.let { require(it > start) { "End must follow start." } }
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, item.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.Events.EVENT_TIMEZONE, java.time.ZoneId.of(item.zoneId).id)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
            .apply {
                item.activityEndAt?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                (item.placeAddress ?: item.placeName)?.takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            }
    }
}
