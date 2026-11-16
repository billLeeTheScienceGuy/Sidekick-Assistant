package com.sidekick.app

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import com.sidekick.app.audio.ListeningService

/** Privacy settings for the recording and voice rules (tasks 3, 4.5) and the privacy policy link (5.5). */
@Composable
internal fun ConsentRulesSection(settings: GuidanceSettings) {
    val context = LocalContext.current
    val strictest by settings.strictestEverywhere.collectAsState()
    val voiceLine by settings.voiceLineEverywhere.collectAsState()
    val listening by ListeningService.state.collectAsState()
    val alwaysAnnounce by settings.alwaysShowAnnounce.collectAsState()
    Text("Conversation rules", style = MaterialTheme.typography.titleMedium)
    QuietLabel("SideKick follows the stricter of nearby states' recording and voice rules. Not legal advice.")
    SettingsToggle("Treat every place as the strictest state",
        "No location checks. Notice is always needed and voices are never grouped.", strictest, settings::setStrictestEverywhere, "strictest-everywhere")
    SettingsToggle("Group voices only after notice, in every state",
        "The notice always mentions voice analysis. With this on, voices are grouped only after it everywhere, as in Texas and Washington.", voiceLine, settings::setVoiceLineEverywhere,
        "voice-line-everywhere", !strictest)
    SettingsToggle("Always show the Announce button",
        "Announce or re-announce note-taking any time while listening, not only when a notice is needed.", alwaysAnnounce,
        settings::setAlwaysShowAnnounce, "always-show-announce")
    QuietLabel("A notice covers one conversation: it ends after 30 minutes without other voices, or after 2 hours.")
    if (listening.recording) QuietLabel("Changes apply now; turning off \"strictest\" applies from the next session.")
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, modifier = Modifier.testTag("privacy-policy")) { Text("Privacy policy") }
}

const val PRIVACY_POLICY_URL = "https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/blob/main/docs/PRIVACY_POLICY.md"
