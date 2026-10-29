package com.sidekick.app.data

import org.json.JSONObject

/** Event metadata only; never contains audio or the full session transcript. */
object MemorySnapshot {
    fun matchesForUndo(current: CommitmentEntity, expected: CommitmentEntity) =
        current.copy(deliveredAt = expected.deliveredAt, reviewNote = expected.reviewNote) == expected
    fun encode(item: CommitmentEntity): String = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("evidence", item.evidence); put("capturedAt", item.capturedAt)
        put("zoneId", item.zoneId); put("reviewNote", item.reviewNote); put("state", item.state)
        put("dueAt", item.dueAt); put("deliveredAt", item.deliveredAt); put("activityStartAt", item.activityStartAt)
        put("activityEndAt", item.activityEndAt); put("informational", item.informational)
        put("placeName", item.placeName); put("placeAddress", item.placeAddress); put("sourceKey", item.sourceKey)
    }.toString()
    fun decode(value: String): CommitmentEntity = JSONObject(value).run {
        fun time(key: String) = if (isNull(key)) null else getLong(key)
        fun optional(key: String) = if (isNull(key)) null else getString(key)
        CommitmentEntity(getString("id"), getString("title"), getString("evidence"), getLong("capturedAt"),
            getString("zoneId"), getString("reviewNote"), getString("state"), time("dueAt"), time("deliveredAt"),
            time("activityStartAt"), time("activityEndAt"), getBoolean("informational"), optional("placeName"), optional("placeAddress"), optional("sourceKey"))
    }
}
