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
    AlertDialog(onDismissRequest = onCancel, title = { Text(tr(R.string.start_listening_2)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.sidekick_follows_the_recording_rules_of))
            Text(tr(R.string.speech_becomes_text_on_this_phone))
            QuietLabel(if (handsOff) tr(R.string.plans_save_automatically_you_can_undo_2) else tr(R.string.review_plans_before_saving_them))
            if (recovery) QuietLabel(tr(R.string.listening_resumes_after_a_restart_until))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(skip, { skip = it }, modifier = Modifier.testTag("skip-listening-intro").controlLabel(tr(R.string.don_t_show_this_again)))
                Text(tr(R.string.don_t_show_this_again), Modifier.weight(1f))
            }
            TextButton(onClick = { details = !details }, modifier = Modifier.testTag("listening-details")) { Text(if (details) tr(R.string.less_detail) else tr(R.string.more_details)) }
            if (details) QuietLabel(tr(R.string.stop_listening_if_someone_doesn_t))
        }
    }, confirmButton = {
        TextButton(onClick = { settings.setSkipListeningIntro(skip); onStart() }, modifier = Modifier.testTag("confirm-listening")) { Text(tr(R.string.start_listening)) }
    }, dismissButton = { TextButton(onClick = onCancel) { Text(tr(R.string.cancel)) } })
}
