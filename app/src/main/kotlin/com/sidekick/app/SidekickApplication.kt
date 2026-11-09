package com.sidekick.app

import android.app.Application
import androidx.room.Room
import com.sidekick.app.data.MemoryDatabase
import com.sidekick.core.CommitmentPipeline
import com.sidekick.core.RuleIntentExtractor
import com.sidekick.app.speech.SpeechEngine
import com.sidekick.app.intent.IntentEngine
import com.sidekick.app.reminders.ReminderCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

class SidekickApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, MemoryDatabase::class.java, "sidekick.db").addMigrations(MemoryDatabase.MIGRATION_1_2, MemoryDatabase.MIGRATION_2_3, MemoryDatabase.MIGRATION_3_4, MemoryDatabase.MIGRATION_4_5, MemoryDatabase.MIGRATION_5_6).build() }
    val pipeline by lazy { CommitmentPipeline(RuleIntentExtractor()) }
    val speech by lazy { SpeechEngine(this, { guidance.speechLanguage.value }, { guidance.speechQuality.value }) }
    val voiceProfile by lazy { com.sidekick.app.speaker.VoiceProfile(this, guidance.speechLanguage.value) }
    val temporary = TemporaryMemory { guidance.includeInformational.value }
    val guidance by lazy { GuidanceSettings(this) }
    val intent by lazy { IntentEngine(this) }
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
        background.launch { runCatching { reminders.restore(); automation.restoreNotifications() } }
        background.launch { guidance.handsOffMode.collect { if (it) automation.process() } }
        background.launch { guidance.speechLanguage.collect { voiceProfile.selectLanguage(it) } }
        background.launch { guidance.riskAdvisories.collect { if (!it) risk.clear() } }
        background.launch { guidance.messageNotifications.collect { if (it) MessageNotificationListener.requestConnection(this@SidekickApplication) } }
        background.launch { guidance.retentionDays.collectLatest { days ->
            if (days != 0) while (isActive) {
                try { reminders.prune(days) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Retry on the next daily check or process start. */ }
                delay(86_400_000)
            }
        } }
    }
}
