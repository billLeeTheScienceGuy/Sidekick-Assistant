package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.LiveSpeechProcessor
import com.sidekick.app.speech.*
import com.sidekick.core.SpeakerAudio
import com.sidekick.core.WavAudio
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercise the real capture/VAD path without decoding, microphone capture or saved audio. */
class CaptureBoundaryReviewTest {
    @Test fun recordedOnsetsQuietSpeechAndSilenceUseBoundedWipedBuffers() = runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness")=="true")
        check(c.packageName.endsWith(".compacttest")||c.packageName.endsWith(".benchmark"))
        val clips=i.context.assets.open("recognition-review.json").bufferedReader().use{JSONObject(it.readText()).getJSONArray("clips")}
        val report=JSONArray();NativeVad.open(VadModel.install(c))
        try {
            for(index in 0..clips.length()+3) {
                val row=if(index<clips.length())clips.getJSONObject(index)else null
                val original=if(row==null) {
                    val random=java.util.Random(3300L+index)
                    when(index-clips.length()) {
                        1->FloatArray(160_000){(random.nextFloat()-.5f)*.002f}
                        2->FloatArray(160_000){(random.nextFloat()-.5f)*.02f}
                        3->FloatArray(160_000).also{for(k in 0 until it.size step 8000)it[k]=.8f}
                        else->FloatArray(160_000)
                    }
                } else i.context.assets.open(row.getString("file")).use{WavAudio.decode(it.readBytes())}
                try {for(quiet in listOf(false,true)) {
                    val samples=FloatArray(16_000)+FloatArray(original.size){original[it]*if(quiet).1585f else 1f}+FloatArray(32_000)
                    val ranges=mutableListOf<Pair<Int,Int>>();val owned=mutableListOf<FloatArray>()
                    val pipeline=LiveSpeechProcessor(SpeechEngine(c),inspect={chunk->
                        owned+=chunk
                        val first=chunk.indexOfFirst{it!=0f}
                        if(first>=0) {
                            val length=minOf(24,chunk.size-first)
                            val location=(0..samples.size-length).firstOrNull { k->samples[k]==chunk[first]&&(0 until length).all{samples[k+it]==chunk[first+it]} }
                            assertNotNull("Detection gain must not change recognition PCM",location)
                            if(location!=null)ranges+=Pair(location-first,location-first+chunk.size)
                        }
                        false
                    }) { _,_,_->error("Inspection-only replay must never transcribe")}
                    try {
                        coroutineScope {
                            val worker=launch(Dispatchers.Default){pipeline.consume()}
                            for(at in samples.indices step 4096) {
                                val frame=samples.copyOfRange(at,minOf(at+4096,samples.size))
                                try{pipeline.accept(frame)}finally{frame.fill(0f)}
                                yield()
                            }
                            pipeline.finish();worker.join()
                        }
                        assertTrue(owned.all{a->a.all{it==0f}})
                        val span=SpeakerAudio.speechSpan(original)
                        val missed=if(span==null)0 else if(ranges.isEmpty())span.last-span.first+1 else maxOf(0,ranges.minOf{it.first}-(16_000+span.first))
                        report.put(JSONObject().put("file",row?.getString("file")?:"noise-control-${index-clips.length()}").put("quiet",quiet).put("segments",owned.size)
                            .put("missedOnsetMs",missed*1000L/16000).put("maximumChunkSamples",owned.maxOfOrNull{it.size}?:0))
                        if(row==null)assertEquals("Silence, noise and clicks must not produce transcript input",0,owned.size)
                        assertTrue(owned.all{it.size<=160_000})
                    } finally{pipeline.close();samples.fill(0f)}
                }}finally{original.fill(0f)}
            }
        }finally {
            NativeVad.close()
            File(c.filesDir,"capture-boundaries.json").writeText(JSONObject().put("conditions","Real capture/VAD; clean-energy onset is a proxy, not manually labeled phoneme truth; no ASR or microphone").put("results",report).toString(2))
        }
    }
}
