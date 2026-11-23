package com.sidekick.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RepeatFilterTest {
    @Test fun `decoder loops collapse to one copy`() {
        assertEquals("I'll call you.", RepeatFilter.collapse("I'll call you. I'll call you. I'll call you. I'll call you."))
        assertEquals("Meet at 5. Thank you.", RepeatFilter.collapse("Meet at 5. Thank you. Thank you. Thank you. Thank you."))
        assertEquals("Thank you,", RepeatFilter.collapse("Thank you, thank you, thank you, thank you"))
        assertEquals("the", RepeatFilter.collapse("the the the the the the the the"))
        assertEquals("내일 만나요", RepeatFilter.collapse("내일 만나요 내일 만나요 내일 만나요 내일 만나요"))
        assertEquals("Oh, today, very,", RepeatFilter.collapse("Oh, today, " + "very, ".repeat(27).trim()))
        assertEquals("We'll meet at the station tomorrow.", RepeatFilter.collapse("We'll meet at the station tomorrow. ".repeat(3)))
        assertEquals("谢谢你", RepeatFilter.collapse("谢谢你谢谢你谢谢你谢谢你"))
        assertEquals("我们明天见。", RepeatFilter.collapse("我们明天见。我们明天见。我们明天见。"))
        assertEquals("Ok so see you", RepeatFilter.collapse("Ok so see you see you see you see you"))
    }

    @Test fun `ordinary repetition stays`() {
        listOf("No, no, I said five.", "very very good", "네 네 네", "Bye bye.", "That that is the plan.",
            "ㅋㅋㅋㅋㅋㅋ", "哈哈哈哈哈哈", "谢谢谢谢", "Call me, call me later.", "one two three, one two three", "",
            "it's a hot day, very, very, very, very hot day", "Let's get it. Let's get it. Let's get it.",
            "Spacing  stays   as  it was.").forEach {
            assertEquals(it, RepeatFilter.collapse(it))
        }
        assertFalse(RepeatFilter.hasLoop("Let's meet at 5 tomorrow."))
        assertTrue(RepeatFilter.hasLoop("yes yes yes yes yes yes"))
    }

    /**
     * Optional check against real recognizer output and human transcripts: REPEAT_CORPUS points to a folder of
     * benchmark JSON files with "reference" and "recognized" fields. No reference may change.
     */
    @Test fun `real transcripts are left alone`() {
        val folder = System.getenv("REPEAT_CORPUS")?.let(::File)
        assumeTrue(folder != null && folder.isDirectory)
        val field = Regex("\"(reference|recognized)\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        var references = 0; var recognized = 0; val changedReferences = mutableListOf<String>(); val loops = mutableListOf<String>()
        folder!!.walkTopDown().filter { it.isFile && it.extension == "json" && it.length() < 50_000_000 }.forEach { file ->
            field.findAll(file.readText()).forEach { match ->
                val text = unescape(match.groupValues[2])
                if (match.groupValues[1] == "reference") { references++; if (RepeatFilter.hasLoop(text)) changedReferences += text }
                else { recognized++; if (RepeatFilter.hasLoop(text)) loops += text }
            }
        }
        println("REPEAT_CORPUS references=$references changed=${changedReferences.size} recognized=$recognized collapsed=${loops.size}")
        changedReferences.distinct().take(20).forEach { println("REF  $it -> ${RepeatFilter.collapse(it)}") }
        loops.distinct().take(40).forEach { println("LOOP ${it.take(160)} -> ${RepeatFilter.collapse(it).take(80)}") }
        assertTrue(changedReferences.distinct().toString(), changedReferences.isEmpty())
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i + 1 >= value.length) { append(c); i++; continue }
            when (val e = value[i + 1]) {
                'u' -> { append(value.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                'n' -> append('\n'); 't' -> append('\t'); 'r' -> append('\r'); else -> append(e)
            }
            i += 2
        }
    }
}
