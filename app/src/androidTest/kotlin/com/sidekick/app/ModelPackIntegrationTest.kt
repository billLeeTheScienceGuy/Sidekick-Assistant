package com.sidekick.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.*
import com.sidekick.core.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ModelPackIntegrationTest {
    @Test fun compactInstallImportsVerifiedWeightsAndActuallyRunsTheImportedModel() = runBlocking<Unit> {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        assumeTrue(context.packageName.endsWith(".compacttest"))
        val input = File(context.cacheDir, "whisper.skmodels")
        assertTrue("Host must copy the generated whisper.skmodels into this test app's cache", input.isFile)
        val root = File(context.noBackupFilesDir, "model-packs/whisper")
        assertFalse("Test requires an isolated, clean compact model store", root.exists())
        assertTrue(ModelPacks.available(context, "ggml-tiny.en.bin"))
        assertFalse(ModelPacks.available(context, "ggml-base.bin"))
        assertEquals(setOf("whisper"), ModelPacks.missing(context, SpeechLanguage.ENGLISH, SpeechQuality.ACCURATE))
        val speech = SpeechEngine(context)
        try {
            assertEquals("whisper", ModelPacks.importPack(context, Uri.fromFile(input)))
            assertTrue(ModelPacks.missing(context, SpeechLanguage.FRENCH, SpeechQuality.FAST).isEmpty())
            val imported = ModelPacks.ensure(context, "ggml-base.bin", SpeechEngine.BASE_HASH)
            assertTrue(imported.canonicalPath.startsWith(root.canonicalPath + File.separator))
            val pcm = i.context.assets.open("quality-party.wav").use { WavAudio.decode(it.readBytes()) }
            val text = speech.transcribe(pcm, selectedLanguage = SpeechLanguage.ENGLISH, selectedQuality = SpeechQuality.ACCURATE)
            assertTrue("Imported Whisper base did not transcribe the synthetic fixture", text.isNotBlank())
            val bad = File(context.cacheDir, "bad-pack.skmodels").apply { writeText("not a pack") }
            try { ModelPacks.importPack(context, Uri.fromFile(bad)); fail("Corrupt archive accepted") } catch (_: Exception) { }
            assertTrue(ModelPacks.available(context, "ggml-base.bin")); bad.delete()
        } finally { speech.release(); root.deleteRecursively(); input.delete(); ModelPacks.revision.value++ }
    }
}
