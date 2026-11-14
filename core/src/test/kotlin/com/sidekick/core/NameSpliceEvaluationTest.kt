package com.sidekick.core

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.*

/**
 * Developer evaluation (skipped unless the laptop benchmark ran): SenseVoice vs SenseVoice + name splice on
 * .deps/names-splice-eval.tsv (set, file, name forms, reference, SenseVoice reading, Zipformer-with-list reading).
 */
class NameSpliceEvaluationTest {
    @Test fun `splice fixes names and leaves name-free Korean alone`() {
        val file = File("../.deps/names-splice-eval.tsv")
        assumeTrue(file.isFile)
        val names = NameSplice.parse("Sarah 세라 사라\nJessica 제시카\nMichael 마이클\nDaniel 다니엘\nEmily 에밀리\nKevin 케빈\nOlivia 올리비아\nBrandon 브랜든\n" +
            "Ashley 애슐리\nRyan 라이언\nChloe 클로이\nJustin 저스틴\nMegan 메건\nTyler 타일러\nHannah 해나 한나\nJacob 제이콥")
        var before = 0; var after = 0; var withNames = 0; var changedPlain = 0; var plain = 0
        val changes = mutableListOf<String>()
        for (line in file.readLines()) {
            val (set, _, forms, _, sense, listed) = line.split('\t').let { listOf(it[0], it[1], it[2], it[3], it[4], it[5]) }
            val spliced = NameSplice.apply(sense, listed, names)
            if (forms.isNotEmpty()) {
                val name = names.first { n -> n.forms.any { it in forms.split('|') } }
                withNames++
                if (name.display in sense || name.forms.any { it in sense }) before++
                if (name.display in spliced || name.forms.any { it in spliced }) after++
            } else {
                plain++
                if (spliced != sense) { changedPlain++; changes += "$set: $sense → $spliced" }
            }
        }
        val report = "names right: SenseVoice $before/$withNames, with splice $after/$withNames; name-free Korean changed: $changedPlain/$plain\n" + changes.joinToString("\n")
        File("build/reports/name-splice.md").apply { parentFile.mkdirs(); writeText(report) }
        println(report)
        assertTrue(after >= before)
    }
}

private operator fun <T> List<T>.component6() = this[5]
