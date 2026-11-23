package com.sidekick.app

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Small dismissible card shown after a crash: Send opens the share sheet, Don't send deletes the report. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrashReportCard() {
    val context = LocalContext.current
    val settings = (context.applicationContext as SidekickApplication).guidance
    val on by settings.crashReports.collectAsState()
    var pending by remember { mutableStateOf(if (on) CrashReports.pending(context) else null) }
    val report = pending
    if (!on || report == null) return
    OutlinedCard(Modifier.fillMaxWidth().testTag("crash-report-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.crash_card_title), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.crash_card_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, tr(R.string.crash_share_subject)).putExtra(Intent.EXTRA_TEXT, CrashReports.text(report))
                    val chooser = Intent.createChooser(share, tr(R.string.crash_share_title))
                    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(chooser) }
                    CrashReports.markOffered(context, report); pending = null
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("crash-send")) { Text(tr(R.string.crash_send)) }
                TextButton(onClick = { CrashReports.delete(context, report); pending = null },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("crash-dont-send")) { Text(tr(R.string.crash_dont_send)) }
            }
        }
    }
}

/** Settings → Privacy: the on/off switch and a list of saved reports. */
@Composable
internal fun CrashReportSettings(settings: GuidanceSettings) {
    val on by settings.crashReports.collectAsState()
    val context = LocalContext.current
    var viewing by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(emptyList<CrashReports.Report>()) }
    SettingsToggle(tr(R.string.crash_toggle_title), tr(R.string.crash_toggle_note), on, settings::setCrashReports, "crash-reports-toggle")
    TextButton(onClick = { reports = CrashReports.list(context); viewing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("crash-reports-view")) {
        Text(tr(R.string.crash_view))
    }
    if (viewing) AlertDialog(onDismissRequest = { viewing = false }, modifier = Modifier.testTag("crash-reports-dialog"),
        title = { Text(tr(R.string.crash_view)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(R.string.crash_view_note), style = MaterialTheme.typography.bodyMedium)
                if (reports.isEmpty()) Text(tr(R.string.crash_none))
                reports.forEach { Text(CrashReports.text(it), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { viewing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.close)) } },
        dismissButton = { if (reports.isNotEmpty()) TextButton(onClick = { CrashReports.deleteAll(context); reports = emptyList() },
            modifier = Modifier.heightIn(min = 48.dp).testTag("crash-reports-delete-all")) { Text(tr(R.string.crash_delete_all)) } })
}
