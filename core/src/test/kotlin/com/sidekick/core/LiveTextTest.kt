package com.sidekick.core

import kotlin.test.*

class LiveTextTest {
    @Test fun `removes exact overlap but preserves revisions and repeated single words`() {
        assertEquals("I will call Emma tomorrow.", LiveText.append("I will call Emma", "call Emma tomorrow."))
        assertEquals("I will call Emma. Tomorrow.", LiveText.append("I will call Emma.", "will call Emma. Tomorrow."))
        assertEquals("very very good", LiveText.append("very", "very good"))
        assertEquals("Call Emma call Emily", LiveText.append("Call Emma", "call Emily"))
    }
}
