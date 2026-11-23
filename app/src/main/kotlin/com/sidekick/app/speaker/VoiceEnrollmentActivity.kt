package com.sidekick.app.speaker

import com.sidekick.app.R
import com.sidekick.app.tr

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningService
import com.sidekick.app.SidekickApplication

/**
 * Written consent for the owner's own voice signature, shown before any recording (all states). The record (name, time,
 * notice version, method) is kept with the profile and deleted with it. Then user-initiated microphone setup; raw speech
 * never leaves bounded service buffers.
 */
@Composable
internal fun VoiceConsentNotice(onAgree: (com.sidekick.core.VoiceprintConsent) -> Unit, agreeLabel: String) {
    var name by rememberSaveable { mutableStateOf("") }
    var ticked by rememberSaveable { mutableStateOf(false) }
    var under18 by rememberSaveable { mutableStateOf(false) }
    var guardian by rememberSaveable { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    var current by remember { mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
    Card(Modifier.fillMaxWidth().testTag("voice-consent")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.your_voice_signature), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.what_is_saved) + com.sidekick.core.VoiceprintPolicy.NOTICE_SAVED)
            Text(tr(R.string.why) + com.sidekick.core.VoiceprintPolicy.NOTICE_PURPOSE)
            Text(tr(R.string.where) + com.sidekick.core.VoiceprintPolicy.NOTICE_WHERE)
            Text(tr(R.string.how_long) + com.sidekick.core.VoiceprintPolicy.NOTICE_RETENTION)
            Text(tr(R.string.delete_2) + com.sidekick.core.VoiceprintPolicy.NOTICE_DELETE)
            Text(tr(R.string.privacy_policy_docs_privacy_policy_md), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr(R.string.your_full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("consent-name"))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(ticked, { ticked = it }, modifier = Modifier.testTag("consent-checkbox"))
                Text(tr(R.string.i_consent_and_sign))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(under18, { under18 = it; if (!it) guardian = false }, modifier = Modifier.testTag("consent-under18"))
                Text(tr(R.string.i_m_under_18))
            }
            if (under18) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(guardian, { guardian = it }, modifier = Modifier.testTag("consent-guardian"))
                Text(tr(R.string.my_parent_or_guardian_agrees))
            }
            Text(tr(R.string.signature_optional), style = MaterialTheme.typography.bodySmall)
            val ink = MaterialTheme.colorScheme.onSurface
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp).clipToBounds()
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { current = listOf(it) }, onDragEnd = { strokes += current; current = emptyList() }) { change, _ ->
                        current = current + change.position
                    }
                }) {
                (strokes + listOf(current)).forEach { stroke ->
                    stroke.zipWithNext { a, b -> drawLine(ink, a, b, strokeWidth = 4f) }
                }
            }
            if (strokes.isNotEmpty()) TextButton(onClick = { strokes.clear() }) { Text(tr(R.string.clear_signature)) }
            Button(enabled = com.sidekick.core.VoiceprintPolicy.canAgree(name, ticked, under18, guardian), onClick = {
                onAgree(com.sidekick.core.VoiceprintConsent(name.trim(), System.currentTimeMillis(), com.sidekick.core.VoiceprintPolicy.NOTICE_VERSION,
                    if (strokes.isNotEmpty()) tr(R.string.checkbox_signature) else "checkbox", guardian = under18 && guardian))
                strokes.clear() // the drawing itself is not kept
            }, modifier = Modifier.testTag("consent-agree")) { Text(agreeLabel) }
        }
    }
}

class VoiceEnrollmentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(com.sidekick.app.AppLanguage.wrap(newBase)) }
    companion object {
        const val CONSENT_ONLY = "com.sidekick.VOICE_CONSENT_ONLY"
        /** Opens only the optional SideKick voice check (its own screen, from Settings → Your voice). */
        const val WAKE_ONLY = "com.sidekick.WAKE_ONLY"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        com.sidekick.app.CrashReports.screen = "Voice setup"
        super.onCreate(savedInstanceState)
        setContent {
            com.sidekick.app.SidekickAppTheme((application as SidekickApplication).guidance) {
                val state by ListeningService.state.collectAsState()
                val selectedModel by (application as com.sidekick.app.SidekickApplication).voiceProfile.model.collectAsState()
                val language by (application as com.sidekick.app.SidekickApplication).guidance.speechLanguage.collectAsState()
                val enrolled by (application as SidekickApplication).voiceProfile.enrolled.collectAsState()
                val savedSettings by (application as SidekickApplication).voiceProfile.settings.collectAsState()
                var settingName by rememberSaveable { mutableStateOf(VoiceSetting.QUIET.name) }
                val setting = VoiceSetting.valueOf(settingName)
                val consentRecord by (application as SidekickApplication).voiceProfile.consent.collectAsState()
                val consentOnly = intent.getBooleanExtra(CONSENT_ONLY, false)
                var error by remember { mutableStateOf<String?>(null) }
                // This screen sets up either the voice profile or (WAKE_ONLY) the optional SideKick voice check.
                val wakeSetup = intent.getBooleanExtra(WAKE_ONLY, false)
                var wakeAttempted by rememberSaveable { mutableStateOf(false) }
                val wakeOn by (application as SidekickApplication).voiceProfile.wakeOn.collectAsState()
                val wakeSaved by (application as SidekickApplication).voiceProfile.wakeSaved.collectAsState()
                val auto = language == com.sidekick.core.SpeechLanguage.AUTO
                // Auto-detect has no single language; the user picks which language profile to record.
                var chosen by rememberSaveable { mutableStateOf(com.sidekick.core.SpeechLanguage.ENGLISH.code) }
                val target = if (auto) com.sidekick.core.SpeechLanguage.fromCode(chosen) else language
                val active = state.recording || state.processing
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    if (grants[Manifest.permission.RECORD_AUDIO] == true && (Build.VERSION.SDK_INT < 33 || grants[Manifest.permission.POST_NOTIFICATIONS] == true)) {
                        runCatching { startForegroundService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).putExtra(ListeningService.ENROLL, true).putExtra(ListeningService.ENROLL_LANGUAGE, target.code).putExtra(ListeningService.ENROLL_SETTING, setting.name).putExtra(ListeningService.ENROLL_WAKE, wakeSetup)) }
                            .onFailure { error = tr(R.string.open_sidekick_and_try_voice_setup) }
                    } else error = tr(R.string.microphone_and_notification_permissions_are_needed)
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (consentOnly) {
                            Text(tr(R.string.voice_consent), style = MaterialTheme.typography.headlineMedium)
                            if (consentRecord?.valid == true) {
                                Text(tr(R.string.thanks_voice_matching_is_on))
                                Button(onClick = { finish() }) { Text(tr(R.string.back_to_sidekick)) }
                            } else {
                                Text(tr(R.string.your_saved_voice_setup_needs_your))
                                VoiceConsentNotice({ (application as SidekickApplication).voiceProfile.recordConsent(it) }, tr(R.string.agree))
                                TextButton(onClick = { finish() }) { Text(tr(R.string.not_now)) }
                            }
                            return@Column
                        }
                        fun askMicrophone() {
                            error = null
                            permission.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                        if (wakeSetup) {
                            Text(tr(R.string.sidekick_voice_check), style = MaterialTheme.typography.headlineMedium)
                            Text(tr(R.string.optional_sidekick_learns_how_you_say))
                            Text(tr(R.string.never_overrides_someone_else_s_voice),
                                style = MaterialTheme.typography.bodySmall)
                            if (wakeSaved) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Switch(wakeOn, { (application as SidekickApplication).voiceProfile.setWakeOn(it) }, enabled = !active, modifier = Modifier.testTag("wake-check"))
                                Spacer(Modifier.width(8.dp))
                                Text(if (wakeOn) tr(R.string.on) else tr(R.string.off))
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(tr(R.string.how_to_record), style = MaterialTheme.typography.titleMedium)
                                    Text(tr(R.string.read_each_one_aloud_with_a))
                                    val detectNow by (application as SidekickApplication).guidance.detectLanguages.collectAsState()
                                    com.sidekick.app.AskExamples(listOf(com.sidekick.app.askLanguages(language, detectNow).first()), numbered = true)
                                }
                            }
                            if (active && state.enrolling) {
                                Text("${state.voiceSamples}/${com.sidekick.core.WakeVoice.SAMPLES}", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("wake-count"))
                                Text(state.note)
                                OutlinedButton(onClick = { startService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).setAction(ListeningService.CANCEL)) }) { Text(tr(R.string.cancel)) }
                            } else {
                                if (wakeAttempted) Text(state.note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("wake-result"))
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (active) Text(tr(R.string.stop_listening_first_then_come_back))
                                else if (consentRecord?.valid != true) VoiceConsentNotice({ (application as SidekickApplication).voiceProfile.recordConsent(it); wakeAttempted = true; askMicrophone() }, tr(R.string.agree_record))
                                else Button(onClick = { wakeAttempted = true; askMicrophone() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wake-setup")) {
                                    Text(if (wakeAttempted && !wakeSaved) tr(R.string.try_again) else if (wakeSaved) tr(R.string.record_again) else tr(R.string.start))
                                }
                                if (wakeSaved) TextButton(onClick = { (application as SidekickApplication).voiceProfile.deleteWake() }) { Text(tr(R.string.delete_sidekick_voice_check)) }
                            }
                            TextButton(onClick = { finish() }) { Text(tr(R.string.back)) }
                            return@Column
                        }
                        Text(tr(R.string.set_up_my_voice), style = MaterialTheme.typography.headlineMedium)
                                                val dates = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                        Text(tr(R.string.saved_profiles) + if (enrolled.isEmpty()) "none" else enrolled.entries.joinToString(", ") { "${it.key.label} (${dates.format(java.util.Date(it.value))})" })
                        if (auto) {
                            Text(tr(R.string.choose_the_language_to_record))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { option ->
                                    FilterChip(target == option, { chosen = option.code }, label = { Text(option.label) }, enabled = !active)
                                }
                            }
                        } else Text(tr(R.string.voice_setup_language_1_s, language.label))
                        val haveSettings = savedSettings[target].orEmpty()
                        Text(tr(R.string.where_are_you_setting_up_in))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceSetting.entries.forEach { option ->
                                FilterChip(setting == option, { settingName = option.name }, enabled = !active,
                                    label = { Text(option.label + if (option in haveSettings) " ✓" else "") },
                                    modifier = Modifier.testTag("voice-setting-${option.name.lowercase()}"))
                            }
                        }
                        Text(if (setting in haveSettings) tr(R.string.replaces_your_1_s_2_s, target.label, setting.label.lowercase())
                            else tr(R.string.adds_a_1_s_2_s, target.label, setting.label.lowercase()))
                                                Text(tr(R.string.n_1_s_only_you_talking_read, setting.hint, target.label))
                        Text(tr(R.string.only_a_voice_signature_is_saved))
                        Text(when (target) {
                            com.sidekick.core.SpeechLanguage.ARABIC -> "اقرأ بصوت طبيعي حتى يكتمل الإعداد. غدا سألتقي بصديق لتناول الغداء، وسنتحدث عن خططنا لعطلة نهاية الأسبوع. أحب المشي في الهواء الطلق وزيارة أماكن جديدة. هذا الأسبوع لدي أشياء كثيرة أريد القيام بها، وفي المساء أحب أن أستريح وأستمع إلى الموسيقى. واصل الحديث بشكل طبيعي عن حياتك اليومية حتى ينتهي الإعداد."
                            com.sidekick.core.SpeechLanguage.KOREAN -> "자연스럽게 계속 읽어 주세요. 내일은 친구를 만나서 점심을 먹을 거예요. 우리는 주말에 무엇을 할지 이야기할 거예요. 저는 날씨가 좋으면 밖에서 산책하는 것을 좋아해요. 새로운 장소를 찾아가고 맛있는 음식을 먹는 것도 좋아해요. 이번 주에는 해야 할 일이 많지만 저녁에는 편하게 쉬고 싶어요. 설정이 끝날 때까지 자연스럽게 이야기를 계속해 주세요."
                            com.sidekick.core.SpeechLanguage.CHINESE -> "请自然地继续说话。明天我要和朋友一起吃午饭。我们会聊聊周末的计划。天气好的时候，我喜欢出去散步，看看新的地方。我也喜欢做饭和听音乐。这周我有很多事情要做，不过晚上我想好好休息。请继续讲讲你平时的生活，直到设置完成。"
                            com.sidekick.core.SpeechLanguage.FRENCH -> "Demain, je vais déjeuner avec un ami. Nous parlerons de nos projets pour le week-end. Quand il fait beau, j'aime me promener et découvrir de nouveaux endroits. Cette semaine, j'ai beaucoup de choses à faire, mais je voudrais me reposer le soir. Continuez à parler naturellement de votre quotidien jusqu'à la fin de la configuration."
                            com.sidekick.core.SpeechLanguage.GERMAN -> "Morgen treffe ich einen Freund zum Mittagessen. Wir sprechen über unsere Pläne für das Wochenende. Bei schönem Wetter gehe ich gerne spazieren und entdecke neue Orte. Diese Woche habe ich viel zu tun, aber am Abend möchte ich mich ausruhen. Sprechen Sie bis zum Ende der Einrichtung ganz natürlich über Ihren Alltag weiter."
                            com.sidekick.core.SpeechLanguage.RUSSIAN -> "Завтра я встречусь с другом за обедом. Мы поговорим о планах на выходные. В хорошую погоду я люблю гулять и открывать новые места. На этой неделе у меня много дел, но вечером я хочу отдохнуть. Продолжайте естественно рассказывать о своей повседневной жизни, пока настройка не закончится."
                            else -> "Try: Tomorrow I will meet a friend for lunch. We will talk about our plans for the weekend. I enjoy walking outside and finding new places to visit. Keep speaking naturally until setup finishes."
                        })
                        Text(if (active && state.enrolling) tr(R.string.n_1_s_3_voice_samples_2, state.voiceSamples, state.seconds) else state.note)
                        if (active && state.enrolling) Text(state.note)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        fun startSetup() = askMicrophone()
                        val record = consentRecord
                        if (record?.valid != true) {
                            // Nothing is recorded before this consent (Illinois BIPA written release; applied in every state).
                            if (!active) VoiceConsentNotice({ (application as SidekickApplication).voiceProfile.recordConsent(it); startSetup() }, tr(R.string.agree_record))
                        } else {
                            Text(tr(R.string.you_agreed_as_1_s_on, record.name, dates.format(java.util.Date(record.atEpochMs))), style = MaterialTheme.typography.bodySmall)
                            Button(enabled = !active, onClick = { startSetup() }) { Text(tr(R.string.start_voice_setup)) }
                        }
                        if (active && state.enrolling) TextButton(onClick = {
                            startService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).setAction(ListeningService.CANCEL))
                        }) { Text(tr(R.string.cancel_setup)) }
                        if (active && !state.enrolling) Text(tr(R.string.finish_the_current_listening_session_before))
                        TextButton(onClick = { finish() }) { Text(tr(R.string.back_to_sidekick)) }
                    }
                }
            }
        }
    }
}
