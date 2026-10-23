package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AppSettingsDialog(settings: GuidanceSettings, onClose: () -> Unit) {
    val context = LocalContext.current
    val voice = (context.applicationContext as SidekickApplication).voiceProfile
    val speechLanguage by settings.speechLanguage.collectAsState()
    val voiceReady by voice.ready.collectAsState()
    val voiceEnabled by voice.enabled.collectAsState()
    val listening by com.sidekick.app.audio.ListeningService.state.collectAsState()
    var deleteVoice by remember { mutableStateOf(false) }
    val mode by settings.mode.collectAsState()
    val informational by settings.includeInformational.collectAsState()
    val automatic by settings.automaticPlaces.collectAsState()
    val placeContext by settings.placeContext.collectAsState()
    val city by settings.city.collectAsState()
    val messages by settings.messageNotifications.collectAsState()
    val selectedApps by settings.messageApps.collectAsState()
    val sharing by settings.sharedMessages.collectAsState()
    var filter by remember { mutableStateOf("") }
    val apps = remember {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settings.setPlaceContext(PlaceContext.PHONE)
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Settings") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupStatus(settings)
            HorizontalDivider()
            Text("Speech language", style = MaterialTheme.typography.titleMedium)
            com.sidekick.core.SpeechLanguage.entries.forEach { language ->
                Row {
                    RadioButton(speechLanguage == language, { settings.setSpeechLanguage(language) },
                        enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speech-language-${language.code}"))
                    Text(language.label, Modifier.padding(top = 12.dp))
                }
            }
            Text("All speech models run locally. Automatic mode detects language per speech chunk; fast switches or mixed languages within one phrase may be inaccurate. Choose a language for more reliable recognition. Finish listening before changing it.")
            Text("Transcription keeps the spoken language. Activity/date detection currently understands English; non-English transcripts can be reviewed and copied, but do not yet have reliable event extraction.")
            HorizontalDivider()
            Text("My voice", style = MaterialTheme.typography.titleMedium)
            Text(if (voiceReady) "Local voice profile installed" else "No voice profile. Live speech stays unassigned and events are information-only.")
            Row {
                Checkbox(voiceEnabled, voice::setEnabled, enabled = voiceReady && !listening.recording && !listening.processing)
                Text("Match my voice for personal plans", Modifier.padding(top = 12.dp))
            }
            Text("Others’ events remain informational suggestions. Short phrases need a strong voice match and a recent confident longer phrase; otherwise they stay unassigned. Matching can be wrong; it does not prove consent.")
            Text("Voice model: WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0, metadata adapted by sherpa-onnx. Runtime: sherpa-onnx, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM"))) }) { Text("Voice model source and license") }
            TextButton(enabled = !listening.recording && !listening.processing, onClick = {
                context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java))
            }) { Text(if (voiceReady) "Replace my voice profile" else "Set up my voice") }
            if (voiceReady) TextButton(enabled = !listening.recording && !listening.processing, onClick = { deleteVoice = true }) { Text("Delete my voice profile") }
            HorizontalDivider()
            Text("Both modes suggest activity times. Unknown times never block saving; every suggestion needs approval.")
            Row {
                RadioButton(mode == GuidanceMode.PASSIVE, { settings.setMode(GuidanceMode.PASSIVE) }, modifier = Modifier.testTag("mode-passive"))
                Text("Passive: suggest without asking.", Modifier.padding(top = 12.dp))
            }
            Row {
                RadioButton(mode == GuidanceMode.ASK, { settings.setMode(GuidanceMode.ASK) }, modifier = Modifier.testTag("mode-ask"))
                Text("Ask about a missing end time when reviewing.", Modifier.padding(top = 12.dp))
            }
            Row {
                Checkbox(informational, settings::setIncludeInformational, modifier = Modifier.testTag("include-informational"))
                Text("Suggest events even if I’m not attending.", Modifier.padding(top = 12.dp))
            }
            Text("Information-only events do not count as calendar conflicts. You can mark an event as personal when reviewing it.")
            HorizontalDivider()
            Text("Places", style = MaterialTheme.typography.titleMedium)
            Row {
                Checkbox(automatic, settings::setAutomaticPlaces)
                Text("Suggest addresses automatically when reviewing a place.", Modifier.padding(top = 12.dp))
            }
            Text("Lookup can send the venue and city or approximate location to the phone’s geocoding provider. Confirm an address before saving. Maps opens only when tapped.")
            Row {
                RadioButton(placeContext == PlaceContext.CITY, { settings.setPlaceContext(PlaceContext.CITY) })
                Text("Use my chosen city", Modifier.padding(top = 12.dp))
            }
            OutlinedTextField(city, settings::setCity, label = { Text("City, state or country") }, placeholder = { Text("For example, Madison, Wisconsin") }, modifier = Modifier.testTag("place-city"))
            Row {
                RadioButton(placeContext == PlaceContext.PHONE, {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) settings.setPlaceContext(PlaceContext.PHONE)
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                })
                Text("Use the phone’s approximate location", Modifier.padding(top = 12.dp))
            }
            Text("Location is requested only for a place lookup while reviewing, never continuous tracking. The chosen city is a fallback if location is unavailable.")
            HorizontalDivider()
            Text("Incoming messages", style = MaterialTheme.typography.titleMedium)
            Row {
                Checkbox(sharing, settings::setSharedMessages, modifier = Modifier.testTag("shared-messages"))
                Text("Accept messages I share into SideKick", Modifier.padding(top = 12.dp))
            }
            Text("Share selected text from a messaging app, then confirm analysis in SideKick. Shared text is temporary.")
            Row {
                Checkbox(messages, settings::setMessageNotifications, modifier = Modifier.testTag("message-scanning"))
                Text("Suggest activities from new message notifications", Modifier.padding(top = 12.dp))
            }
            Text("Enable Android notification access and select messaging apps below. SideKick ignores other apps. This reads new visible notification text, not message history. Senders’ plans are information-only, never your promises. Text and unapproved suggestions remain temporary.")
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Manage Android notification access") }
            if (messages) {
                OutlinedTextField(filter, { filter = it }, label = { Text("Find a messaging app") })
                apps.filter { it.second.contains(filter, true) }.forEach { (name, label) ->
                    Row {
                        Checkbox(name in selectedApps, { settings.setMessageApp(name, it) })
                        Text(label, Modifier.padding(top = 12.dp))
                    }
                }
                if (selectedApps.isEmpty()) Text("No apps selected. Message scanning is inactive.")
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
    if (deleteVoice) AlertDialog(onDismissRequest = { deleteVoice = false }, title = { Text("Delete voice profile?") },
        text = { Text("This removes the saved voice signature from this phone. Saved events stay. You can enroll again later.") },
        confirmButton = { TextButton(onClick = { voice.delete(); deleteVoice = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteVoice = false }) { Text("Cancel") } })
}
