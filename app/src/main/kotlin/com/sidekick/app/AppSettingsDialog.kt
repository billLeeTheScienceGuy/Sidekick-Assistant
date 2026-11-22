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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppSettingsDialog(settings: GuidanceSettings, onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
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
                    if (page != null) TextButton(onClick = { page = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("settings-back")) { Text(tr(R.string.back)) }
                    Text(settingsPages.firstOrNull { it.id == page }?.title ?: tr(R.string.settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.close)) }
                }
                HorizontalDivider()
                key(page) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            null -> {
                                QuietLabel(tr(R.string.choose_what_you_want_to_change))
                                settingsPages.forEach { section ->
                                    OutlinedCard(onClick = { page = section.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("settings-page-${section.id}")) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(section.title, style = MaterialTheme.typography.titleLarge)
                                            Text(section.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            "SCREEN" -> {
                                Text(tr(R.string.app_language), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var chosenLanguage by rememberSaveable { mutableStateOf(AppLanguage.current()) }
                                    AppLanguage.tags.forEach { tag ->
                                        FilterChip(chosenLanguage == tag, { if (chosenLanguage != tag) { chosenLanguage = tag; AppLanguage.set(context, tag) } },
                                            label = { Text(if (tag.isEmpty()) tr(R.string.same_as_phone) else AppLanguage.nativeName(tag)) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("app-language-" + tag.ifEmpty { "system" }))
                                    }
                                }
                                QuietLabel(tr(R.string.app_language_note))
                                Text(tr(R.string.screen), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(oneScreen, { settings.setOneScreenHome(true) }, label = { Text(tr(R.string.simple_recommended)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("home-simple"))
                                    FilterChip(!oneScreen, { settings.setOneScreenHome(false) }, label = { Text(tr(R.string.detailed)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("home-detailed"))
                                }
                                QuietLabel(tr(R.string.simple_shows_a_large_start_button))
                                Text(tr(R.string.text_size), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteTextSize.entries.forEach { choice ->
                                        FilterChip(textSize == choice, { settings.setNoteTextSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("text-size-${choice.name}"))
                                    }
                                }
                                QuietLabel(tr(R.string.also_follows_your_phone_s_font))
                                Text(tr(R.string.light_or_dark), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppearanceMode.entries.forEach { mode ->
                                        FilterChip(selected = mode == appearance, onClick = { settings.setAppearance(mode) },
                                            label = { Text(when (mode) { AppearanceMode.SYSTEM -> tr(R.string.same_as_phone); AppearanceMode.LIGHT -> tr(R.string.light); AppearanceMode.DARK -> tr(R.string.dark) }) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("appearance-${mode.name}"))
                                    }
                                }
                                Text(tr(R.string.colors), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InterfaceDesign.entries.forEach { choice ->
                                        FilterChip(design == choice, { settings.setInterfaceDesign(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("design-${choice.name}"))
                                    }
                                }
                                QuietLabel(tr(R.string.calm_is_teal_classic_is_blue))
                                Text(tr(R.string.buttons_spacing), style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    InterfaceSize.entries.forEach { choice ->
                                        FilterChip(interfaceSize == choice, { settings.setInterfaceSize(choice) }, label = { Text(choice.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("interface-size-${choice.name}"))
                                    }
                                }
                                QuietLabel(tr(R.string.text_size_changes_words_buttons_spacing))
                                TextButton(onClick = settings::resetAppearance, modifier = Modifier.heightIn(min = 48.dp).testTag("reset-appearance")) { Text(tr(R.string.reset_screen_choices)) }
                                SettingsToggle(tr(R.string.type_notes), tr(R.string.show_a_place_to_type_notes), manualNotes, settings::setManualNotes, "manual-notes")
                            }
                            "LISTENING" -> {
                                val auto = speechLanguage == com.sidekick.core.SpeechLanguage.AUTO
                                SettingsToggle(tr(R.string.auto_detect_language), tr(R.string.switch_languages_as_you_speak), auto, {
                                    settings.setAutomaticLanguage(it)
                                    voice.selectLanguage(settings.speechLanguage.value)
                                }, "auto-language", !listening.recording && !listening.processing)
                                if (auto) {
                                    QuietLabel(tr(R.string.short_phrases_may_be_detected_wrong))
                                    val detect by settings.detectLanguages.collectAsState()
                                    Text(tr(R.string.languages_to_detect), style = MaterialTheme.typography.titleSmall)
                                    QuietLabel(tr(R.string.fewer_languages_fewer_mistakes))
                                    com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { language ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(language in detect, { on -> settings.setDetectLanguages(if (on) detect + language else detect - language) },
                                                enabled = !listening.recording && !listening.processing && !(language in detect && detect.size == 1),
                                                modifier = Modifier.testTag("detect-${language.code}").controlLabel(tr(R.string.detect_1_s, language.label)))
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
                                                    st.ready -> tr(R.string.ready)
                                                    st.download.isEmpty() -> tr(R.string.turn_on_files)
                                                    else -> tr(R.string.needs_1_s_mb, (st.downloadBytes + 500_000) / 1_000_000) + if (st.readyInFaster) tr(R.string.works_in_faster) else ""
                                                }) }, onClick = {
                                                    settings.setSpeechLanguage(language); voice.selectLanguage(language); languageMenu = false
                                                }, modifier = Modifier.testTag("speech-language-${language.code}"))
                                            }
                                        }
                                    }
                                }
                                if (listening.recording || listening.processing) QuietLabel(tr(R.string.stop_listening_before_changing_the_language))
                                val pauseResting by settings.pauseWhenResting.collectAsState()
                                SettingsToggle(tr(R.string.pause_when_the_phone_is_resting), tr(R.string.off_by_default_saves_battery_after) +
                                    tr(R.string.no_speech_screen_off_not_carried) +
                                    tr(R.string.talk_that_starts_while_it_stays), pauseResting, settings::setPauseWhenResting, "pause-when-resting")
                                val skipIntro by settings.skipListeningIntro.collectAsState()
                                SettingsToggle(tr(R.string.show_listening_tips), tr(R.string.a_short_reminder_before_you_start), !skipIntro, { settings.setSkipListeningIntro(!it) }, "start-reminder")
                                HorizontalDivider()
                                Text(tr(R.string.speech_recognition), style = MaterialTheme.typography.titleMedium)
                                com.sidekick.core.SpeechQuality.entries.forEach { quality ->
                                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(speechQuality == quality, { settings.setSpeechQuality(quality) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.controlLabel(tr(R.string.speech_quality_1_s, quality.label)))
                                        Text(speechQualityName(quality), Modifier.weight(1f))
                                    }
                                }
                                QuietLabel(tr(R.string.recommended_works_with_every_language_faster))
                                ModelPackSection(initiallyExpanded = false)
                                HorizontalDivider()
                                Text(tr(R.string.keep_listening_after_a_restart), style = MaterialTheme.typography.titleMedium)
                                Text(if (assistantSelected) tr(R.string.sidekick_is_your_default_assistant) else tr(R.string.sidekick_is_not_your_default_assistant), modifier = Modifier.testTag("assistant-role-status"))
                                TextButton(onClick = { assistantPicker.launch(assistant.picker()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("assistant-picker")) { Text(tr(R.string.choose_default_assistant)) }
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Switch(recoveryEnabled, assistant::setEnabled, modifier = Modifier.testTag("assistant-recovery").controlLabel(tr(R.string.resume_listening_after_a_restart)))
                                    Text(tr(R.string.resume_listening_after_a_restart), Modifier.padding(start = 8.dp))
                                }
                                QuietLabel(tr(R.string.needs_sidekick_as_your_default_assistant))
                                Text(recoveryStatus)
                                HorizontalDivider()
                                SettingsDetails(tr(R.string.what_you_can_ask_sidekick), "ask-examples") {
                                    val detectNow by settings.detectLanguages.collectAsState()
                                    AskExamples(askLanguages(speechLanguage, detectNow))
                                }
                                var checks by remember { mutableStateOf(false) }
                                TextButton(onClick = { checks = !checks }, modifier = Modifier.heightIn(min = 48.dp).testTag("phone-checks")) { Text(if (checks) tr(R.string.hide_phone_checks) else tr(R.string.check_phone_setup)) }
                                if (checks) SetupStatus(settings)
                            }
                            "VOICE" -> {
                                val enrolledVoices by voice.enrolled.collectAsState()
                                Text(if (voiceReady) tr(R.string.your_voice_is_set_up_1, enrolledVoices.keys.joinToString(", ") { it.label }) else tr(R.string.set_up_your_voice_so_sidekick),
                                    style = MaterialTheme.typography.bodyLarge)
                                val voiceConsentNeeded by voice.consentNeeded.collectAsState()
                                if (voiceConsentNeeded) {
                                    Text(tr(R.string.voice_consent_needed_voice_matching_is), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("voice-consent-needed"))
                                    TextButton(onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)
                                        .putExtra(com.sidekick.app.speaker.VoiceEnrollmentActivity.CONSENT_ONLY, true)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.review_voice_consent)) }
                                }
                                Button(enabled = !listening.recording && !listening.processing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("voice-setup"),
                                    onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)) }) { Text(if (voiceReady) tr(R.string.update_my_voice_setup) else tr(R.string.set_up_my_voice)) }
                                val wakeOn by voice.wakeOn.collectAsState()
                                val wakeSaved by voice.wakeSaved.collectAsState()
                                SettingsToggle(tr(R.string.sidekick_voice_check), if (wakeSaved) tr(R.string.recognizes_you_by_how_you_say)
                                    else tr(R.string.not_set_up_yet), wakeOn && wakeSaved, voice::setWakeOn, "wake-check-setting", wakeSaved && !listening.recording && !listening.processing)
                                OutlinedButton(enabled = !listening.recording && !listening.processing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wake-setup-open"),
                                    onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)
                                        .putExtra(com.sidekick.app.speaker.VoiceEnrollmentActivity.WAKE_ONLY, true)) }) { Text(if (wakeSaved) tr(R.string.redo_sidekick_voice_check) else tr(R.string.set_up_sidekick_voice_check)) }
                                SettingsToggle(tr(R.string.recognize_my_voice), tr(R.string.recommended_keeps_other_people_s_plans), voiceEnabled, voice::setEnabled, "voice-matching", voiceReady && !listening.recording && !listening.processing)
                                HorizontalDivider()
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Switch(diarization, settings::setDiarization, enabled = !listening.recording && !listening.processing,
                                    modifier = Modifier.testTag("diarization-setting").controlLabel(tr(R.string.tell_different_voices_apart)))
                                    Text(tr(R.string.tell_different_voices_apart), Modifier.padding(start = 8.dp))
                                }
                                QuietLabel(tr(R.string.recommended_labels_reset_when_listening_stops))
                                SettingsDetails(tr(R.string.about_voice_matching)) {
                                    Text(if (voiceReady) tr(R.string.voice_ready_1_s, speechLanguage.label) else tr(R.string.no_voice_setup_for_1_s, speechLanguage.label))
                                    QuietLabel(tr(R.string.saved_per_language_auto_detect_checks))
                                    QuietLabel(tr(R.string.short_phrases_often_stay_unknown_matching))
                                    Text(tr(R.string.voice_matching_method), style = MaterialTheme.typography.titleSmall)
                                    com.sidekick.app.speaker.SpeakerModel.entries.filter { it.offered }.forEach { candidate ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            RadioButton(speakerModel == candidate, { voice.selectModel(candidate) }, enabled = !listening.recording && !listening.processing, modifier = Modifier.testTag("speaker-model-${candidate.name}").controlLabel(tr(R.string.voice_matching_method_1_s, candidate.label)))
                                            Text(candidate.label, Modifier.padding(start = 8.dp))
                                        }
                                    }
                                    QuietLabel(tr(R.string.each_method_needs_its_own_voice))
                                    Text(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "Voice model: 3D-Speaker CAM++ Chinese/English advanced, Apache 2.0. Runtime: sherpa-onnx, Apache 2.0." else "Voice model: WeSpeaker VoxCeleb ResNet34-LM, CC BY 4.0. Runtime: sherpa-onnx, Apache 2.0.", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(if (speakerModel == com.sidekick.app.speaker.SpeakerModel.CAMPPLUS) "https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced" else "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM"))) }) { Text(tr(R.string.details_license)) }
                                }
                                SettingsDetails(tr(R.string.names_you_often_mention)) {
                                    val spokenNames by settings.spokenNames.collectAsState()
                                    QuietLabel("One per line: the name, then how it sounds in Korean, e.g. “Sarah 세라”. Needs the Korean names speech file.")
                                    androidx.compose.material3.OutlinedTextField(value = spokenNames, onValueChange = settings::setSpokenNames, minLines = 3,
                                        label = { Text(tr(R.string.names)) }, modifier = Modifier.fillMaxWidth().testTag("spoken-names"))
                                }
                                HorizontalDivider()
                                TextButton(enabled = !listening.recording && !listening.processing, onClick = { deleteVoice = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("delete-voice")) { Text(tr(R.string.delete_my_voice_setup)) }
                            }
                            "PLANS" -> {
                                SettingsToggle(tr(R.string.save_plans_automatically), tr(R.string.recommended_saves_without_asking_you_can), handsOff, settings::setHandsOff, "hands-off")
                                HorizontalDivider()
                                Text(tr(R.string.if_a_plan_has_no_end))
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(mode == GuidanceMode.PASSIVE, { settings.setMode(GuidanceMode.PASSIVE) }, modifier = Modifier.testTag("mode-passive").controlLabel(tr(R.string.leave_it_unknown)))
                                    Text(tr(R.string.leave_it_unknown_recommended), Modifier.padding(start = 8.dp))
                                }
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(mode == GuidanceMode.ASK, { settings.setMode(GuidanceMode.ASK) }, modifier = Modifier.testTag("mode-ask").controlLabel(tr(R.string.ask_when_i_check_it)))
                                    Text(tr(R.string.ask_when_i_check_it), Modifier.padding(start = 8.dp))
                                }
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(informational, settings::setIncludeInformational, modifier = Modifier.testTag("include-informational").controlLabel(tr(R.string.keep_plans_i_m_not_attending)))
                                    Text(tr(R.string.keep_plans_i_m_not_attending), Modifier.padding(start = 8.dp))
                                }
                                QuietLabel(tr(R.string.they_don_t_block_your_calendar))
                                HorizontalDivider()
                                val readAloud by settings.readRemindersAloud.collectAsState()
                                SettingsToggle(tr(R.string.read_reminders_aloud), tr(R.string.the_phone_also_says_reminders_out), readAloud, settings::setReadRemindersAloud, "read-reminders-aloud")
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.reminder_alerts)) }
                                CalendarSyncSection()
                                HorizontalDivider()
                                Text(tr(R.string.places), style = MaterialTheme.typography.titleMedium)
                                SettingsToggle(tr(R.string.find_addresses), tr(R.string.sends_the_place_name_and_city), automatic, settings::setAutomaticPlaces, "automatic-places")
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(placeContext == PlaceContext.CITY, { settings.setPlaceContext(PlaceContext.CITY) }, modifier = Modifier.controlLabel(tr(R.string.use_a_city_i_choose)))
                                    Text(tr(R.string.use_a_city_i_choose), Modifier.padding(start = 8.dp))
                                }
                                OutlinedTextField(city, settings::setCity, label = { Text(tr(R.string.city_or_region)) }, placeholder = { Text(tr(R.string.for_example_madison_wisconsin)) }, modifier = Modifier.testTag("place-city"))
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(placeContext == PlaceContext.PHONE, {
                                        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) settings.setPlaceContext(PlaceContext.PHONE)
                                        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }, modifier = Modifier.controlLabel(tr(R.string.use_my_nearby_location)))
                                    Text(tr(R.string.use_my_nearby_location), Modifier.padding(start = 8.dp))
                                }
                                QuietLabel(tr(R.string.only_used_when_finding_an_address))
                                HorizontalDivider()
                                Text(tr(R.string.messages), style = MaterialTheme.typography.titleMedium)
                                SettingsToggle(tr(R.string.read_text_i_share), tr(R.string.find_plans_in_text_shared_from), sharing, settings::setSharedMessages, "shared-messages")
                                SettingsToggle(tr(R.string.read_new_message_notifications), tr(R.string.reads_new_previews_from_apps_you), messages, settings::setMessageNotifications, "message-scanning")
                                QuietLabel(tr(R.string.text_is_temporary_except_quotes_kept))
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(R.string.allow_notification_access)) }
                                if (messages) {
                                    OutlinedTextField(filter, { filter = it }, label = { Text(tr(R.string.find_a_messaging_app)) })
                                    apps.filter { it.second.contains(filter, true) }.forEach { (name, label) ->
                                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(name in selectedApps, { settings.setMessageApp(name, it) }, modifier = Modifier.controlLabel(tr(R.string.read_notifications_from_1_s, label)))
                                            Text(label, Modifier.padding(start = 8.dp))
                                        }
                                    }
                                    if (selectedApps.isEmpty()) Text(tr(R.string.choose_an_app_to_start_reading))
                                }
                            }
                            "PRIVACY" -> {
                                ConsentRulesSection(settings)
                                MemoryPrivacySection()
                                CrashReportSettings(settings)
                                PrivacyGuidanceSection(settings)
                            }
                        }
                    } }
            }
        }
    }
    if (deleteVoice) AlertDialog(onDismissRequest = { deleteVoice = false }, title = { Text(tr(R.string.delete_voice_setup)) },
    text = { Text(tr(R.string.deletes_your_voice_setup_for_all)) },
    confirmButton = { TextButton(onClick = { voice.delete(); deleteVoice = false }) { Text(tr(R.string.delete)) } },
    dismissButton = { TextButton(onClick = { deleteVoice = false }) { Text(tr(R.string.cancel)) } })
}

