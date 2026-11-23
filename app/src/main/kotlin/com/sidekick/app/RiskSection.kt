package com.sidekick.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun RiskSection() {
    val context = LocalContext.current
    val risk = (context.applicationContext as SidekickApplication).risk
    val alerts by risk.alerts.collectAsState()
    if (alerts.isNotEmpty()) Card(Modifier.fillMaxWidth().testTag("risk-advisories")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.pause_check), style = MaterialTheme.typography.titleMedium)
            QuietLabel(tr(R.string.based_on_common_warning_signs_can))
            alerts.forEach { alert ->
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.explanation)
                TextButton(onClick = { risk.dismiss(alert.id) }, modifier = Modifier.testTag("dismiss-risk-${alert.id}")) { Text(tr(R.string.dismiss)) }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://consumer.ftc.gov/scams"))) }) { Text(tr(R.string.ftc_scam_information)) }
        }
    }
}
