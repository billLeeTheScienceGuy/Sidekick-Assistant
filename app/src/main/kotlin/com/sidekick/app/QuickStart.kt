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
            working = true; message = "Verifying model pack…"
            try { val id = ModelPacks.importPack(context, it); message = "Installed: ${ModelPacks.labels[id]}" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Could not install model pack." }
            finally { working = false }
        }
    } }
    val missing = remember(language, quality, revision) { ModelPacks.missing(context, language, quality) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Text("Speech files", style = MaterialTheme.typography.titleMedium)
    Text(if (missing.isEmpty()) "Speech is ready" else "Install for this language: ${missing.joinToString { ModelPacks.labels[it].orEmpty() }}", modifier = Modifier.testTag("model-readiness"))
    if (missing.isEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide speech files" else "Manage speech files") }
    if (expanded || missing.isNotEmpty()) {
    Text("English Faster mode and voice matching are included. Install other speech files once. Speech stays on this phone.")
    Text("Extra files can help check unclear Korean times.")
    Text("Auto-detect uses the multilingual file. Other installed files can help with short phrases. Unclear times stay unknown.")
    ModelPacks.labels.forEach { (id, label) ->
        val rows = remember(revision) { ModelPacks.catalog(context).filter { it.pack == id } }
        val ready = remember(revision) { rows.all { ModelPacks.available(context, it.name) } }
        Text("$label · ${rows.sumOf { it.bytes } / 1_000_000} MB · ${if (ready) "Ready" else "Not installed"}")
    }
    TextButton(enabled = !working && !listening.recording && !listening.processing, modifier = Modifier.testTag("import-speech-pack"),
        onClick = { picker.launch(arrayOf("*/*")) }) { Text("Install speech file") }
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/blob/main/docs/MODEL_PACKS.md"))) }) { Text("How to install speech files") }
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
            Text("2. Optional personal voice setup", style = MaterialTheme.typography.titleMedium)
            Text(if (voiceReady) "Voice profile ready for this language" else "Without a matching voice profile, speech remains information-only.")
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
            Text("Return home and tap Start listening when everyone agrees. Voice matching does not prove consent. Settings has options for recovery, messages and places.")
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}
