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
            Text("SideKick follows the recording rules of your state. Where everyone must agree, or if your location is unknown, others' words are kept only after you announce note-taking.")
            Text("Speech becomes text on this phone; audio is never saved. Works with the screen off.")
            QuietLabel(if (handsOff) "Plans save automatically. You can undo changes." else "Review plans before saving them.")
            if (recovery) QuietLabel("Listening resumes after a restart until you stop it.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(skip, { skip = it }, modifier = Modifier.testTag("skip-listening-intro").controlLabel("Don’t show this again"))
                Text("Don’t show this again", Modifier.weight(1f))
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.testTag("listening-details")) { Text(if (details) "Less detail" else "More details") }
            if (details) QuietLabel("Stop listening if someone doesn’t agree. Voice matching does not verify consent. Turn this reminder back on in Settings → Listening.")
        }
    }, confirmButton = {
        TextButton(onClick = { settings.setSkipListeningIntro(skip); onStart() }, modifier = Modifier.testTag("confirm-listening")) { Text("Start listening") }
    }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
