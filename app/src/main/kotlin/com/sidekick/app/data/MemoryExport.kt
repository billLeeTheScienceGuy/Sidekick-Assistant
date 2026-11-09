package com.sidekick.app.data

import android.util.JsonWriter
import java.io.OutputStream

/** Explicit event export. Excludes temporary transcripts, sender IDs, voice profiles and Undo history. */
object MemoryExport {
    fun write(output: OutputStream, items: List<CommitmentEntity>, excerpts: Boolean, exportedAt: Long = System.currentTimeMillis()) {
        JsonWriter(output.writer(Charsets.UTF_8)).use { json ->
            json.setIndent("  ")
            json.beginObject().name("format").value("SideKick saved events").name("version").value(1)
            json.name("exportedAt").value(java.time.Instant.ofEpochMilli(exportedAt).toString())
            json.name("includesSavedExcerpts").value(excerpts)
            json.name("events").beginArray()
            for (item in items.sortedWith(compareBy({ it.capturedAt }, { it.id }))) {
                json.beginObject().name("id").value(item.id).name("title").value(item.title)
                json.name("state").value(item.state).name("timezone").value(item.zoneId)
                json.name("informational").value(item.informational)
                fun timestamp(name: String, value: Long?) {
                    json.name(name).value(value?.let { java.time.Instant.ofEpochMilli(it).toString() })
                }
                timestamp("capturedAt", item.capturedAt); timestamp("startAt", item.activityStartAt)
                timestamp("endAt", item.activityEndAt); timestamp("reminderAt", item.dueAt)
                timestamp("reminderDeliveredAt", item.deliveredAt)
                json.name("placeName").value(item.placeName).name("placeAddress").value(item.placeAddress)
                if (excerpts) json.name("savedExcerpt").value(item.evidence)
                json.endObject()
            }
            json.endArray().endObject()
        }
    }
}
