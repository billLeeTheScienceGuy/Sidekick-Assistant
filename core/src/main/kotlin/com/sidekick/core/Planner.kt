package com.sidekick.core

enum class PlannerView { REVIEW, REMINDERS, UNSCHEDULED, HISTORY, ALL }
data class PlannerEntry(val id: String, val state: String, val dueAt: Long?, val capturedAt: Long)

object Planner {
    fun select(items: List<PlannerEntry>, view: PlannerView): List<PlannerEntry> {
        fun group(item: PlannerEntry) = when {
            item.state == "PROPOSED" -> 0
            item.state == "ACCEPTED" && item.dueAt != null -> 1
            item.state == "ACCEPTED" -> 2
            else -> 3
        }
        return items.filter { item -> when (view) {
            PlannerView.REVIEW -> item.state == "PROPOSED"
            PlannerView.REMINDERS -> item.state == "ACCEPTED" && item.dueAt != null
            PlannerView.UNSCHEDULED -> item.state == "ACCEPTED" && item.dueAt == null
            PlannerView.HISTORY -> item.state in setOf("DONE", "DISMISSED")
            PlannerView.ALL -> true
        } }.sortedWith(compareBy<PlannerEntry> { group(it) }
            .thenBy { if (group(it) == 1) it.dueAt else -it.capturedAt }
            .thenBy { it.id })
    }

    /** Same reminder minute, not a claim that the underlying activities overlap. */
    fun sameReminderMinute(items: List<PlannerEntry>, exceptId: String, dueAt: Long): List<String> =
        items.filter { it.id != exceptId && it.state == "ACCEPTED" && it.dueAt != null &&
            Math.floorDiv(it.dueAt, 60_000) == Math.floorDiv(dueAt, 60_000) }.map { it.id }
}
