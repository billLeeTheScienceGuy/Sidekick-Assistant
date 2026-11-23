package com.sidekick.core

import java.io.File
import kotlin.test.Test

/**
 * Runs the real scam rules over the public corpora prepared by scripts/laptop-eval/scam_prepare.py
 * (.deps/scam-data/_prepared/corpus.tsv; third-party data, never committed). Skips when the file is absent.
 * Writes results-<SCAM_TAG>.tsv next to it; scripts/laptop-eval/scam_eval.py turns that into the report.
 * Conversations go through ScamMonitor one utterance at a time (5 s apart); messages through ScamCheck.message.
 * Conversations with both speakers are also run on the phone owner's side only (what the app hears on a call).
 */
class ScamCorpusEvalTest {
    @Test fun corpus() {
        val dir = File("../.deps/scam-data/_prepared")
        val corpus = File(dir, "corpus.tsv")
        if (System.getenv("SCAM_CORPUS") == null || !corpus.exists()) { println("scam corpus: not requested (set SCAM_CORPUS=1) or not prepared, skipped"); return }
        val tag = System.getenv("SCAM_TAG") ?: "latest"
        val out = File(dir, "results-$tag.tsv").bufferedWriter(Charsets.UTF_8)
        out.write("id\tmode\tmax\tsteps\twarned\tstops\tsignals\n")
        fun level(l: ScamLevel) = l.ordinal
        corpus.useLines(Charsets.UTF_8) { lines ->
            for (line in lines) {
                val f = line.split('\t'); if (f.size < 7) continue
                val kind = f[2]
                val utts = f[6].split('␞').map { u -> u.split('␟', limit = 2).let { it[0] to it.getOrElse(1) { "" } } }
                val modes = mutableListOf("all" to utts)
                if (kind == "conv" && utts.any { it.first == "B" } && utts.any { it.first == "A" }) {
                    modes += "owner" to utts.filter { it.first == "B" }
                    // The same owner side with the call flag on, and the whole dialogue with it (weak signs count sooner during a call).
                    modes += "ownercall" to utts.filter { it.first == "B" }
                    modes += "allcall" to utts
                }
                for ((mode, us) in modes) {
                    var max = ScamLevel.NONE; var warned = 0; var stops = 0; var sig = emptyList<ScamSignal>()
                    if (kind == "msg") {
                        val a = ScamCheck.message(us.joinToString(" ") { it.second })
                        max = a.level; sig = a.signals; warned = if (a.level != ScamLevel.NONE) 1 else 0; stops = if (a.level == ScamLevel.STOP) 1 else 0
                        out.write("${f[5]}\t$mode\t${level(max)}\t1\t$warned\t$stops\t${sig.joinToString(",")}\n")
                        // The same text as one utterance of a phone call (weak signs count sooner).
                        val c = ScamMonitor().add(us.joinToString(" ") { it.second }, 0L, inCall = true)
                        out.write("${f[5]}\tmsgcall\t${level(c.level)}\t1\t${if (c.level != ScamLevel.NONE) 1 else 0}\t${if (c.level == ScamLevel.STOP) 1 else 0}\t${c.signals.joinToString(",")}\n"); continue
                    }
                    val m = ScamMonitor()
                    us.forEachIndexed { i, (_, text) ->
                        val a = m.add(text, i * 5_000L, inCall = mode.endsWith("call"))
                        if (a.level != ScamLevel.NONE) warned++
                        if (a.level == ScamLevel.STOP) stops++
                        if (a.level > max) { max = a.level; sig = a.signals }
                    }
                    out.write("${f[5]}\t$mode\t${level(max)}\t${us.size}\t$warned\t$stops\t${sig.joinToString(",")}\n")
                }
            }
        }
        out.close()
        println("scam corpus: results written to results-$tag.tsv")
    }
}
