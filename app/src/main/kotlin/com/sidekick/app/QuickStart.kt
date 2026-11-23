package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.sidekick.app.speech.ModelPacks
import com.sidekick.core.*
import kotlinx.coroutines.*

@Composable
internal fun ModelPackSection(initiallyExpanded: Boolean = true) {
    val context = LocalContext.current
    val app = context.applicationContext as SidekickApplication
    val language by app.guidance.speechLanguage.collectAsState()
    val quality by app.guidance.speechQuality.collectAsState()
    val revision by ModelPacks.revision.collectAsState()
    val listening by com.sidekick.app.audio.ListeningService.state.collectAsState()
    var working by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let {
        scope.launch {
            working = true; message = tr(R.string.checking_the_speech_file)
            try { val id = ModelPacks.importPack(context, it); message = tr(R.string.installed_1_s, ModelPacks.labels[id]) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: tr(R.string.could_not_install_the_speech_file) }
            finally { working = false }
        }
    } }
    val missing = remember(language, quality, revision) { ModelPacks.missing(context, language, quality) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Text(tr(R.string.speech_files), style = MaterialTheme.typography.titleMedium)
    val readyNow = remember(language, quality, revision) { ModelPacks.status(context, language, quality) }
    Text(when {
        readyNow.ready -> tr(R.string.speech_is_ready)
        readyNow.download.isEmpty() -> tr(R.string.turn_on_1_s_already_on, packNames(readyNow.switchOn))
        else -> tr(R.string.this_language_needs_1_s_2, packNames(readyNow.download), mb(readyNow.downloadBytes))
    }, modifier = Modifier.testTag("model-readiness"))
    if (missing.isEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) tr(R.string.hide_speech_files) else tr(R.string.manage_speech_files)) }
    if (expanded || missing.isNotEmpty()) {
    Text(tr(R.string.english_faster_and_voice_matching_are))
    var confirm by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // pack, true = remove / false = switch off
    val installedNow = remember(revision) { ModelPacks.installedPacks(context) }
    val inUseNow = remember(revision) { ModelPacks.packsInUse(context) }
    val sizes = remember { ModelPacks.sizes(context) }
    val openDownloads = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/tag/speech-packs"))) }

    // ---- Languages: what each one needs in the chosen mode, given the files already here ----
    Text(tr(R.string.languages_1_s, speechQualityName(quality)), style = MaterialTheme.typography.titleSmall)
    SpeechLanguage.entries.forEach { lang ->
        val st = remember(revision, quality) { LanguageReadiness.status(lang, quality, installedNow, inUseNow, sizes) }
        Column(Modifier.fillMaxWidth().testTag("language-ready-${lang.code}")) {
            Text(lang.label + " · " + when {
                st.ready -> tr(R.string.ready_2)
                st.download.isEmpty() -> tr(R.string.turn_on_1_s_no_download, packNames(st.switchOn))
                else -> tr(R.string.needs_1_s_2_s, packNames(st.download), mb(st.downloadBytes))
            }, style = MaterialTheme.typography.bodyLarge)
            if (!st.ready) {
                val alsoAdds = remember(revision, quality) {
                    SpeechLanguage.entries.filter { it != lang &&
                        !LanguageReadiness.status(it, quality, installedNow, inUseNow, sizes).ready &&
                        LanguageReadiness.status(it, quality, installedNow + st.download, inUseNow + st.download + st.switchOn, sizes).ready }
                }
                if (alsoAdds.isNotEmpty()) QuietLabel(tr(R.string.also_makes_ready_1_s, alsoAdds.joinToString { it.label.substringAfter(" · ") }))
                if (st.readyInFaster && quality != SpeechQuality.FAST) QuietLabel(tr(R.string.works_now_in_faster_mode))
                Row {
                    if (st.switchOn.isNotEmpty()) TextButton(enabled = !listening.recording && !listening.processing, onClick = { st.switchOn.forEach { ModelPacks.setInUse(context, it, true) } },
                        modifier = Modifier.testTag("language-turn-on-${lang.code}")) { Text(tr(R.string.turn_on)) }
                    if (st.download.isNotEmpty()) TextButton(onClick = { openDownloads() }, modifier = Modifier.testTag("language-get-${lang.code}")) { Text(tr(R.string.download_1_s, mb(st.downloadBytes))) }
                    if (st.readyInFaster && quality != SpeechQuality.FAST && lang == language) TextButton(enabled = !listening.recording && !listening.processing,
                        onClick = { app.guidance.setSpeechQuality(SpeechQuality.FAST) }, modifier = Modifier.testTag("language-use-faster")) { Text(tr(R.string.use_faster_mode)) }
                }
            }
        }
    }
    HorizontalDivider()

    // ---- Files: size, languages covered, and what removing or switching off would change ----
    Text(tr(R.string.speech_files), style = MaterialTheme.typography.titleSmall)
    ModelPacks.labels.forEach { (id, label) ->
        val megabytes = sizes.getValue(id) / 1_000_000
        val installed = id in installedNow
        val bundled = remember(revision) { installed && ModelPacks.bundled(context, id) }
        val inUse = id in inUseNow
        OutlinedCard(Modifier.fillMaxWidth().testTag("pack-$id")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr(R.string.n_1_s_2_s_mb, label, megabytes), style = MaterialTheme.typography.titleSmall)
                QuietLabel(ModelPacks.usedFor[id].orEmpty())
                Text(when { !installed -> tr(R.string.not_on_this_phone); !inUse -> tr(R.string.on_this_phone_switched_off); bundled -> tr(R.string.included_in_this_app_in_use); else -> tr(R.string.on_this_phone_in_use) })
                if (installed) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(inUse, { on -> if (on) ModelPacks.setInUse(context, id, true) else confirm = id to false }, enabled = !listening.recording && !listening.processing,
                        modifier = Modifier.testTag("pack-use-$id").controlLabel(tr(R.string.use_1_s, label)))
                    Text(tr(R.string.use), Modifier.padding(start = 8.dp))
                }
                if (installed && !bundled) TextButton(enabled = !working && !listening.recording && !listening.processing, onClick = { confirm = id to true },
                    modifier = Modifier.testTag("pack-remove-$id")) { Text(tr(R.string.remove_from_phone_frees_1_s, megabytes)) }
                if (!installed && id == "korean") QuietLabel(tr(R.string.not_available_for_download_yet))
                else if (!installed) {
                    val adds = remember(revision, quality) { LanguageReadiness.unlocks(id, quality, installedNow, inUseNow) }
                    if (adds.isNotEmpty()) QuietLabel(tr(R.string.makes_ready_1_s, adds.joinToString { it.label.substringAfter(" · ") }))
                    TextButton(modifier = Modifier.testTag("pack-get-$id"), onClick = { openDownloads() }) { Text(tr(R.string.download_1_s_mb_opens_browser, megabytes)) }
                }
            }
        }
    }
    confirm?.let { (id, remove) ->
        val megabytes = sizes.getValue(id) / 1_000_000
        val impact = LanguageReadiness.impact(id, quality, installedNow, inUseNow)
        val effect = listOfNotNull(
            impact.stops.takeIf { it.isNotEmpty() }?.let { tr(R.string.stops_working_1_s, it.joinToString { l -> l.label.substringAfter(" · ") }) },
            impact.fasterOnly.takeIf { it.isNotEmpty() }?.let { tr(R.string.still_works_in_faster_mode_only, it.joinToString { l -> l.label.substringAfter(" · ") }) },
        ).joinToString(" ").ifEmpty { tr(R.string.no_language_you_can_use_now) }
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (remove) tr(R.string.remove_1_s_2, ModelPacks.labels[id]) else tr(R.string.switch_off_1_s, ModelPacks.labels[id])) },
            text = { Text(if (remove) tr(R.string.n_1_s_deletes_2_s_mb, effect, megabytes) else tr(R.string.n_1_s_the_files_stay_on, effect)) },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (!remove) ModelPacks.setInUse(context, id, false)
                else scope.launch { working = true; try { val freed = ModelPacks.remove(context, id); message = tr(R.string.removed_freed_1_s_mb, freed / 1_000_000) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { message = tr(R.string.couldn_t_remove_the_files_try) } finally { working = false } }
            }, modifier = Modifier.testTag("pack-confirm")) { Text(if (remove) tr(R.string.remove) else tr(R.string.switch_off)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr(R.string.cancel)) } })
    }
    QuietLabel(tr(R.string.files_come_only_from_sidekick_s))
    TextButton(enabled = !working && !listening.recording && !listening.processing, modifier = Modifier.testTag("import-speech-pack"),
        onClick = { picker.launch(arrayOf("*/*")) }) { Text(tr(R.string.install_speech_file)) }
    // SideKick has no network permission: the browser downloads the file, then "Install speech file" verifies it.
    TextButton(modifier = Modifier.testTag("download-speech-packs"), onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/tag/speech-packs"))) }) { Text(tr(R.string.get_speech_files_opens_browser)) }
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/blob/main/docs/guides/MODEL_PACKS.md"))) }) { Text(tr(R.string.how_to_install_speech_files)) }
    }
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message)
}

