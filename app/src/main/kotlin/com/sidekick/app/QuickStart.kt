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
    Text(if (missing.isEmpty()) "Speech is ready" else "Install for this language: ${missing.joinToString { ModelPacks.labels[it].orEmpty() }}", modifier = Modifier.testTag("model-readiness"))
    if (missing.isEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide speech files" else "Manage speech files") }
    if (expanded || missing.isNotEmpty()) {
    Text("English Faster mode and voice matching are included (built in). Base files make a language work; the others improve accuracy. " +
        "Installed files can be switched off without deleting them. Speech stays on this phone.")
    var removing by remember { mutableStateOf<String?>(null) }
    ModelPacks.labels.forEach { (id, label) ->
        val megabytes = remember { ModelPacks.bytes(context, id) / 1_000_000 }
        val installed = remember(revision) { ModelPacks.installed(context, id) }
        val bundled = remember(revision) { installed && ModelPacks.bundled(context, id) }
        val inUse = remember(revision) { ModelPacks.inUse(context, id) }
        OutlinedCard(Modifier.fillMaxWidth().testTag("pack-$id")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$label · $megabytes MB", style = MaterialTheme.typography.titleSmall)
                QuietLabel(ModelPacks.usedFor[id].orEmpty())
                Text(when { !installed -> "Not on this phone"; !inUse -> "On this phone · switched off"; bundled -> "Included in this app · in use"; else -> "On this phone · in use" })
                if (installed) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(inUse, { ModelPacks.setInUse(context, id, it) }, enabled = !listening.recording && !listening.processing,
                        modifier = Modifier.testTag("pack-use-$id").controlLabel("Use $label"))
                    Text("Use", Modifier.padding(start = 8.dp))
                }
                if (installed && !bundled) TextButton(enabled = !working && !listening.recording && !listening.processing, onClick = { removing = id },
                    modifier = Modifier.testTag("pack-remove-$id")) { Text("Remove from phone (frees $megabytes MB)") }
                if (!installed && id == "korean") QuietLabel("Not offered for download yet: its training data's license still needs confirmation.")
                else if (!installed) TextButton(modifier = Modifier.testTag("pack-get-$id"), onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/releases/tag/speech-packs")))
                }) { Text("Download $megabytes MB (opens browser)") }
            }
        }
    }
    removing?.let { id ->
        val megabytes = ModelPacks.bytes(context, id) / 1_000_000
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${ModelPacks.labels[id]}?") },
            text = { Text("Deletes $megabytes MB of speech files from this phone. You can download and install them again later.") },
            confirmButton = { TextButton(onClick = {
                removing = null
                scope.launch { working = true; try { val freed = ModelPacks.remove(context, id); message = "Removed · freed ${freed / 1_000_000} MB" }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Couldn't remove the files. Try again." } finally { working = false } }
            }, modifier = Modifier.testTag("pack-remove-confirm")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
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
