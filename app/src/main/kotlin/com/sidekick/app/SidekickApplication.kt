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

class SidekickApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, MemoryDatabase::class.java, "sidekick.db").addMigrations(MemoryDatabase.MIGRATION_1_2, MemoryDatabase.MIGRATION_2_3, MemoryDatabase.MIGRATION_3_4, MemoryDatabase.MIGRATION_4_5).build() }
    val pipeline by lazy { CommitmentPipeline(RuleIntentExtractor()) }
    val speech by lazy { SpeechEngine(this, { guidance.speechLanguage.value }, { guidance.speechQuality.value }) }
    val voiceProfile by lazy { com.sidekick.app.speaker.VoiceProfile(this, guidance.speechLanguage.value) }
    val temporary = TemporaryMemory { guidance.includeInformational.value }
    val guidance by lazy { GuidanceSettings(this) }
    val intent by lazy { IntentEngine(this) }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val messageInbox by lazy { MessageInbox(guidance, temporary, background) { automation.process() } }
    val reminders by lazy { ReminderCoordinator(this, database.commitments()) }
    val automation by lazy { AutomaticMemory(this, guidance, temporary, database.commitments(), reminders) }
    val assistant by lazy { com.sidekick.app.assistant.AssistantControl(this) }
    override fun onCreate() {
        super.onCreate()
        guidance // Read persisted mode before any background worker starts.
        background.launch { runCatching { reminders.restore(); automation.restoreNotifications() } }
        background.launch { guidance.handsOffMode.collect { if (it) automation.process() } }
        background.launch { guidance.speechLanguage.collect { voiceProfile.selectLanguage(it) } }
    }
}
