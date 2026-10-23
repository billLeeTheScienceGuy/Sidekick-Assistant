package com.sidekick.app.speaker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningService

/** User-initiated microphone setup; raw speech never leaves bounded service buffers. */
class VoiceEnrollmentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by ListeningService.state.collectAsState()
                var error by remember { mutableStateOf<String?>(null) }
                val active = state.recording || state.processing
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    if (grants[Manifest.permission.RECORD_AUDIO] == true && (Build.VERSION.SDK_INT < 33 || grants[Manifest.permission.POST_NOTIFICATIONS] == true)) {
                        runCatching { startForegroundService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).putExtra(ListeningService.ENROLL, true)) }
                            .onFailure { error = "Open SideKick and try voice setup again." }
                    } else error = "Microphone and notification permissions are needed for setup."
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Set up my voice", style = MaterialTheme.typography.headlineMedium)
                        Text("Do this alone in a quiet room. Speak naturally for about 20 seconds, in long phrases. Setup stops after three samples or 45 seconds.")
                        Text("Only a local voice signature is saved. Audio is discarded; enrollment is never transcribed. Speaker matches can be wrong and do not establish consent.")
                        Text("Try: Tomorrow I will meet a friend for lunch. We will talk about our plans for the weekend. I enjoy walking outside and finding new places to visit. Keep speaking naturally until setup finishes.")
                        Text(if (active && state.enrolling) "${state.voiceSamples}/3 voice samples · ${state.seconds}s" else state.note)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(enabled = !active, onClick = {
                            error = null
                            permission.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
                        }) { Text("Start voice setup") }
                        if (active && state.enrolling) TextButton(onClick = {
                            startService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).setAction(ListeningService.CANCEL))
                        }) { Text("Cancel setup") }
                        if (active && !state.enrolling) Text("Finish the current listening session before setting up your voice.")
                        TextButton(onClick = { finish() }) { Text("Back to SideKick") }
                    }
                }
            }
        }
    }
}
