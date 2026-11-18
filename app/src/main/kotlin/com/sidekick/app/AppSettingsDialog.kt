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
        if (page != "PLANS" || !messages) emptyList() else
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
                    if (page != null) TextButton(onClick = { page = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("settings-back")) { Text("Back") }
                    Text(settingsPages.firstOrNull { it.id == page }?.title ?: "Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
                }
                HorizontalDivider()
                key(page) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            null -> {
                                // Screen choices first: they matter most to people who find the phone hard to read.
                                Text("Screen", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(oneScreen, { settings.setOneScreenHome(true) }, label = { Text("Simple (recommended)") }, modifier = Modifier.heightIn(min = 48.dp).testTag("home-simple"))
                                    FilterChip(!oneScreen, { settings.setOneScreenHome(false) }, label = { Text("Detailed") }, modifier = Modifier.heightIn(min = 48.dp).testTag("home-detailed"))
                                }
                                QuietLabel("Simple shows a large Start button and your next plans. Detailed shows every tool.")
                                Text("Text size", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteTextSize.entries.forEach { choice ->
                                        FilterChip(textSize == choice, { settings.setNoteTextSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("text-size-${choice.name}"))
                                    }
                                }
                                QuietLabel("Also follows your phone’s font size.")
                                SettingsDetails("More screen options", "screen-options") {
                                    Text("Light or dark", style = MaterialTheme.typography.titleMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppearanceMode.entries.forEach { mode ->
                                            FilterChip(selected = mode == appearance, onClick = { settings.setAppearance(mode) },
                                                label = { Text(when (mode) { AppearanceMode.SYSTEM -> "Same as phone"; AppearanceMode.LIGHT -> "Light"; AppearanceMode.DARK -> "Dark" }) },
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("appearance-${mode.name}"))
                                        }
                                    }
                                    Text("Colors", style = MaterialTheme.typography.titleMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InterfaceDesign.entries.forEach { choice ->
                                            FilterChip(design == choice, { settings.setInterfaceDesign(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("design-${choice.name}"))
                                        }
                                    }
                                    QuietLabel("Calm is teal. Classic is blue. Warm is amber.")
                                    Text("Buttons & spacing", style = MaterialTheme.typography.titleMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InterfaceSize.entries.forEach { choice ->
                                            FilterChip(interfaceSize == choice, { settings.setInterfaceSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("interface-size-${choice.name}"))
                                        }
                                    }
                                    QuietLabel("Text size changes words. Buttons & spacing changes controls.")
                                    TextButton(onClick = settings::resetAppearance, modifier = Modifier.heightIn(min = 48.dp).testTag("reset-appearance")) { Text("Reset screen choices") }
                                    SettingsToggle("Type notes", "Show a place to type notes or add plans. You can still change plans when this is off.", manualNotes, settings::setManualNotes, "manual-notes")
                                }
                                HorizontalDivider()
                                QuietLabel("Choose what you want to change.")
                                settingsPages.forEach { section ->
                                    OutlinedCard(onClick = { page = section.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("settings-page-${section.id}")) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(section.title, style = MaterialTheme.typography.titleLarge)
                                            Text(section.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            "LISTENING" -> {
                                val auto = speechLanguage == com.sidekick.core.SpeechLanguage.AUTO
                                SettingsToggle("Auto-detect language", "Switch languages as you speak.", auto, {
                                    settings.setAutomaticLanguage(it)
                                    voice.selectLanguage(settings.speechLanguage.value)
                                }, "auto-language", !listening.recording && !listening.processing)
                                if (auto) {
                                    QuietLabel("Works across conversations. Short or mixed-language phrases can be wrong.")
                                    val detect by settings.detectLanguages.collectAsState()
                                    Text("Languages to detect", style = MaterialTheme.typography.titleSmall)
                                    QuietLabel("Fewer languages: fewer wrong-language guesses. One language: no guessing at all.")
                                    com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { language ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(language in detect, { on -> settings.setDetectLanguages(if (on) detect + language else detect - language) },
                                                enabled = !listening.recording && !listening.processing && !(language in detect && detect.size == 1),
                                                modifier = Modifier.testTag("detect-${language.code}").controlLabel("Detect ${language.label}"))
                                            Text(language.label, Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                                else {
                                    var languageMenu by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick = { languageMenu = true }, enabled = !listening.recording && !listening.processing, modifier = Modifier.heightIn(min = 48.dp).testTag("language-choice")) { Text(speechLanguage.label) }
                                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                                            com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { language ->
                                                val st = remember(languageMenu, speechQuality) { com.sidekick.app.speech.ModelPacks.status(context, language, speechQuality) }
                                                DropdownMenuItem(text = { Text(language.label + when {
                                                    st.ready -> " · ready"
                                                    st.download.isEmpty() -> " · turn on files"
                                                    else -> " · needs ${(st.downloadBytes + 500_000) / 1_000_000} MB" + if (st.readyInFaster) " (works in Faster)" else ""
                                                }) }, onClick = {
                                                    settings.setSpeechLanguage(language); voice.selectLanguage(language); languageMenu = false
                                                }, modifier = Modifier.testTag("speech-language-${language.code}"))
                                            }
                                        }
                                    }
                                }
                                if (listening.recording || listening.processing) QuietLabel("Stop listening before changing the language.")
                                val skipIntro by settings.skipListeningIntro.collectAsState()
                                SettingsToggle("Show listening tips", "Recommended. Show a short reminder before you start listening.", !skipIntro, { settings.setSkipListeningIntro(!it) }, "start-reminder")
                                HorizontalDivider()
                                Text("Speech recognition", style = MaterialTheme.typography.titleMedium)
                                com.sidekick.core.SpeechQuality.entries.forEach { quality ->
                                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(speechQuality == quality, { settings.setSpeechQuality(quality) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.controlLabel("Speech quality: ${quality.label}"))
                                        Text(speechQualityName(quality), Modifier.weight(1f))
                                    }
                                }
                                QuietLabel("Recommended works with every supported language. Faster uses less battery. Larger may be slower and isn’t always more accurate. Speech stays on this phone.")
                                ModelPackSection(initiallyExpanded = false)
                                HorizontalDivider()
                                Text("Keep listening after a restart", style = MaterialTheme.typography.titleMedium)
                                Text(if (assistantSelected) "SideKick is your default assistant" else "SideKick is not your default assistant", modifier = Modifier.testTag("assistant-role-status"))
                                TextButton(onClick = { assistantPicker.launch(assistant.picker()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("assistant-picker")) { Text("Choose default assistant") }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Switch(recoveryEnabled, assistant::setEnabled, modifier = Modifier.testTag("assistant-recovery").controlLabel("Resume listening after a restart"))
                                    Text("Resume listening after a restart", Modifier.padding(start = 8.dp))
                                }
                                QuietLabel("Needs SideKick as your default assistant. Restarts listening you already started. Tapping Stop turns this off until you start again. Android force-stop or removed permissions can prevent it. Voice setup never restarts itself.")
                                Text(recoveryStatus)
                                HorizontalDivider()
                                var checks by remember { mutableStateOf(false) }
                                TextButton(onClick = { checks = !checks }, modifier = Modifier.heightIn(min = 48.dp).testTag("phone-checks")) { Text(if (checks) "Hide phone checks" else "Check phone setup") }
                                if (checks) SetupStatus(settings)
                            }
                            "VOICE" -> {
                                val enrolledVoices by voice.enrolled.collectAsState()
                                Text(if (voiceReady) "Your voice is set up: ${enrolledVoices.keys.joinToString(", ") { it.label }}." else "Set up your voice so SideKick knows which plans are yours.",
                                    style = MaterialTheme.typography.bodyLarge)
                                val voiceConsentNeeded by voice.consentNeeded.collectAsState()
                                if (voiceConsentNeeded) {
                                    Text("Voice consent needed. Voice matching is off until you agree.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("voice-consent-needed"))
                                    TextButton(onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)
                                        .putExtra(com.sidekick.app.speaker.VoiceEnrollmentActivity.CONSENT_ONLY, true)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review voice consent") }
                                }
                                Button(enabled = !listening.recording && !listening.processing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("voice-setup"),
                                    onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)) }) { Text(if (voiceReady) "Update my voice setup" else "Set up my voice") }
                                SettingsToggle("Recognize my voice", "Recommended. Other people’s plans won’t become yours. Voice matching can be wrong.", voiceEnabled, voice::setEnabled, "voice-matching", voiceReady && !listening.recording && !listening.processing)
                                HorizontalDivider()
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Switch(diarization, settings::setDiarization, enabled = !listening.recording && !listening.processing,
                                    modifier = Modifier.testTag("diarization-setting").controlLabel("Tell different voices apart"))
                                    Text("Tell different voices apart", Modifier.padding(start = 8.dp))
                                }
                                QuietLabel("Recommended. Works on this phone. Voice labels reset when listening stops and don’t identify people or prove consent. Unclear or overlapping voices stay unknown. Turn off to save battery.")
                                SettingsDetails("About voice matching") {
                                    Text(if (voiceReady) "Voice ready · ${speechLanguage.label}" else "No voice setup for ${speechLanguage.label}. Set up your voice to recognize your own plans.")
                                    QuietLabel("Your voice setup is saved separately for each language. Auto-detect checks all your languages. Start with a full sentence to help matching.")
                                    QuietLabel("Other voices stay as information. Brief phrases need a recent strong match; otherwise the speaker stays unknown. Matching can be wrong and does not prove consent.")
                                    Text("Voice matching method", style = MaterialTheme.typography.titleSmall)
                                    com.sidekick.app.speaker.SpeakerModel.entries.filter { it.offered }.forEach { candidate ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            RadioButton(speakerModel == candidate, { voice.selectModel(candidate) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speaker-model-${candidate.name}").controlLabel("Voice matching method: ${candidate.label}"))
                                            Text(candidate.label, Modifier.padding(start = 8.dp))
                                        }
                                    }
                                    QuietLabel("Each method needs its own voice setup. Your previous setup is kept.")
                                    Text(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "Voice model: 3D-Speaker CAM++ Chinese/English advanced, Apache 2.0. Runtime: sherpa-onnx, Apache 2.0." else "Voice model: WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0. Runtime: sherpa-onnx, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced" else "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM"))) }) { Text("Details & license") }
                                }
                                SettingsDetails("Names you often mention") {
                                    val spokenNames by settings.spokenNames.collectAsState()
                                    QuietLabel("One per line: the name, then how it sounds in Korean, e.g. “Sarah 세라”. When you say it in Korean speech, SideKick writes the name. Needs the “Korean names & time check” speech file. Stays on this phone.")
                                    androidx.compose.material3.OutlinedTextField(value = spokenNames, onValueChange = settings::setSpokenNames, minLines = 3,
                                        label = { Text("Names") }, modifier = Modifier.fillMaxWidth().testTag("spoken-names"))
                                }
                                HorizontalDivider()
                                TextButton(enabled = !listening.recording && !listening.processing, onClick = { deleteVoice = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("delete-voice")) { Text("Delete my voice setup") }
                            }
                            "PLANS" -> {
                                SettingsToggle("Save plans automatically", "Recommended. Saves plans and changes without asking; undo any of them. Off: you check each plan first.", handsOff, settings::setHandsOff, "hands-off")
                                HorizontalDivider()
                                Text("If a plan has no end time:")
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(mode == GuidanceMode.PASSIVE, { settings.setMode(GuidanceMode.PASSIVE) }, modifier = Modifier.testTag("mode-passive").controlLabel("Leave it unknown"))
                                    Text("Leave it unknown (recommended)", Modifier.padding(start = 8.dp))
                                }
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(mode == GuidanceMode.ASK, { settings.setMode(GuidanceMode.ASK) }, modifier = Modifier.testTag("mode-ask").controlLabel("Ask when I check it"))
                                    Text("Ask when I check it", Modifier.padding(start = 8.dp))
                                }
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(informational, settings::setIncludeInformational, modifier = Modifier.testTag("include-informational").controlLabel("Keep plans I’m not attending"))
                                    Text("Keep plans I’m not attending", Modifier.padding(start = 8.dp))
                                }
                                QuietLabel("These plans don’t block your calendar. You can mark them as your own later.")
                                HorizontalDivider()
                                val readAloud by settings.readRemindersAloud.collectAsState()
                                SettingsToggle("Read reminders aloud", "Off by default. When a reminder arrives, the phone also says it out loud.", readAloud, settings::setReadRemindersAloud, "read-reminders-aloud")
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Reminder alerts") }
                                CalendarSyncSection()
                                HorizontalDivider()
                                Text("Places", style = MaterialTheme.typography.titleMedium)
                                SettingsToggle("Find addresses", "Sends the place and city or nearby location to Android’s address service. You confirm before saving. Tap a place to open Maps.", automatic, settings::setAutomaticPlaces, "automatic-places")
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(placeContext == PlaceContext.CITY, { settings.setPlaceContext(PlaceContext.CITY) }, modifier = Modifier.controlLabel("Use a city I choose"))
                                    Text("Use a city I choose", Modifier.padding(start = 8.dp))
                                }
                                OutlinedTextField(city, settings::setCity, label = { Text("City or region") }, placeholder = { Text("For example, Madison, Wisconsin") }, modifier = Modifier.testTag("place-city"))
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(placeContext == PlaceContext.PHONE, {
                                        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) settings.setPlaceContext(PlaceContext.PHONE)
                                        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }, modifier = Modifier.controlLabel("Use my nearby location"))
                                    Text("Use my nearby location", Modifier.padding(start = 8.dp))
                                }
                                QuietLabel("Location is checked only when finding an address. No continuous tracking. Your chosen city is used if location is unavailable.")
                                HorizontalDivider()
                                Text("Messages", style = MaterialTheme.typography.titleMedium)
                                SettingsToggle("Read text I share", "Find plans in text shared from another app.", sharing, settings::setSharedMessages, "shared-messages")
                                SettingsToggle("Read new message notifications", "Reads new previews from apps you choose, not message history. Senders’ plans aren’t your promises.", messages, settings::setMessageNotifications, "message-scanning")
                                QuietLabel("Text is temporary, except quotes kept in saved plans.")
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow notification access") }
                                if (messages) {
                                    OutlinedTextField(filter, { filter = it }, label = { Text("Find a messaging app") })
                                    apps.filter { it.second.contains(filter, true) }.forEach { (name, label) ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(name in selectedApps, { settings.setMessageApp(name, it) }, modifier = Modifier.controlLabel("Read notifications from $label"))
                                            Text(label, Modifier.padding(start = 8.dp))
                                        }
                                    }
                                    if (selectedApps.isEmpty()) Text("Choose an app to start reading messages.")
                                }
                            }
                            "PRIVACY" -> {
                                ConsentRulesSection(settings)
                                MemoryPrivacySection()
                                PrivacyGuidanceSection(settings)
                            }
                        }
                    } }
            }
        }
    }
    if (deleteVoice) AlertDialog(onDismissRequest = { deleteVoice = false }, title = { Text("Delete voice setup?") },
    text = { Text("Deletes your voice setup for every language and matching method. Saved plans stay. You can set up your voice again.") },
    confirmButton = { TextButton(onClick = { voice.delete(); deleteVoice = false }) { Text("Delete") } },
    dismissButton = { TextButton(onClick = { deleteVoice = false }) { Text("Cancel") } })
}

private data class SettingsSection(val id: String, val title: String, val description: String)
/** Four plain sections. Page ids are kept from the earlier layout where the content stayed (LISTENING, PLANS, PRIVACY). */
private val settingsPages = listOf(
SettingsSection("LISTENING", "Listening", "Language, speech, listening tips and phone checks"),
SettingsSection("VOICE", "Your voice", "Set up your voice so SideKick knows which plans are yours"),
SettingsSection("PLANS", "Reminders & calendar", "Saving plans, reminders, calendar, places and messages"),
SettingsSection("PRIVACY", "Privacy", "Conversation rules, saved plans and your region")
)

@Composable
internal fun SettingsToggle(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
    com.sidekick.core.SpeechQuality.ACCURATE -> "Larger"
}

@Composable
internal fun SettingsDetails(label: String, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // Survives the screen being rebuilt (for example after a bigger text or button size is chosen inside it).
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(label) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).let { if (tag != null) it.testTag(tag) else it }) { Text(if (expanded) "Hide details" else label) }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
