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
    val autoAnnounce by settings.autoAnnounce.collectAsState()
    Text(tr(R.string.conversation_rules), style = MaterialTheme.typography.titleMedium)
    QuietLabel(tr(R.string.follows_the_stricter_of_nearby_states))
    SettingsToggle(tr(R.string.treat_every_place_as_the_strictest),
        tr(R.string.no_location_checks_notice_always_needed), strictest, settings::setStrictestEverywhere, "strictest-everywhere")
    SettingsToggle(tr(R.string.group_voices_only_after_notice_in),
        tr(R.string.the_notice_always_mentions_voice_analysis), voiceLine, settings::setVoiceLineEverywhere,
        "voice-line-everywhere", !strictest)
    SettingsToggle(tr(R.string.always_show_the_announce_button),
        tr(R.string.announce_note_taking_anytime_while_listening), alwaysAnnounce,
        settings::setAlwaysShowAnnounce, "always-show-announce")
    SettingsToggle(tr(R.string.announce_automatically_when_a_new_conversation),
        tr(R.string.plays_the_notice_when_a_new), autoAnnounce,
        settings::setAutoAnnounce, "auto-announce")
    QuietLabel(tr(R.string.the_notice_must_play_at_half))
    QuietLabel(tr(R.string.a_notice_lasts_one_conversation_until))
    if (listening.recording) QuietLabel(tr(R.string.changes_apply_now_turning_off_strictest))
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, modifier = Modifier.testTag("privacy-policy")) { Text(tr(R.string.privacy_policy)) }
}

const val PRIVACY_POLICY_URL = "https://github.com/billLeeTheScienceGuy/Sidekick-Assistant/blob/main/docs/PRIVACY_POLICY.md"
