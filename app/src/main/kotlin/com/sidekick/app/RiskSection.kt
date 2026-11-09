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
            Text("Pause & verify", style = MaterialTheme.typography.titleMedium)
            Text("Local English warning-sign rules can miss risks or flag legitimate requests. These advisories do not identify a person as a scammer.")
            alerts.forEach { alert ->
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.explanation)
                TextButton(onClick = { risk.dismiss(alert.id) }, modifier = Modifier.testTag("dismiss-risk-${alert.id}")) { Text("Dismiss") }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://consumer.ftc.gov/scams"))) }) { Text("FTC scam information") }
        }
    }
}
