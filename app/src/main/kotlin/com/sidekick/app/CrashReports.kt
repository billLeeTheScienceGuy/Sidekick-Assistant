package com.sidekick.app

import android.content.Context
import android.os.Build
import com.sidekick.core.CrashReport
import java.io.File

/**
 * Optional, local-only crash reports. A report is a small text file in the app's private storage (no network, no upload).
 * It has the time, versions, device model, the exception type and its stack (class/method/line), never the exception message
 * or anything the user said. The user can send it from the share sheet, or delete it.
 */
object CrashReports {
    internal const val PREFERENCE = "offer_crash_reports"
    private const val STATE = "crash_report_state"
    private const val OFFERED = "last_offered"

    /** The screen or feature that is open, set cheaply by the screens. A plain label, never user text. */
    @Volatile var screen: String? = null

    class Report(val file: File, val timeMs: Long)

    private fun directory(context: Context) = File(context.noBackupFilesDir, "crash-reports")
    fun enabled(context: Context): Boolean =
        runCatching { context.getSharedPreferences("guidance", Context.MODE_PRIVATE).getBoolean(PREFERENCE, true) }.getOrDefault(true)

    /** Keeps the previous default handler and calls it after saving, so Android still shows its usual crash behavior. */
    fun install(app: android.app.Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { if (enabled(app)) write(app, error, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
        runCatching { prune(app, System.currentTimeMillis()) }
    }

    /** Writes one report. Also called directly by tests, which must not crash the test process. */
    fun write(context: Context, error: Throwable, nowMs: Long): File? {
        if (!enabled(context)) return null
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()})"
        }.getOrDefault("unknown")
        val text = CrashReport.build(nowMs, version, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "${Build.MANUFACTURER} ${Build.MODEL}", screen ?: describeActivity(), error)
        val dir = directory(context).apply { mkdirs() }
        val file = File(dir, "crash-$nowMs.txt")
        file.writeText(text)
        prune(context, nowMs)
        return file
    }

    private fun describeActivity(): String? = if (com.sidekick.app.audio.ListeningService.state.value.recording) "Listening" else null

    fun list(context: Context): List<Report> = directory(context).listFiles().orEmpty().mapNotNull { f ->
        f.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull()?.let { Report(f, it) }
    }.sortedByDescending { it.timeMs }

    fun prune(context: Context, nowMs: Long) {
        val reports = list(context)
        CrashReport.toDelete(reports.map { it.file.name to it.timeMs }, nowMs).forEach { name -> File(directory(context), name).delete() }
    }

    /** The newest report the user has not been asked about yet. */
    fun pending(context: Context): Report? {
        val offered = context.getSharedPreferences(STATE, Context.MODE_PRIVATE).getLong(OFFERED, 0L)
        return list(context).firstOrNull { it.timeMs > offered }
    }

    fun markOffered(context: Context, report: Report) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit().putLong(OFFERED, report.timeMs).apply()
    }

    fun text(report: Report): String = runCatching { report.file.readText() }.getOrDefault("")
    fun delete(context: Context, report: Report) { markOffered(context, report); report.file.delete() }
    fun deleteAll(context: Context) { directory(context).listFiles().orEmpty().forEach { it.delete() } }
}
