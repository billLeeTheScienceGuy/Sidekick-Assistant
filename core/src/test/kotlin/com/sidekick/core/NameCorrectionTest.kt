package com.sidekick.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NameCorrectionTest {
    private val known = listOf("애슐리", "브랜든", "클로이", "다니엘", "에밀리", "해나", "한나", "제이콥", "제시카", "저스틴", "케빈",
        "메건", "마이클", "올리비아", "라이언", "세라", "사라", "타일러")

    private fun lines(name: String) = javaClass.classLoader.getResource(name)!!.readText(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    @Test fun nearMissesBeforeAParticleAreCorrected() {
        assertEquals("내일 저녁에 애슐리랑 같이", NameCorrection.correct("내일 저녁에 에슐리랑 같이", known))
        assertEquals("아까 저스틴 과 회의", NameCorrection.correct("아까 저스팅 과 회의", known))
        assertEquals("라이언한테 전화", NameCorrection.correct("나이언한테 전화", known))
        assertEquals("메건 이랑 커피", NameCorrection.correct("매건 이랑 커피", known))
    }

    @Test fun namesAreReadFromSavedPlans() {
        val names = NameCorrection.namesIn(listOf("내일 저녁에 애슐리랑 같이 밥", "케빈 씨가 아침 아홉 시에 온대요", "친구랑 영화", "엄마한테 전화",
            "Dinner with Sarah", "브랜든네 집에 가요"))
        assertEquals(setOf("애슐리", "케빈", "브랜든"), names)
        assertEquals("에밀리 말고 애슐리랑 같이", NameCorrection.correct("에밀리 말고 에슐리랑 같이", names))
    }

    @Test fun ordinaryWordsAndUnknownNamesStay() {
        assertEquals("내일 저녁 녁에 사랑 같이 밥", NameCorrection.correct("내일 저녁 녁에 사랑 같이 밥", known))
        assertEquals("에슐리랑 같이", NameCorrection.correct("에슐리랑 같이", listOf("케빈")))
        assertEquals("에슐리랑 같이", NameCorrection.correct("에슐리랑 같이", emptyList()))
        assertEquals("제시카한테 3시에", NameCorrection.correct("제시카한테 3시에", known))
    }

    /** The 49-clip Korean names set (synthetic voices) for every recognizer that was run on it, plus other Korean text. */
    @Test fun namesSetImprovesAndOtherKoreanTextIsUntouched() {
        val variants = mapOf("Sarah" to listOf("세라", "사라"), "Hannah" to listOf("해나", "한나"))
        val roman = mapOf("Ashley" to "애슐리", "Brandon" to "브랜든", "Chloe" to "클로이", "Daniel" to "다니엘", "Emily" to "에밀리",
            "Jacob" to "제이콥", "Jessica" to "제시카", "Justin" to "저스틴", "Kevin" to "케빈", "Megan" to "메건", "Michael" to "마이클",
            "Olivia" to "올리비아", "Ryan" to "라이언", "Tyler" to "타일러")
        val byMode = mutableMapOf<String, IntArray>()
        for (row in lines("korean-names.tsv")) {
            val (mode, reference, recognized) = row.split('\t')
            val target = known.filter { reference.contains(it) } +
                roman.filterKeys { reference.contains(it) }.values + variants.filterKeys { reference.contains(it) }.values.flatten()
            if (target.isEmpty()) continue
            val after = NameCorrection.correct(recognized, known)
            val counts = byMode.getOrPut(mode) { IntArray(3) }
            counts[0]++
            if (target.any { recognized.contains(it) }) counts[1]++
            if (target.any { after.contains(it) }) counts[2]++
        }
        for ((mode, c) in byMode) println("names $mode: ${c[1]}/${c[0]} -> ${c[2]}/${c[0]}")
        assertTrue(byMode.values.all { it[2] >= it[1] })
        assertTrue(byMode.getValue("sense2024")[2] > byMode.getValue("sense2024")[1])
        val other = lines("korean-other.txt")
        val changed = other.filter { NameCorrection.correct(it, known) != it }
        java.io.File("build/name-correction-changes.txt").writeText(
            changed.joinToString("\n") { it + "\n -> " + NameCorrection.correct(it, known) }, Charsets.UTF_8)
        println("other Korean sentences changed: ${changed.size}/${other.size}")
        assertTrue(changed.isEmpty())
    }
}
