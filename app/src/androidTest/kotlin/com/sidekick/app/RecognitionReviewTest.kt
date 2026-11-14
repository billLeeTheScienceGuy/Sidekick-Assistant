package com.sidekick.app

import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.speech.*
import com.sidekick.core.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.*
import kotlin.math.*

/** Recorded public speech only; clean human code switches plus controlled acoustic stress. */
class RecognitionReviewTest {
    @Test fun measureNaturalSwitchesQuietNoiseDistanceAndOverlappingSpeech() = runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation(); val c=i.targetContext
        assumeTrue(c.packageName.endsWith(".benchmark")); assumeTrue(InstrumentationRegistry.getArguments().getString("runRobustness")=="true")
        val source=i.context.assets.open("recognition-review.json").bufferedReader().use { JSONObject(it.readText()) }
        val clips=source.getJSONArray("clips"); val results=JSONArray(); val speech=SpeechEngine(c,{SpeechLanguage.AUTO},{SpeechQuality.ADAPTIVE})
        fun read(row:JSONObject):FloatArray {
            val data=i.context.assets.open(row.getString("file")).use { it.readBytes() }
            check(MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}==row.getString("sha256"))
            return WavAudio.decode(data)
        }
        fun norm(t:String)=Normalizer.normalize(t.lowercase(Locale.ROOT),Normalizer.Form.NFKC).codePoints().toArray().filter { Character.isLetterOrDigit(it) }.toIntArray()
        fun edits(a:IntArray,b:IntArray):Int {
            var p=IntArray(b.size+1){it}
            for(k in a.indices) { val n=IntArray(b.size+1);n[0]=k+1;for(j in b.indices)n[j+1]=minOf(n[j]+1,p[j+1]+1,p[j]+if(a[k]==b[j])0 else 1);p=n }
            return p.last()
        }
        fun rms(a:FloatArray)=sqrt(a.sumOf { it.toDouble()*it }/a.size).coerceAtLeast(1e-9)
        val background=read(clips.getJSONObject(0))
        var complete=false
        try {
            for(index in 0 until clips.length()) {
                val row=clips.getJSONObject(index);val pcm=read(row)
                val conditions=if(row.getString("dataset")=="ASCEND")listOf("clean") else listOf("clean","quiet-minus16dB","white-noise-10dB","simulated-distance","overlap-minus10dB")
                try { for(condition in conditions) {
                    val power=c.getSystemService(android.os.PowerManager::class.java)
                    check(android.os.Build.VERSION.SDK_INT<29||power.currentThermalStatus<android.os.PowerManager.THERMAL_STATUS_SEVERE)
                    val samples=when(condition) {
                        "quiet-minus16dB" -> FloatArray(pcm.size){pcm[it]*.1585f}
                        "white-noise-10dB" -> { val random=Random(20261113L+index);val n=FloatArray(pcm.size){random.nextFloat()-.5f};val gain=rms(pcm)/rms(n)/sqrt(10.0);FloatArray(pcm.size){(pcm[it]+n[it]*gain).toFloat().coerceIn(-1f,1f)}.also{n.fill(0f)} }
                        "simulated-distance" -> { var filtered=0f;FloatArray(pcm.size){k-> filtered=.5f*filtered+.5f*pcm[k];(.3f*filtered+if(k>=1440).06f*pcm[k-1440] else 0f).coerceIn(-1f,1f)} }
                        "overlap-minus10dB" -> {val gain=rms(pcm)/rms(background)/sqrt(10.0);FloatArray(pcm.size){(pcm[it]+background[it%background.size]*gain).toFloat().coerceIn(-1f,1f)}}
                        else -> pcm.copyOf()
                    }
                    val start=android.os.SystemClock.elapsedRealtime()
                    val text=speech.transcribe(samples,retainModel=true)
                    val uncertain=runCatching { speech.javaClass.getMethod("getLastUncertain").invoke(speech) as Boolean }.getOrDefault(false)
                    assertTrue(samples.all{it==0f})
                    val ref=norm(row.getString("reference"))
                    results.put(JSONObject().put("file",row.getString("file")).put("code",row.getString("code")).put("condition",condition)
                        .put("reference",row.getString("reference")).put("recognized",text).put("backend",speech.lastBackend)
                        .put("referenceCharacters",ref.size).put("characterEdits",edits(ref,norm(text))).put("automaticChangesBlocked",uncertain)
                        .put("audioMs",pcm.size*1000L/16000).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-start))
                } } finally { pcm.fill(0f) }
            }
            complete=true
        } finally {
            background.fill(0f);speech.release()
            File(c.filesDir,"recognition-review.json").writeText(JSONObject().put("completed",complete).put("dataset",source)
                .put("conditions","Public recorded speech; synthetic acoustic transforms are not real-room measurements; no microphone").put("results",results).toString(2))
        }
        if(InstrumentationRegistry.getArguments().getString("runSenseCandidate")=="true") {
            val model=ModelPacks.ensure(c,SenseVoiceEngine.MODEL,SenseVoiceEngine.MODEL_HASH)
            val tokens=ModelPacks.ensure(c,SenseVoiceEngine.TOKENS,SenseVoiceEngine.TOKENS_HASH)
            val decoder=com.k2fsa.sherpa.onnx.OfflineRecognizer(config=com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(modelConfig=com.k2fsa.sherpa.onnx.OfflineModelConfig(
                senseVoice=com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig(model=model.absolutePath,language="auto",useInverseTextNormalization=true),tokens=tokens.absolutePath,numThreads=2)))
            val candidate=JSONArray()
            try {
                for(index in 0 until clips.length()) {
                    val row=clips.getJSONObject(index);if(row.getString("dataset")!="ASCEND")continue
                    val pcm=read(row);val stream=decoder.createStream();val start=android.os.SystemClock.elapsedRealtime()
                    val text=try {stream.acceptWaveform(pcm,16000);decoder.decode(stream);decoder.getResult(stream).text.replace(Regex("<\\|[^|>]+\\|>"),"").trim()}finally{stream.release();pcm.fill(0f)}
                    val ref=norm(row.getString("reference"));candidate.put(JSONObject().put("file",row.getString("file")).put("reference",row.getString("reference"))
                        .put("recognized",text).put("characterEdits",edits(ref,norm(text))).put("referenceCharacters",ref.size).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-start))
                }
            } finally {decoder.release();File(c.filesDir,"recognition-sense-candidate.json").writeText(JSONObject().put("results",candidate).toString(2))}
        }
    }
}