@Composable
internal fun QuickStart(onClose: () -> Unit) {
    val context = LocalContext.current; val app = context.applicationContext as SidekickApplication
    val language by app.guidance.speechLanguage.collectAsState()
    val quality by app.guidance.speechQuality.collectAsState()
    val voiceReady by app.voiceProfile.ready.collectAsState()
    val listening by com.sidekick.app.audio.ListeningService.state.collectAsState()
    val editable = !listening.recording && !listening.processing
    var refresh by remember { mutableIntStateOf(0) }
    var languageMenu by remember { mutableStateOf(false) }; var qualityMenu by remember { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    AlertDialog(onDismissRequest = onClose, title = { Text(tr(R.string.get_started)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.you_can_type_notes_and_use))
            Text(tr(R.string.n_1_choose_speech_language_and_quality), style = MaterialTheme.typography.titleMedium)
            Box {
                TextButton(enabled = editable, onClick = { languageMenu = true }, modifier = Modifier.testTag("setup-language-choice")) { Text(tr(R.string.language_1_s, language.label)) }
                DropdownMenu(languageMenu, { languageMenu = false }) { SpeechLanguage.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { if (choice == SpeechLanguage.AUTO) app.guidance.setAutomaticLanguage(true) else app.guidance.setSpeechLanguage(choice); app.voiceProfile.selectLanguage(choice); languageMenu = false }, modifier = Modifier.testTag("setup-language-${choice.code}"))
                } }
            }
            Box {
                TextButton(enabled = editable, onClick = { qualityMenu = true }, modifier = Modifier.testTag("setup-quality-choice")) { Text(tr(R.string.quality_1_s, speechQualityName(quality))) }
                DropdownMenu(qualityMenu, { qualityMenu = false }) { SpeechQuality.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(speechQualityName(choice)) }, onClick = { app.guidance.setSpeechQuality(choice); qualityMenu = false }, modifier = Modifier.testTag("setup-quality-${choice.name}"))
                } }
            }
            ModelPackSection(initiallyExpanded = false)
            Text(tr(R.string.n_2_optional_set_up_your_voice), style = MaterialTheme.typography.titleMedium)
            Text(if (voiceReady) tr(R.string.your_voice_is_set_up_for) else tr(R.string.until_then_plans_you_say_are))
            TextButton(enabled = editable, onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)) }) { Text(tr(R.string.set_up_my_voice)) }
            Text(tr(R.string.n_3_android_permissions), style = MaterialTheme.typography.titleMedium)
            val mic = remember(refresh) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
            Text(tr(R.string.microphone_1_s, if (mic) tr(R.string.allowed) else tr(R.string.needed_for_listening)), modifier = Modifier.testTag("setup-microphone"))
            Text(remember(refresh) { tr(R.string.reminder_delivery_1_s, if (app.reminders.notificationsAllowed()) tr(R.string.enabled) else tr(R.string.blocked)) })
            TextButton(onClick = { microphone.launch(Manifest.permission.RECORD_AUDIO) }) { Text(tr(R.string.allow_microphone)) }
            TextButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(tr(R.string.enable_reminder_alerts)) }
            Text(tr(R.string.n_4_start_listening), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.tap_start_listening_when_everyone_agrees))
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(tr(R.string.done)) } })
}

private fun packNames(ids: Set<String>) = ids.joinToString(" + ") { ModelPacks.labels[it] ?: it }
private fun mb(bytes: Long) = tr(R.string.n_1_s_mb, (bytes + 500_000) / 1_000_000)
