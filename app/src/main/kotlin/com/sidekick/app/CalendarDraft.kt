package com.sidekick.app

import android.content.Intent
import android.provider.CalendarContract
import com.sidekick.app.data.CommitmentEntity

/** Explicit handoff to the user's calendar editor; no account access or background writes. */
object CalendarDraft {
    fun intent(item: CommitmentEntity): Intent {
        require(item.state == "ACCEPTED") { tr(R.string.choose_an_active_saved_event) }
        val start = requireNotNull(item.activityStartAt) { tr(R.string.choose_an_activity_start_first) }
        item.activityEndAt?.let { require(it > start) { tr(R.string.end_must_follow_start) } }
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
