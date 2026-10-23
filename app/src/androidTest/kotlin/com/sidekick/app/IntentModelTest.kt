package com.sidekick.app

import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.intent.IntentEngine
import com.sidekick.core.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class IntentModelTest {
    private fun source(text: String) = Transcript(text, Instant.now(), ZoneId.of("America/Chicago"), Speaker.USER, true)

    @Test fun recognizesPersonalFuturePlansWithoutInventingAttendance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val engine = IntentEngine(context)
        assumeTrue(engine.ready.value)
        for (text in listOf("I will be going to Louie's birthday tomorrow", "I'm attending Maya's wedding tomorrow.")) {
            val result = withTimeout(90_000) { engine.extract(source(text)) }
            assertTrue("Expected a future plan", result.isNotEmpty())
            assertTrue(result.any { it.evidence.trimEnd('.') == text.trimEnd('.') })
            Log.i("SidekickIntentBenchmark", "case=future-plan proposals=${result.size} modelProposals=${result.count { it.needsReview.startsWith("Local model") }}")
        }
        for (text in listOf("Louie's birthday is tomorrow.", "I will not be going to Louie's birthday tomorrow.",
            "Maybe I will be going to Louie's birthday tomorrow if I can.")) {
            assertTrue(withTimeout(90_000) { engine.extract(source(text)) }.isEmpty())
        }
    }

    @Test fun localModelFindsNewActionsRejectsQuotesAndRecoversFromCancellation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".benchmark"))
        val engine = IntentEngine(context)
        assumeTrue(engine.ready.value)
        val positive = source("I'll water the plants tomorrow.")
        val start = SystemClock.elapsedRealtime()
        val proposals = withTimeout(90_000) { engine.extract(positive) }
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        Log.i("SidekickIntentBenchmark", "case=positive elapsedMs=${SystemClock.elapsedRealtime() - start} pssKb=${memory.totalPss} proposals=${proposals.size}")
        assertTrue("Expected a grounded new action; got $proposals", proposals.any { it.title.lowercase().contains("plants") })
        assertTrue(proposals.all { positive.text.contains(it.evidence) })
        assertTrue(withTimeout(90_000) { engine.extract(source("Emma said: I'll water the plants tomorrow.")) }.isEmpty())
        assertTrue(withTimeout(90_000) { engine.extract(source("Maybe I'll water the plants if I have time.")) }.isEmpty())
        val job = launch { engine.extract(positive) }
        delay(100)
        withTimeout(20_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertTrue(withTimeout(90_000) { engine.extract(positive) }.isNotEmpty())
        // A bad replacement must not overwrite the verified installed model.
        val bad = File(context.cacheDir, "bad-model.gguf").apply { writeText("invalid model") }
        try {
            var rejected = false
            try { engine.importModel(Uri.fromFile(bad)) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(engine.ready.value)
        } finally { bad.delete() }
    }
}
