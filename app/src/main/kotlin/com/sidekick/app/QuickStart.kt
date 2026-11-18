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
            working = true; message = "Checking the speech file…"
            try { val id = ModelPacks.importPack(context, it); message = "Installed: ${ModelPacks.labels[id]}" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not install the speech file." }
            finally { working = false }
        }
    } }
    val missing = remember(language, quality, revision) { ModelPacks.missing(context, language, quality) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Text("Speech files", style = MaterialTheme.typography.titleMedium)
    val readyNow = remember(language, quality, revision) { ModelPacks.status(context, language, quality) }
    Text(when {
        readyNow.ready -> "Speech is ready"
        readyNow.download.isEmpty() -> "Turn on ${packNames(readyNow.switchOn)} (already on this phone)"
        else -> "This language needs ${packNames(readyNow.download)} · ${mb(readyNow.downloadBytes)}"
    }, modifier = Modifier.testTag("model-readiness"))
    if (missing.isEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide speech files" else "Manage speech files") }
    if (expanded || missing.isNotEmpty()) {
    Text("English Faster mode and voice matching are built in. Languages share files: one download can make several languages ready, " +
        "and you only ever download what is missing. Files can be switched off without deleting them. Speech stays on this phone.")
    var confirm by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // pack, true = remove / false = switch off
    val installedNow = remember(revision) { ModelPacks.installedPacks(context) }
    val inUseNow = remember(revision) { ModelPacks.packsInUse(context) }
    val sizes = remember { ModelPacks.sizes(context) }
    val openDownloads = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/tag/speech-packs"))) }

    // ---- Languages: what each one needs in the chosen mode, given the files already here ----
    Text("Languages · ${speechQualityName(quality)}", style = MaterialTheme.typography.titleSmall)
    SpeechLanguage.entries.forEach { lang ->
        val st = remember(revision, quality) { LanguageReadiness.status(lang, quality, installedNow, inUseNow, sizes) }
        Column(Modifier.fillMaxWidth().testTag("language-ready-${lang.code}")) {
            Text(lang.label + " · " + when {
                st.ready -> "Ready"
                st.download.isEmpty() -> "Turn on ${packNames(st.switchOn)} · no download"
                else -> "Needs ${packNames(st.download)} · ${mb(st.downloadBytes)}"
            }, style = MaterialTheme.typography.bodyLarge)
            if (!st.ready) {
                val alsoAdds = remember(revision, quality) {
                    SpeechLanguage.entries.filter { it != lang &&
                        !LanguageReadiness.status(it, quality, installedNow, inUseNow, sizes).ready &&
                        LanguageReadiness.status(it, quality, installedNow + st.download, inUseNow + st.download + st.switchOn, sizes).ready }
                }
                if (alsoAdds.isNotEmpty()) QuietLabel("Also makes ready: ${alsoAdds.joinToString { it.label.substringAfter(" · ") }}")
                if (st.readyInFaster && quality != SpeechQuality.FAST) QuietLabel("Works now in Faster mode.")
                Row {
                    if (st.switchOn.isNotEmpty()) TextButton(enabled = !listening.recording && !listening.processing, onClick = { st.switchOn.forEach { ModelPacks.setInUse(context, it, true) } },
                        modifier = Modifier.testTag("language-turn-on-${lang.code}")) { Text("Turn on") }
                    if (st.download.isNotEmpty()) TextButton(onClick = { openDownloads() }, modifier = Modifier.testTag("language-get-${lang.code}")) { Text("Download ${mb(st.downloadBytes)}") }
                    if (st.readyInFaster && quality != SpeechQuality.FAST && lang == language) TextButton(enabled = !listening.recording && !listening.processing,
                        onClick = { app.guidance.setSpeechQuality(SpeechQuality.FAST) }, modifier = Modifier.testTag("language-use-faster")) { Text("Use Faster mode") }
                }
            }
        }
    }
    HorizontalDivider()

    // ---- Files: size, languages covered, and what removing or switching off would change ----
    Text("Speech files", style = MaterialTheme.typography.titleSmall)
    ModelPacks.labels.forEach { (id, label) ->
        val megabytes = sizes.getValue(id) / 1_000_000
        val installed = id in installedNow
        val bundled = remember(revision) { installed && ModelPacks.bundled(context, id) }
        val inUse = id in inUseNow
        OutlinedCard(Modifier.fillMaxWidth().testTag("pack-$id")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$label · $megabytes MB", style = MaterialTheme.typography.titleSmall)
                QuietLabel(ModelPacks.usedFor[id].orEmpty())
                Text(when { !installed -> "Not on this phone"; !inUse -> "On this phone · switched off"; bundled -> "Included in this app · in use"; else -> "On this phone · in use" })
                if (installed) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(inUse, { on -> if (on) ModelPacks.setInUse(context, id, true) else confirm = id to false }, enabled = !listening.recording && !listening.processing,
                        modifier = Modifier.testTag("pack-use-$id").controlLabel("Use $label"))
                    Text("Use", Modifier.padding(start = 8.dp))
                }
                if (installed && !bundled) TextButton(enabled = !working && !listening.recording && !listening.processing, onClick = { confirm = id to true },
                    modifier = Modifier.testTag("pack-remove-$id")) { Text("Remove from phone (frees $megabytes MB)") }
                if (!installed && id == "korean") QuietLabel("Not offered for download yet: its training data's license still needs confirmation.")
                else if (!installed) {
                    val adds = remember(revision, quality) { LanguageReadiness.unlocks(id, quality, installedNow, inUseNow) }
                    if (adds.isNotEmpty()) QuietLabel("Makes ready: ${adds.joinToString { it.label.substringAfter(" · ") }}")
                    TextButton(modifier = Modifier.testTag("pack-get-$id"), onClick = { openDownloads() }) { Text("Download $megabytes MB (opens browser)") }
                }
            }
        }
    }
    confirm?.let { (id, remove) ->
        val megabytes = sizes.getValue(id) / 1_000_000
        val impact = LanguageReadiness.impact(id, quality, installedNow, inUseNow)
        val effect = listOfNotNull(
            impact.stops.takeIf { it.isNotEmpty() }?.let { "Stops working: ${it.joinToString { l -> l.label.substringAfter(" · ") }}." },
            impact.fasterOnly.takeIf { it.isNotEmpty() }?.let { "Still works in Faster mode only: ${it.joinToString { l -> l.label.substringAfter(" · ") }}." },
        ).joinToString(" ").ifEmpty { "No language you can use now stops working." }
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (remove) "Remove ${ModelPacks.labels[id]}?" else "Switch off ${ModelPacks.labels[id]}?") },
            text = { Text(if (remove) "$effect Deletes $megabytes MB from this phone; you can download it again later." else "$effect The files stay on this phone; switch them back on anytime, no download.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (!remove) ModelPacks.setInUse(context, id, false)
                else scope.launch { working = true; try { val freed = ModelPacks.remove(context, id); message = "Removed · freed ${freed / 1_000_000} MB" }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Couldn't remove the files. Try again." } finally { working = false } }
            }, modifier = Modifier.testTag("pack-confirm")) { Text(if (remove) "Remove" else "Switch off") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
    QuietLabel("Downloads come only from SideKick's own page. Each file is checked against a fingerprint built into the app before it is installed; any other file is refused. SideKick itself never connects to the internet.")
    TextButton(enabled = !working && !listening.recording && !listening.processing, modifier = Modifier.testTag("import-speech-pack"),
        onClick = { picker.launch(arrayOf("*/*")) }) { Text("Install speech file") }
    // SideKick has no network permission: the browser downloads the file, then "Install speech file" verifies it.
    TextButton(modifier = Modifier.testTag("download-speech-packs"), onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/tag/speech-packs"))) }) { Text("Get speech files (opens browser)") }
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/blob/main/docs/guides/MODEL_PACKS.md"))) }) { Text("How to install speech files") }
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
    AlertDialog(onDismissRequest = onClose, title = { Text("Get started") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You can type notes and use plans now. Follow these steps for speech.")
            Text("1. Choose speech language and quality", style = MaterialTheme.typography.titleMedium)
            Box {
                TextButton(enabled = editable, onClick = { languageMenu = true }, modifier = Modifier.testTag("setup-language-choice")) { Text("Language: ${language.label}") }
                DropdownMenu(languageMenu, { languageMenu = false }) { SpeechLanguage.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { if (choice == SpeechLanguage.AUTO) app.guidance.setAutomaticLanguage(true) else app.guidance.setSpeechLanguage(choice); app.voiceProfile.selectLanguage(choice); languageMenu = false }, modifier = Modifier.testTag("setup-language-${choice.code}"))
                } }
            }
            Box {
                TextButton(enabled = editable, onClick = { qualityMenu = true }, modifier = Modifier.testTag("setup-quality-choice")) { Text("Quality: ${speechQualityName(quality)}") }
                DropdownMenu(qualityMenu, { qualityMenu = false }) { SpeechQuality.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(speechQualityName(choice)) }, onClick = { app.guidance.setSpeechQuality(choice); qualityMenu = false }, modifier = Modifier.testTag("setup-quality-${choice.name}"))
                } }
            }
            ModelPackSection(initiallyExpanded = false)
            Text("2. Optional: set up your voice", style = MaterialTheme.typography.titleMedium)
            Text(if (voiceReady) "Your voice is set up for this language" else "Until your voice is set up for this language, plans you say are kept as information only.")
            TextButton(enabled = editable, onClick = { context.startActivity(Intent(context, com.sidekick.app.speaker.VoiceEnrollmentActivity::class.java)) }) { Text("Set up my voice") }
            Text("3. Android permissions", style = MaterialTheme.typography.titleMedium)
            val mic = remember(refresh) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
            Text("Microphone: ${if (mic) "Allowed" else "Needed for listening"}", modifier = Modifier.testTag("setup-microphone"))
            Text(remember(refresh) { "Reminder delivery: ${if (app.reminders.notificationsAllowed()) "Enabled" else "Blocked"}" })
            TextButton(onClick = { microphone.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
            TextButton(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Enable reminder alerts") }
            Text("4. Start listening", style = MaterialTheme.typography.titleMedium)
            Text("Return home and tap Start listening when everyone agrees. Voice matching does not prove consent. Settings has more options for listening, messages and places.")
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}

private fun packNames(ids: Set<String>) = ids.joinToString(" + ") { ModelPacks.labels[it] ?: it }
private fun mb(bytes: Long) = "${(bytes + 500_000) / 1_000_000} MB"
