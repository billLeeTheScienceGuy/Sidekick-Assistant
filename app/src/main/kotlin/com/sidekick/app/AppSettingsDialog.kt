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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppSettingsDialog(settings: GuidanceSettings, onClose: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
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
    val diarization by settings.diarization.collectAsState()
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
    val apps = remember(page, messages) {
        if (page != "CONNECTIONS" || !messages) emptyList() else
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
        .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settings.setPlaceContext(PlaceContext.PHONE)
    }
    val appearance by settings.appearance.collectAsState()
    val design by settings.interfaceDesign.collectAsState()
    val textSize by settings.noteTextSize.collectAsState()
    val interfaceSize by settings.interfaceSize.collectAsState()
    val manualNotes by settings.manualNotes.collectAsState()
    val oneScreen by settings.oneScreenHome.collectAsState()
    Dialog(onDismissRequest = { if (page == null) onClose() else page = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.safeDrawingPadding().imePadding().widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(0.95f), shape = MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (page != null) TextButton(onClick = { page = null }, modifier = Modifier.testTag("settings-back")) { Text("Back") }
                    Text(settingsPages.firstOrNull { it.id == page }?.title?.substringBefore(" &") ?: "Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Close") }
                }
                HorizontalDivider()
                key(page) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            null -> {
                                QuietLabel("Choose what you want to change.")
                                settingsPages.forEach { section ->
                                    OutlinedCard(onClick = { page = section.id }, modifier = Modifier.fillMaxWidth().testTag("settings-page-${section.id}")) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(section.title, style = MaterialTheme.typography.titleMedium)
                                            QuietLabel(section.description)
                                        }
                                    }
                                }
                            }
                            "DISPLAY" -> {
                                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppearanceMode.entries.forEach { mode ->
                                        FilterChip(selected = mode == appearance, onClick = { settings.setAppearance(mode) },
                                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("appearance-${mode.name}"))
                                    }
                                }
                                Text("Design", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InterfaceDesign.entries.forEach { choice ->
                                        FilterChip(design == choice, { settings.setInterfaceDesign(choice) }, label = { Text(choice.label) }, modifier = Modifier.testTag("design-${choice.name}"))
                                    }
                                }
                                QuietLabel("Calm is teal. Classic is blue. Warm is amber.")
                                Text("Text size", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteTextSize.entries.forEach { choice ->
                                        FilterChip(textSize == choice, { settings.setNoteTextSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.testTag("text-size-${choice.name}"))
                                    }
                                }
                                Text("Buttons & spacing", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InterfaceSize.entries.forEach { choice ->
                                        FilterChip(interfaceSize == choice, { settings.setInterfaceSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.testTag("interface-size-${choice.name}"))
                                    }
                                }
                                QuietLabel("Text size changes words. Buttons & spacing changes controls. Both follow your phone settings.")
                                TextButton(onClick = settings::resetAppearance, modifier = Modifier.testTag("reset-appearance")) { Text("Reset appearance") }
                                HorizontalDivider()
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Type notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Switch(manualNotes, settings::setManualNotes, modifier = Modifier.testTag("manual-notes").controlLabel("Type notes"))
                                }
                                QuietLabel("Show a place to type notes or add plans. You can still edit plans when this is off.")
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Home layout", style = MaterialTheme.typography.titleMedium)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(oneScreen, { settings.setOneScreenHome(true) }, label = { Text("Simple") }, modifier = Modifier.testTag("home-simple"))
                                    FilterChip(!oneScreen, { settings.setOneScreenHome(false) }, label = { Text("Detailed") }, modifier = Modifier.testTag("home-detailed"))
                                }
                                QuietLabel("Simple shows listening and your next plan. Detailed adds text and tools.")
                                HorizontalDivider()
                            }
                            "LISTENING" -> {
                                val auto = speechLanguage == com.sidekick.core.SpeechLanguage.AUTO
                                SettingsToggle("Auto-detect language", "Switch languages as you speak.", auto, {
                                    settings.setAutomaticLanguage(it)
                                    voice.selectLanguage(settings.speechLanguage.value)
                                }, "auto-language", !listening.recording && !listening.processing)
                                if (auto) QuietLabel("Works across conversations. Short or mixed-language phrases can be wrong.")
                                else {
                                    var languageMenu by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick = { languageMenu = true }, enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("language-choice")) { Text(speechLanguage.label) }
                                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                                            com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { language ->
                                                DropdownMenuItem(text = { Text(language.label) }, onClick = {
                                                    settings.setSpeechLanguage(language); voice.selectLanguage(language); languageMenu = false
                                                }, modifier = Modifier.testTag("speech-language-${language.code}"))
                                            }
                                        }
                                    }
                                }
                                if (listening.recording || listening.processing) QuietLabel("Finish listening before changing the language.")
                                val skipIntro by settings.skipListeningIntro.collectAsState()
                                SettingsToggle("Show listening tips", "Show tips before you start listening.", !skipIntro, { settings.setSkipListeningIntro(!it) }, "start-reminder")
                                HorizontalDivider()
                                Text("My voice", style = MaterialTheme.typography.titleMedium)
                                val enrolledVoices by voice.enrolled.collectAsState()
                                QuietLabel(if (voiceReady) "Your voice is set up: ${enrolledVoices.keys.joinToString(", ") { it.label }}." else "Set up your voice so SideKick can recognize your plans.")
                                SettingsToggle("Recognize my voice", "Other people’s plans won’t become your commitments. Voice matching can be wrong.", voiceEnabled, voice::setEnabled, "voice-matching", voiceReady && !listening.recording && !listening.processing)
                                TextButton(enabled = !listening.recording && !listening.processing, onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)) }) { Text(if (voiceReady) "Update my voice profiles" else "Set up my voice") }
                            }
                            "PLANS" -> {
                                SettingsToggle("Save plans automatically", "Save plans and updates without asking. Undo in notifications. Off: review before saving.", handsOff, settings::setHandsOff, "hands-off")
                                HorizontalDivider()
                                Text("If a plan has no end time:")
                                Row {
                                    RadioButton(mode == GuidanceMode.PASSIVE, { settings.setMode(GuidanceMode.PASSIVE) }, modifier = Modifier.testTag("mode-passive").controlLabel("Leave it unknown"))
                                    Text("Leave it unknown", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                Row {
                                    RadioButton(mode == GuidanceMode.ASK, { settings.setMode(GuidanceMode.ASK) }, modifier = Modifier.testTag("mode-ask").controlLabel("Ask when I review it"))
                                    Text("Ask when I review it", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                Row {
                                    Checkbox(informational, settings::setIncludeInformational, modifier = Modifier.testTag("include-informational").controlLabel("Keep plans I’m not attending"))
                                    Text("Keep plans I’m not attending", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                QuietLabel("These plans don’t block your calendar. You can mark them as your own later.")
                                HorizontalDivider()
                                CalendarSyncSection()
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Reminder alerts") }
                            }
                            "CONNECTIONS" -> {
                                Text("Places", style = MaterialTheme.typography.titleMedium)
                                SettingsToggle("Find addresses", "Sends the place and city or nearby location to Android’s address service. You confirm before saving. Tap a place to open Maps.", automatic, settings::setAutomaticPlaces, "automatic-places")
                                Row {
                                    RadioButton(placeContext == PlaceContext.CITY, { settings.setPlaceContext(PlaceContext.CITY) }, modifier = Modifier.controlLabel("Use a city I choose"))
                                    Text("Use a city I choose", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                OutlinedTextField(city, settings::setCity, label = { Text("City or region") }, placeholder = { Text("For example, Madison, Wisconsin") }, modifier = Modifier.testTag("place-city"))
                                Row {
                                    RadioButton(placeContext == PlaceContext.PHONE, {
                                        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) settings.setPlaceContext(PlaceContext.PHONE)
                                        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }, modifier = Modifier.controlLabel("Use my nearby location"))
                                    Text("Use my nearby location", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                QuietLabel("Location is checked only when finding an address. No continuous tracking. Your chosen city is used if location is unavailable.")
                                HorizontalDivider()
                                Text("Messages", style = MaterialTheme.typography.titleMedium)
                                SettingsToggle("Read text I share", "Find plans in text shared from another app.", sharing, settings::setSharedMessages, "shared-messages")
                                SettingsToggle("Read new message notifications", "Reads new previews from apps you choose, not message history. Senders’ plans aren’t your promises.", messages, settings::setMessageNotifications, "message-scanning")
                                QuietLabel("Text is temporary, except quotes kept in saved plans.")
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Allow notification access") }
                                if (messages) {
                                    OutlinedTextField(filter, { filter = it }, label = { Text("Find a messaging app") })
                                    apps.filter { it.second.contains(filter, true) }.forEach { (name, label) ->
                                        Row {
                                            Checkbox(name in selectedApps, { settings.setMessageApp(name, it) }, modifier = Modifier.controlLabel("Read notifications from $label"))
                                            Text(label, Modifier.padding(start = 8.dp, top = 12.dp))
                                        }
                                    }
                                    if (selectedApps.isEmpty()) Text("Choose an app to start reading messages.")
                                }
                            }
                            "PRIVACY" -> {
                                MemoryPrivacySection()
                                PrivacyGuidanceSection(settings)
                            }
                            "ADVANCED" -> {
                                ModelPackSection(initiallyExpanded = false)
                                Text("Recognition quality", style = MaterialTheme.typography.titleMedium)
                                com.sidekick.core.SpeechQuality.entries.forEach { quality ->
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(speechQuality == quality, { settings.setSpeechQuality(quality) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.controlLabel("Speech quality: ${quality.label}"))
                                        Text(speechQualityName(quality), Modifier.weight(1f))
                                    }
                                }
                                QuietLabel("Recommended works with all supported languages. Faster uses less processing. Larger model may be slower and won’t always be more accurate. Speech stays on this phone.")
                                HorizontalDivider()
                                Text("Default assistant", style = MaterialTheme.typography.titleMedium)
                                Text(if (assistantSelected) "SideKick is your default assistant" else "SideKick is not your default assistant", modifier = Modifier.testTag("assistant-role-status"))
                                TextButton(onClick = { assistantPicker.launch(assistant.picker()) }, modifier = Modifier.testTag("assistant-picker")) { Text("Choose default assistant") }
                                Row {
                                    Switch(recoveryEnabled, assistant::setEnabled, modifier = Modifier.testTag("assistant-recovery").controlLabel("Resume listening after a restart"))
                                    Text("Resume listening after a restart", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                QuietLabel("Requires SideKick as your default assistant. Restarts a session you already started. Stop turns recovery off for that session. Android force-stop or removed permissions can prevent recovery. Voice setup never restarts itself.")
                                Text(recoveryStatus)
                                HorizontalDivider()
                                Text("Speaker options", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    Switch(diarization, settings::setDiarization, enabled = !listening.recording && !listening.processing,
                                    modifier = Modifier.testTag("diarization-setting").controlLabel("Label different speakers"))
                                    Text("Label different speakers", Modifier.padding(start = 8.dp, top = 12.dp))
                                }
                                QuietLabel("Labels speakers on this phone. Labels reset each session and don’t identify people or prove consent. Unclear or overlapping voices stay unknown. Turn off to reduce processing.")
                                com.sidekick.app.speaker.SpeakerModel.entries.forEach { candidate ->
                                    Row {
                                        RadioButton(speakerModel == candidate, { voice.selectModel(candidate) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speaker-model-${candidate.name}").controlLabel("Voice model: ${candidate.label}"))
                                        Text(candidate.label, Modifier.padding(start = 8.dp, top = 12.dp))
                                    }
                                }
                                SettingsDetails("About voice matching") {
                                    QuietLabel("Each voice model needs its own setup. Your previous setup is kept.")
                                    Text(if (voiceReady) "Voice ready · ${speechLanguage.label}" else "No voice setup for ${speechLanguage.label}. Set up your voice to recognize your own plans.")
                                    QuietLabel("Voice setup is saved separately for each language. Auto-detect checks all your enrolled language profiles. Start with a full sentence to help matching.")
                                    Row {
                                        Checkbox(voiceEnabled, voice::setEnabled, enabled = voiceReady && !listening.recording && !listening.processing, modifier = Modifier.controlLabel("Recognize my voice"))
                                        Text("Recognize my voice", Modifier.padding(start = 8.dp, top = 12.dp))
                                    }
                                    QuietLabel("Other voices stay as information. Brief phrases need a recent strong match; otherwise the speaker stays unknown. Matching can be wrong and does not prove consent.")
                                    Text(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "Voice model: 3D-Speaker CAM++ Chinese/English advanced, Apache 2.0. Runtime: sherpa-onnx, Apache 2.0." else "Voice model: WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0. Runtime: sherpa-onnx, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced" else "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM"))) }) { Text("Model details & license") }
                                }
                                TextButton(enabled = !listening.recording && !listening.processing, onClick = {
                                    context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java))
                                }) { Text(if (voiceReady) "Update my voice profiles" else "Set up my voice") }
                                TextButton(enabled = !listening.recording && !listening.processing, onClick = { deleteVoice = true }) { Text("Delete my voice setup") }
                                HorizontalDivider()
                                var checks by remember { mutableStateOf(false) }
                                TextButton(onClick = { checks = !checks }, modifier = Modifier.testTag("phone-checks")) { Text(if (checks) "Hide phone checks" else "Check phone setup") }
                                if (checks) SetupStatus(settings)
                            }
                        }
                    } }
            }
        }
    }
    if (deleteVoice) AlertDialog(onDismissRequest = { deleteVoice = false }, title = { Text("Delete voice setup?") },
    text = { Text("Deletes your voice setup for every language and model. Saved plans stay. You can set up your voice again.") },
    confirmButton = { TextButton(onClick = { voice.delete(); deleteVoice = false }) { Text("Delete") } },
    dismissButton = { TextButton(onClick = { deleteVoice = false }) { Text("Cancel") } })
}

private data class SettingsSection(val id: String, val title: String, val description: String)
private val settingsPages = listOf(
SettingsSection("DISPLAY", "Display", "Home layout, text size and colors"),
SettingsSection("LISTENING", "Listening", "Language, my voice and listening tips"),
SettingsSection("PLANS", "Plans & reminders", "Saving plans, end times and calendar"),
SettingsSection("CONNECTIONS", "Messages & places", "Messages, location and maps"),
SettingsSection("PRIVACY", "Privacy & saved data", "Saved plans, copies and privacy"),
SettingsSection("ADVANCED", "Advanced & help", "Speech options and phone checks")
)

@Composable
internal fun SettingsToggle(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            QuietLabel(summary)
        }
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.testTag(tag).controlLabel(title))
    }
}

internal fun speechQualityName(quality: com.sidekick.core.SpeechQuality): String = when (quality) {
    com.sidekick.core.SpeechQuality.ADAPTIVE -> "Recommended"
    com.sidekick.core.SpeechQuality.FAST -> "Faster"
    com.sidekick.core.SpeechQuality.ACCURATE -> "Larger model"
}

@Composable
internal fun SettingsDetails(label: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else label) }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