private data class SettingsSection(val id: String, val title: String, val description: String)
/** Five plain sections. Page ids are kept from the earlier layout where the content stayed (LISTENING, PLANS, PRIVACY). */
private val settingsPages: List<SettingsSection> get() = listOf(
// Screen first: it matters most to people who find the phone hard to read.
SettingsSection("SCREEN", tr(R.string.screen_text_size), tr(R.string.home_layout_text_size_colors)),
SettingsSection("LISTENING", tr(R.string.listening), tr(R.string.language_speech_battery)),
SettingsSection("VOICE", tr(R.string.your_voice), tr(R.string.so_sidekick_knows_which_plans_are)),
SettingsSection("PLANS", tr(R.string.reminders_calendar), tr(R.string.saving_reminders_calendar_places_messages)),
SettingsSection("PRIVACY", tr(R.string.privacy), tr(R.string.recording_rules_saved_data_region))
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
    com.sidekick.core.SpeechQuality.ADAPTIVE -> tr(R.string.recommended)
    com.sidekick.core.SpeechQuality.FAST -> tr(R.string.faster)
    com.sidekick.core.SpeechQuality.ACCURATE -> tr(R.string.larger)
}

@Composable
internal fun SettingsDetails(label: String, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // Survives the screen being rebuilt (for example after a bigger text or button size is chosen inside it).
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(label) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).let { if (tag != null) it.testTag(tag) else it }) { Text(if (expanded) tr(R.string.hide_details) else label) }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Languages whose example questions to show: the chosen one, or Auto-detect's languages (English first). */
internal fun askLanguages(language: com.sidekick.core.SpeechLanguage, detect: Set<com.sidekick.core.SpeechLanguage>): List<String> =
    if (language != com.sidekick.core.SpeechLanguage.AUTO) listOf(language.code)
    else detect.map { it.code }.sortedBy { if (it == "en") 0 else 1 }

/** Questions to read off the screen (each one is checked to be understood: PlanQuestionTest). */
@Composable
internal fun AskExamples(codes: List<String>, numbered: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("ask-list")) {
        if (!numbered) QuietLabel(tr(R.string.start_with_sidekick_answers_come_from))
        codes.forEach { code ->
            if (codes.size > 1) Text(com.sidekick.core.SpeechLanguage.fromCode(code).label, style = MaterialTheme.typography.titleSmall)
            com.sidekick.core.PlanQuestion.examples(code).forEachIndexed { i, q ->
                Text((if (numbered) "${i + 1}. " else "• ") + q, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
