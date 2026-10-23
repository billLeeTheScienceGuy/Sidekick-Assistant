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

class SidekickApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, MemoryDatabase::class.java, "sidekick.db").addMigrations(MemoryDatabase.MIGRATION_1_2, MemoryDatabase.MIGRATION_2_3, MemoryDatabase.MIGRATION_3_4).build() }
    val pipeline by lazy { CommitmentPipeline(RuleIntentExtractor()) }
    val speech by lazy { SpeechEngine(this) { guidance.speechLanguage.value } }
    val voiceProfile by lazy { com.sidekick.app.speaker.VoiceProfile(this) }
    val temporary = TemporaryMemory { guidance.includeInformational.value }
    val guidance by lazy { GuidanceSettings(this) }
    val intent by lazy { IntentEngine(this) }
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val messageInbox by lazy { MessageInbox(guidance, temporary, background) }
    val reminders by lazy { ReminderCoordinator(this, database.commitments()) }
    override fun onCreate() {
        super.onCreate()
        background.launch { runCatching { reminders.restore() } }
    }
}
