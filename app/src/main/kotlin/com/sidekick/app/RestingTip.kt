package com.sidekick.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningState

/** After this much continuous listening, suggest "Pause when the phone is resting" once. */
internal const val RESTING_TIP_AFTER_SECONDS = 3 * 60 * 60

/**
 * One-time suggestion: after 3 hours of listening with "Pause when the phone is resting" off, offer to turn it on.
 * Shown once; either answer hides it for good. Nothing changes unless the user taps Turn on.
 */
@Composable
internal fun RestingTip(listening: ListeningState) {
    val app = LocalContext.current.applicationContext as SidekickApplication
    val done by app.guidance.restingTipDone.collectAsState()
    val on by app.guidance.pauseWhenResting.collectAsState()
    if (done || on || !listening.recording || listening.seconds < RESTING_TIP_AFTER_SECONDS) return
    OutlinedCard(Modifier.fillMaxWidth().testTag("resting-tip")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.save_battery_overnight), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.after_30_quiet_minutes_untouched_sidekick), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { app.guidance.setPauseWhenResting(true); app.guidance.setRestingTipDone() },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("resting-tip-on")) { Text(tr(R.string.turn_on)) }
                TextButton(onClick = { app.guidance.setRestingTipDone() }, modifier = Modifier.heightIn(min = 48.dp).testTag("resting-tip-later")) { Text(tr(R.string.not_now)) }
            }
        }
    }
}
