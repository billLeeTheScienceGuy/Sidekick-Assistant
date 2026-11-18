package com.sidekick.app

import android.app.Application
import androidx.room.Room
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.core.CommitmentPipeline
import com.sidekick.core.RuleIntentExtractor
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.reminders.ReminderCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SidekickApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, MemoryDatabase::class.java, "sidekick.db").addMigrations(MemoryDatabase.MIGRATION_1_2, MemoryDatabase.MIGRATION_2_3, MemoryDatabase.MIGRATION_3_4, MemoryDatabase.MIGRATION_4_5, MemoryDatabase.MIGRATION_5_6, MemoryDatabase.MIGRATION_6_7, MemoryDatabase.MIGRATION_7_8).build() }
    val pipeline by lazy { CommitmentPipeline(RuleIntentExtractor()) }
    val speech by lazy { SpeechEngine(this, { guidance.speechLanguage.value }, { guidance.speechQuality.value }, { guidance.spokenNames.value },
        { if (guidance.learnNames.value) guidance.learnedNames.value else emptySet() }, { guidance.detectLanguages.value }) }
    val voiceProfile by lazy { com.sidekick.app.speaker.VoiceProfile(this, guidance.speechLanguage.value) }
    val temporary = TemporaryMemory { guidance.includeInformational.value }
    val guidance by lazy { GuidanceSettings(this) }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val risk by lazy { LocalRiskAlerts(this) { guidance.riskAdvisories.value } }
    val messageInbox by lazy { MessageInbox(guidance, temporary, background, onRisk = risk::inspect) { automation.process() } }
    val reminders by lazy { ReminderCoordinator(this, database.commitments()) }
    val calendarMirror by lazy { CalendarMirror(this, database, guidance) }
    val automation by lazy { AutomaticMemory(this, guidance, temporary, database.commitments(), reminders) }
    val assistant by lazy { com.sidekick.app.assistant.AssistantControl(this) }
    override fun onCreate() {
        super.onCreate()
        guidance // Read persisted mode before any background worker starts.
        calendarMirror.start(background)
        // Home-screen widget: redraw when plans change or listening starts or stops.
        background.launch { kotlinx.coroutines.flow.combine(database.commitments().observe(),
            com.sidekick.app.audio.ListeningService.state.map { it.recording }.distinctUntilChanged()) { _, _ -> Unit }
            .collect { runCatching { PlanWidget.render(this@SidekickApplication) } } }
        // Korean names in saved plans (not Review, not dismissed) feed the names list used to correct Korean speech.
        background.launch { kotlinx.coroutines.flow.combine(database.commitments().observe(), guidance.learnNames, guidance.keepLearnedNames) { saved, learn, keep ->
            Triple(saved, learn, keep)
        }.collect { (saved, learn, keep) ->
            if (!learn) return@collect
            val found = com.sidekick.core.NameCorrection.namesIn(saved.filter { it.state == "ACCEPTED" || it.state == "DONE" }.flatMap { listOf(it.title, it.evidence) })
            guidance.setLearnedNames(if (keep) guidance.learnedNames.value + found else found)
        } }
        background.launch { runCatching { reminders.restore(); automation.restoreNotifications() } }
        background.launch { guidance.handsOffMode.collect { if (it) automation.process() } }
        background.launch { guidance.speechLanguage.collect { voiceProfile.selectLanguage(it) } }
        // Owner voiceprint retention: delete the profile and its consent record after 12 months unused.
        background.launch { runCatching { voiceProfile.purgeIfExpired() } }
        // The optional Qwen model was removed (rules matched or beat it on every test case); free its 484 MB.
        background.launch { runCatching { java.io.File(noBackupFilesDir, "qwen3-0.6b-q4_k_m.gguf").delete(); java.io.File(noBackupFilesDir, "intent-model.import").delete() } }
        background.launch { guidance.riskAdvisories.collect { if (!it) risk.clear() } }
        background.launch { guidance.messageNotifications.collect { if (it) MessageNotificationListener.requestConnection(this@SidekickApplication) } }
        background.launch { guidance.retentionDays.collectLatest { days ->
            if (days != 0) while (isActive) {
                try { reminders.prune(days); database.noticeRecords().deleteBefore(System.currentTimeMillis() - days * 86_400_000L) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Retry on the next daily check or process start. */ }
                delay(86_400_000)
            }
        } }
    }
}
