package com.sidekick.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ListeningStartDialog(settings: GuidanceSettings, handsOff: Boolean, recovery: Boolean, onCancel: () -> Unit, onStart: () -> Unit) {
    var skip by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Start listening?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make sure everyone agrees.")
            Text("SideKick transcribes on this phone and discards the audio. Listening continues with the screen off.")
            QuietLabel(if (handsOff) "Plans save automatically. You can undo changes." else "Review plans before saving them.")
            if (recovery) QuietLabel("This session can resume after a phone restart until you stop it.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(skip, { skip = it }, modifier = Modifier.testTag("skip-listening-intro").controlLabel("Don’t show this again"))
                Text("Don’t show this again", Modifier.weight(1f))
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.testTag("listening-details")) { Text(if (details) "Less detail" else "More details") }
            if (details) QuietLabel("Use the notification to stop listening. Stop if someone joins without agreeing or withdraws consent. Voice matching does not verify consent. Recent text is temporary; saved plans remain after listening stops. Restore this reminder in Settings → Listening.")
        }
    }, confirmButton = {
        TextButton(onClick = { settings.setSkipListeningIntro(skip); onStart() }, modifier = Modifier.testTag("confirm-listening")) { Text("Start listening") }
    }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
