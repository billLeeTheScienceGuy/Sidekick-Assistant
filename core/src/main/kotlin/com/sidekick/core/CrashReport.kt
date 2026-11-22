package com.sidekick.core

import java.time.Instant

/**
 * Text of a local crash report. It holds only technical facts: the time, versions, device model, the kind of exception
 * and where it happened (class, method, line). The exception's message is never included, because messages can contain
 * what the user said or typed. The report stays on the phone; the user decides whether to send it.
 */
object CrashReport {
    const val MAX_REPORTS = 5
    const val MAX_AGE_MS = 30L * 86_400_000L
    private const val MAX_FRAMES = 30
    private const val MAX_CAUSES = 3
    private const val MAX_CAUSE_FRAMES = 10
    private val unsafe = Regex("[^A-Za-z0-9 ._:/+()\$<>-]")

    /** Keeps only characters that cannot carry a sentence or a name: short, plain labels. */
    fun clean(value: String?, max: Int = 60): String = (value ?: "").replace(unsafe, "").trim().take(max).ifBlank { "unknown" }

    fun build(timeMs: Long, appVersion: String, androidVersion: String, device: String, screen: String?, error: Throwable): String {
        val out = StringBuilder()
        out.append("SideKick crash report\n")
        out.append("Time: ").append(Instant.ofEpochMilli(timeMs)).append('\n')
        out.append("App version: ").append(clean(appVersion)).append('\n')
        out.append("Android: ").append(clean(androidVersion)).append('\n')
        out.append("Device: ").append(clean(device)).append('\n')
        if (!screen.isNullOrBlank()) out.append("Screen: ").append(clean(screen)).append('\n')
        out.append("Exception: ").append(clean(error.javaClass.name, 120)).append('\n')
        frames(out, error, MAX_FRAMES)
        var cause = error.cause; var depth = 0
        while (cause != null && cause !== error && depth < MAX_CAUSES) {
            out.append("Caused by: ").append(clean(cause.javaClass.name, 120)).append('\n')
            frames(out, cause, MAX_CAUSE_FRAMES)
            cause = cause.cause; depth++
        }
        out.append("No messages, conversations, plans, names, voice data or locations are included.\n")
        return out.toString()
    }

    private fun frames(out: StringBuilder, error: Throwable, limit: Int) {
        error.stackTrace.take(limit).forEach { f ->
            out.append("  at ").append(clean(f.className, 120)).append('.').append(clean(f.methodName, 60))
                .append('(').append(clean(f.fileName, 60)).append(':').append(f.lineNumber).append(")\n")
        }
    }

    /** Names (with file ages) to delete: older than 30 days, or beyond the five newest. */
    fun toDelete(reports: List<Pair<String, Long>>, nowMs: Long): List<String> {
        val fresh = reports.filter { nowMs - it.second <= MAX_AGE_MS }.sortedByDescending { it.second }
        val keep = fresh.take(MAX_REPORTS).map { it.first }.toSet()
        return reports.map { it.first }.filter { it !in keep }
    }
}
