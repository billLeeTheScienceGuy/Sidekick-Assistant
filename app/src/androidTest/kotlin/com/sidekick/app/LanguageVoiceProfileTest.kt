package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speaker.*
import com.sidekick.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LanguageVoiceProfileTest {
    @Test fun languageSetupPreservesExistingEnglishAndModelProfilesAcrossReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val profile = VoiceProfile(context); val previous = profile.model.value
        val english = FloatArray(192) { if (it == 0) 1f else 0f }
        val korean = FloatArray(192) { if (it == 1) 1f else 0f }
        try {
            profile.selectModel(SpeakerModel.CAMPPLUS)
            profile.save(listOf(english, english, english))
            profile.selectLanguage(SpeechLanguage.KOREAN)
            assertFalse(profile.ready.value); assertNull(profile.snapshot())
            profile.save(listOf(korean, korean, korean))
            assertArrayEquals(korean, VoiceProfile(context, SpeechLanguage.KOREAN).snapshot(), .000001f)
            assertArrayEquals(english, VoiceProfile(context).snapshot(), .000001f)
            profile.selectModel(SpeakerModel.RESNET); assertFalse(profile.ready.value)
            profile.selectModel(SpeakerModel.CAMPPLUS); assertArrayEquals(korean, profile.snapshot(), .000001f)
            profile.selectLanguage(SpeechLanguage.FRENCH); assertFalse(profile.ready.value)
            profile.selectLanguage(SpeechLanguage.ENGLISH); assertArrayEquals(english, profile.snapshot(), .000001f)
            profile.delete()
            assertFalse(VoiceProfile(context, SpeechLanguage.KOREAN).ready.value)
            assertFalse(VoiceProfile(context).ready.value)
        } finally { profile.delete(); profile.selectModel(previous); english.fill(0f); korean.fill(0f) }
    }
}
