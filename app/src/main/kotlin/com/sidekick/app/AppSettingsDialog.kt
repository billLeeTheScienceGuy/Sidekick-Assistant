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
    val assistant = (context.applicationContext as SidekickApplication).assistant
    val assistantSelected by assistant.selected.collectAsState()
    val recoveryEnabled by assistant.enabled.collectAsState()
    val recoveryStatus by assistant.status.collectAsState()
    val assistantPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { assistant.refresh() }
    val speechLanguage by settings.speechLanguage.collectAsState()
    val speechQuality by settings.speechQuality.collectAsState()
    val voiceReady by voice.ready.collectAsState()
    val speakerModel by voice.model.collectAsState()
    val voiceEnabled by voice.enabled.collectAsState()
    val listening by com.sidekick.app.audio.ListeningService.state.collectAsState()
    var deleteVoice by remember { mutableStateOf(false) }
    val mode by settings.mode.collectAsState()
    val handsOff by settings.handsOffMode.collectAsState()
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
            Text("Default assistant", style = MaterialTheme.typography.titleMedium)
            Text(if (assistantSelected) "SideKick is your default assistant" else "SideKick is not your default assistant", modifier = Modifier.testTag("assistant-role-status"))
            TextButton(onClick = { assistantPicker.launch(assistant.picker()) }, modifier = Modifier.testTag("assistant-picker")) { Text("Choose default assistant") }
            Row {
                Switch(recoveryEnabled, assistant::setEnabled, modifier = Modifier.testTag("assistant-recovery"))
                Text("Resume enabled listening after system restart", Modifier.padding(top = 12.dp))
            }
            Text("Requires SideKick as default assistant. Start listening once; recovery remains enabled across reboots until you press Stop. Selecting SideKick alone does not start the microphone. Voice enrollment never resumes automatically. Force-stop and revoked permissions still apply. No screen contents or screenshots are requested.")
            Text(recoveryStatus)
            HorizontalDivider()
            Row {
                Switch(handsOff, settings::setHandsOff, modifier = Modifier.testTag("hands-off"))
                Text("Hands-off: save events and apply corrections automatically", Modifier.padding(top = 12.dp))
            }
            Text("No approval prompts in hands-off mode. Undo automatic changes from notifications. Personal plans with clear times get reminders; other voices stay information-only. Uncertain addresses stay as spoken. Review mode is available by switching this off.")
            HorizontalDivider()
            Text("Speech language", style = MaterialTheme.typography.titleMedium)
            com.sidekick.core.SpeechQuality.entries.forEach { quality ->
                Row {
                    RadioButton(speechQuality == quality, { settings.setSpeechQuality(quality) }, enabled = !listening.recording && !listening.processing)
                    Text(quality.label, Modifier.padding(top = 12.dp))
                }
            }
            Text("Base uses more memory and processing for a possible accuracy gain. All recognition runs offline. Finish listening before switching.")
            Text("Adaptive uses SenseVoice for Korean/Chinese, Omnilingual for Arabic and longer French/German speech, and Whisper for the remaining paths. Short French/Russian phrases use Whisper base when the queue is clear. Arabic was much less accurate in the explicit Whisper modes in our fixtures. Models run sequentially on this phone.")
            Text("SenseVoiceSmall and Omnilingual weights: Apache 2.0. Whisper weights: MIT. All recognition is offline; no raw audio is saved.")
            com.sidekick.core.SpeechLanguage.entries.forEach { language ->
                Row {
                    RadioButton(speechLanguage == language, { voice.selectLanguage(language); settings.setSpeechLanguage(language) },
                        enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speech-language-${language.code}"))
                    Text(language.label, Modifier.padding(top = 12.dp))
                }
            }
            Text("All speech models run locally. Automatic mode detects language per speech chunk; fast switches or mixed languages within one phrase may be inaccurate. Choose a language for more reliable recognition. Finish listening before changing it.")
            Text("Transcription keeps the spoken language. Basic event/date rules support English, Korean, Chinese, French, German, Russian and Modern Standard Arabic. Dialects and rapid language switches may be less reliable. Ambiguous phrases stay unknown.")
            HorizontalDivider()
            Text("My voice", style = MaterialTheme.typography.titleMedium)
            com.sidekick.app.speaker.SpeakerModel.entries.forEach { candidate ->
                Row {
                    RadioButton(speakerModel == candidate, { voice.selectModel(candidate) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speaker-model-${candidate.name}"))
                    Text(candidate.label, Modifier.padding(top = 12.dp))
                }
            }
            Text("Each model needs its own voice setup. Switching keeps your previous profile so you can switch back.")
            Text(if (voiceReady) "Local voice profile installed · ${speechLanguage.label}" else "No voice profile for ${speechLanguage.label}. Set up your voice in this language; live speech stays information-only until it matches.")
            Text("Each selected language keeps its own voice signature, so Korean setup preserves your English profile. Automatic mode uses the general English/automatic profile. Start a session with a longer natural phrase; short phrases still need a recent confident match.")
            Row {
                Checkbox(voiceEnabled, voice::setEnabled, enabled = voiceReady && !listening.recording && !listening.processing)
                Text("Match my voice for personal plans", Modifier.padding(top = 12.dp))
            }
            Text("Others’ events remain informational suggestions. Quiet edges are excluded from voice matching. Short phrases need a strong voice match and a recent confident longer phrase within one minute; otherwise they stay unassigned. Matching can be wrong; it does not prove consent.")
            Text(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "Voice model: 3D-Speaker CAM++ Chinese/English advanced, Apache 2.0. Runtime: sherpa-onnx, Apache 2.0." else "Voice model: WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0. Runtime: sherpa-onnx, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced" else "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM"))) }) { Text("Voice model source and license") }
            TextButton(enabled = !listening.recording && !listening.processing, onClick = {
                context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java))
            }) { Text(if (voiceReady) "Replace my voice profile" else "Set up my voice") }
            TextButton(enabled = !listening.recording && !listening.processing, onClick = { deleteVoice = true }) { Text("Delete all my voice profiles") }
            HorizontalDivider()
            Text("Unknown times never block saving. These guidance options apply when you choose to edit an item; hands-off mode does not ask questions.")
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
        text = { Text("This removes voice signatures for all languages and both models from this phone. Saved events stay. You can enroll again later.") },
        confirmButton = { TextButton(onClick = { voice.delete(); deleteVoice = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteVoice = false }) { Text("Cancel") } })
}
