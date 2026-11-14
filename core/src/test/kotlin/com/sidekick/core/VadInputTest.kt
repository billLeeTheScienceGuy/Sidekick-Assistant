package com.sidekick.core

import kotlin.test.*

class VadInputTest {
    @Test fun quietDetectionGainIsBoundedWhileSilenceAndLoudSpeechStayUnchanged() {
        val quiet=floatArrayOf(.002f,-.002f);VadInput.prepareInPlace(quiet)
        assertEquals(.008f,quiet[0],.000001f)
        val silence=FloatArray(512);VadInput.prepareInPlace(silence);assertTrue(silence.all{it==0f})
        val loud=floatArrayOf(.2f,-.2f);val before=loud.copyOf();VadInput.prepareInPlace(loud);assertContentEquals(before,loud)
    }
    @Test fun clicksCannotCauseClippingAndInvalidFramesAreRejected() {
        val click=FloatArray(16000){.001f};click[500]=.9f;VadInput.prepareInPlace(click)
        assertTrue(click.all{it in -0.950001f..0.950001f})
        assertFailsWith<IllegalArgumentException>{VadInput.prepareInPlace(floatArrayOf(Float.NaN))}
    }
}
