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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidekick.app.audio.ListeningService
import com.sidekick.app.SidekickApplication

/** User-initiated microphone setup; raw speech never leaves bounded service buffers. */
class VoiceEnrollmentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.sidekick.app.SidekickAppTheme((application as SidekickApplication).guidance) {
                val state by ListeningService.state.collectAsState()
                val selectedModel by (application as com.sidekick.app.SidekickApplication).voiceProfile.model.collectAsState()
                val language by (application as com.sidekick.app.SidekickApplication).guidance.speechLanguage.collectAsState()
                val enrolled by (application as SidekickApplication).voiceProfile.enrolled.collectAsState()
                var error by remember { mutableStateOf<String?>(null) }
                val auto = language == com.sidekick.core.SpeechLanguage.AUTO
                // Auto-detect has no single language; the user picks which language profile to record.
                var chosen by rememberSaveable { mutableStateOf(com.sidekick.core.SpeechLanguage.ENGLISH.code) }
                val target = if (auto) com.sidekick.core.SpeechLanguage.fromCode(chosen) else language
                val active = state.recording || state.processing
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    if (grants[Manifest.permission.RECORD_AUDIO] == true && (Build.VERSION.SDK_INT < 33 || grants[Manifest.permission.POST_NOTIFICATIONS] == true)) {
                        runCatching { startForegroundService(Intent(this@VoiceEnrollmentActivity, ListeningService::class.java).putExtra(ListeningService.ENROLL, true).putExtra(ListeningService.ENROLL_LANGUAGE, target.code)) }
                            .onFailure { error = "Open SideKick and try voice setup again." }
                    } else error = "Microphone and notification permissions are needed for setup."
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Set up my voice", style = MaterialTheme.typography.headlineMedium)
                        Text("Speaker model: ${selectedModel.label}")
                        val dates = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                        Text("Saved profiles: " + if (enrolled.isEmpty()) "none" else enrolled.entries.joinToString(", ") { "${it.key.label} (${dates.format(java.util.Date(it.value))})" })
                        if (auto) {
                            Text("Listening language is Auto-detect, which checks every saved profile. Choose which language to record now:")
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.sidekick.core.SpeechLanguage.entries.filter { it != com.sidekick.core.SpeechLanguage.AUTO }.forEach { option ->
                                    FilterChip(target == option, { chosen = option.code }, label = { Text(option.label) }, enabled = !active)
                                }
                            }
                        } else Text("Voice setup language: ${language.label}")
                        Text(enrolled[target]?.let { "This replaces your ${target.label} profile from ${dates.format(java.util.Date(it))}. Your other profiles are kept." }
                            ?: "This creates your ${target.label} profile. Your other profiles are kept.")
                        Text("Speak ${target.label} for this setup. English and Auto-detect share the English profile.")
                        Text("Do this alone in a quiet room. Keep the phone at your usual listening distance (about 30–50 cm). Read three different sentences, at least four seconds each, with a two-second pause between them. Each sample is checked for consistent matching. Setup stops after three samples or 45 seconds.")
                        Text("Only a local voice signature is saved. Audio is discarded; enrollment is never transcribed. Speaker matches can be wrong and do not establish consent.")
                        Text(when (target) {
                            com.sidekick.core.SpeechLanguage.ARABIC -> "اقرأ بصوت طبيعي حتى يكتمل الإعداد. غدا سألتقي بصديق لتناول الغداء، وسنتحدث عن خططنا لعطلة نهاية الأسبوع. أحب المشي في الهواء الطلق وزيارة أماكن جديدة. هذا الأسبوع لدي أشياء كثيرة أريد القيام بها، وفي المساء أحب أن أستريح وأستمع إلى الموسيقى. واصل الحديث بشكل طبيعي عن حياتك اليومية حتى ينتهي الإعداد."
                            com.sidekick.core.SpeechLanguage.KOREAN -> "자연스럽게 계속 읽어 주세요. 내일은 친구를 만나서 점심을 먹을 거예요. 우리는 주말에 무엇을 할지 이야기할 거예요. 저는 날씨가 좋으면 밖에서 산책하는 것을 좋아해요. 새로운 장소를 찾아가고 맛있는 음식을 먹는 것도 좋아해요. 이번 주에는 해야 할 일이 많지만 저녁에는 편하게 쉬고 싶어요. 설정이 끝날 때까지 자연스럽게 이야기를 계속해 주세요."
                            com.sidekick.core.SpeechLanguage.CHINESE -> "请自然地继续说话。明天我要和朋友一起吃午饭。我们会聊聊周末的计划。天气好的时候，我喜欢出去散步，看看新的地方。我也喜欢做饭和听音乐。这周我有很多事情要做，不过晚上我想好好休息。请继续讲讲你平时的生活，直到设置完成。"
                            com.sidekick.core.SpeechLanguage.FRENCH -> "Demain, je vais déjeuner avec un ami. Nous parlerons de nos projets pour le week-end. Quand il fait beau, j'aime me promener et découvrir de nouveaux endroits. Cette semaine, j'ai beaucoup de choses à faire, mais je voudrais me reposer le soir. Continuez à parler naturellement de votre quotidien jusqu'à la fin de la configuration."
                            com.sidekick.core.SpeechLanguage.GERMAN -> "Morgen treffe ich einen Freund zum Mittagessen. Wir sprechen über unsere Pläne für das Wochenende. Bei schönem Wetter gehe ich gerne spazieren und entdecke neue Orte. Diese Woche habe ich viel zu tun, aber am Abend möchte ich mich ausruhen. Sprechen Sie bis zum Ende der Einrichtung ganz natürlich über Ihren Alltag weiter."
                            com.sidekick.core.SpeechLanguage.RUSSIAN -> "Завтра я встречусь с другом за обедом. Мы поговорим о планах на выходные. В хорошую погоду я люблю гулять и открывать новые места. На этой неделе у меня много дел, но вечером я хочу отдохнуть. Продолжайте естественно рассказывать о своей повседневной жизни, пока настройка не закончится."
                            else -> "Try: Tomorrow I will meet a friend for lunch. We will talk about our plans for the weekend. I enjoy walking outside and finding new places to visit. Keep speaking naturally until setup finishes."
                        })
                        Text(if (active && state.enrolling) "${state.voiceSamples}/3 voice samples · ${state.seconds}s" else state.note)
                        if (active && state.enrolling) Text(state.note)
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
