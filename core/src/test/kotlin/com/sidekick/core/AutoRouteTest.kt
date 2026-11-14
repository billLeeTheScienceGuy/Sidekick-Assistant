package com.sidekick.core

import kotlin.test.*

/** Readings below are real on-device outputs from the natural-speech and personal-voice evaluations. */
class AutoRouteTest {
    @Test fun `SenseVoice alone for tagged Korean Chinese and fluent English`() {
        assertTrue(AutoRoute.senseVoiceSuffices("네, 좋아요.", "ko", 2000))
        assertTrue(AutoRoute.senseVoiceSuffices("I'm meeting Sarah for lunch at 1230 on Monday 월요일 점심 약속이에요.", "ko", 7500))
        assertTrue(AutoRoute.senseVoiceSuffices("那今天跟大家分享一个什么课程呢", "zh", 5500))
        assertTrue(AutoRoute.senseVoiceSuffices("I'll go to the gym tomorrow at 7 AM.", "en", 2600))
        assertTrue(AutoRoute.senseVoiceSuffices("but represent a step in the process towards designating marine conservation zones in Welsh waters.", "en", 8940))
    }
    @Test fun `French German Russian Arabic labeled en are checked with Omnilingual`() {
        for ((text, ms) in listOf("The." to 7000L, "." to 6000L, "and." to 5200L, "Con Donim Ccusva of during during C2." to 9000L,
            "Hi, this. I'm experimental." to 5880L, "Through the F premier, which now myself Sos Medical Cooper Cr Review." to 9800L))
            assertFalse(AutoRoute.senseVoiceSuffices(text, "en", ms), text)
        assertFalse(AutoRoute.senseVoiceSuffices("", "nospeech", 4000))
        // Arabic/Russian tagged "zh" with a syllable or two for several seconds of speech (natural-speech evaluation).
        for ((text, ms) in listOf("哦." to 6500L, "啊啦。" to 7000L, "放。" to 5000L, "还是我想爱是呀。" to 8200L))
            assertFalse(AutoRoute.senseVoiceSuffices(text, "zh", ms), text)
        assertFalse(AutoRoute.senseVoiceSuffices("something", "ja", 4000))
    }
    @Test fun `second opinion picks the language SenseVoice cannot read`() {
        assertFalse(AutoRoute.preferSenseVoice("The.", "en", "السلام عليكم ورحمة الله وبركات أهلا ومرحبا بكم"))
        assertFalse(AutoRoute.preferSenseVoice("and.", "en", "друзья всем привет мы сейчас находимся в сочи"))
        assertFalse(AutoRoute.preferSenseVoice("My name is Dr. Davidford and yet not tip easy sta in episode.", "en",
            "guten tag mein name ist doktor david und jetzt kommt noch ein tipp wie sie die stärken"))
        assertFalse(AutoRoute.preferSenseVoice("Con Donim.", "en", "comment bien chanter du rap on va en parler tout de suite alors je ne suis pas rappeur"))
        // Korean that SenseVoice did not tag, while Omnilingual wrote Latin nonsense (live Pixel recording).
        assertTrue(AutoRoute.preferSenseVoice("내일 오후 6시에 jam에서 회의가 있어요.", "en", "de e oh joså sie the james e so haga sejer"))
        // English that read slowly: SenseVoice kept most of it.
        assertTrue(AutoRoute.preferSenseVoice("On Tuesday at 3 pm I have a team meeting.", "en", "on tuesday at pm i have a team meeting downtown"))
    }
    @Test fun `only plain English goes to the English model`() {
        assertTrue(AutoRoute.englishOnly("On Tuesday at 3 pm, I have a team meeting downtown.", "en"))
        // Mixed Korean/English (the owner's line 2) must stay with SenseVoice: the English model would drop the Korean.
        assertFalse(AutoRoute.englishOnly("I'm meeting Sarah for lunch at 12:30 on Monday, 월요일 점심 약속이에요.", "en"))
        assertFalse(AutoRoute.englishOnly("내일 오후 6시에 회의가 있어요.", "ko"))
        assertFalse(AutoRoute.englishOnly("Bonjour tout le monde.", "fr"))
        assertFalse(AutoRoute.englishOnly("...", "en"))
    }
    @Test fun `English model times are shown as digits`() {
        assertEquals("I'll go to the gym tomorrow at 7am.", AutoRoute.englishDigits("I'll go to the gym tomorrow at seven AM."))
        assertEquals("On Tuesday, at 3pm, I have a team meeting downtown.", AutoRoute.englishDigits("On Tuesday, at three PM, I have a team meeting downtown."))
        assertEquals("Lunch at 12:30 on Monday.", AutoRoute.englishDigits("Lunch at twelve thirty on Monday."))
        assertEquals("Three people are coming.", AutoRoute.englishDigits("Three people are coming."))
    }
    @Test fun `only plain Russian goes to the Russian model`() {
        assertTrue(AutoRoute.russianOnly("друзья всем привет мы сейчас находимся в сочи"))
        assertFalse(AutoRoute.russianOnly("давай встретимся на meeting в пятницу"))
        assertFalse(AutoRoute.russianOnly("guten tag mein name ist doktor david"))
        assertFalse(AutoRoute.russianOnly(""))
    }
    @Test fun `two English readings agree only on English speech`() {
        // The owner's lines on the Pixel: digits vs spoken numbers still agree.
        assertTrue(AutoRoute.agreeOnEnglish("I'll go to the gym tomorrow at 7 AM.", "I'll go to the gym tomorrow at seven AM."))
        assertTrue(AutoRoute.agreeOnEnglish("On Tuesday at 3 pm, I have a team meeting downtown.", "On Tuesday, at three PM, I have a team meeting downtown."))
        assertTrue(AutoRoute.agreeOnEnglish("at 7", "at seven"))
        // German speech SenseVoice labeled "en": two unrelated English guesses.
        assertFalse(AutoRoute.agreeOnEnglish("My name is Dr. Davidford and yet not tip easy sta in episode.", "Mine name is doctor David and yet comes no tip we see the starkin."))
        assertFalse(AutoRoute.agreeOnEnglish("I'll go.", "I'll go to the gym tomorrow at seven AM."))
        assertFalse(AutoRoute.agreeOnEnglish("", "hello"))
    }
}
