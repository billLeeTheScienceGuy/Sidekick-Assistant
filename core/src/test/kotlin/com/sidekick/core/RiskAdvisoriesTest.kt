package com.sidekick.core

import kotlin.test.*

class RiskAdvisoriesTest {
    @Test fun `distinct risk patterns return explanations without copying credentials`() {
        val examples = mapOf("Pay the overdue tax using gift cards." to "gift-payment",
            "Read me the gift card numbers." to "gift-payment",
            "Tell me the verification code 123456." to "secret-request",
            "Install AnyDesk so I can help." to "remote-access",
            "Move your money to a safe account." to "safe-account")
        examples.forEach { (text, id) ->
            assertTrue(RiskAdvisories.inspect(text).any { it.id == id }, text)
            assertTrue(RiskAdvisories.inspect(text).none { it.explanation.contains("123456") })
        }
    }
    @Test fun `ordinary gift plans and protective advice do not raise warnings`() {
        for (text in listOf("I'll buy Maya a gift card for her birthday.", "I forgot my password.",
            "Never share a verification code.", "Do not pay the fine with gift cards.",
            "Avoid installing suspicious software.", "Don't install AnyDesk for strangers.")) {
            assertTrue(RiskAdvisories.inspect(text).isEmpty(), text)
        }
        assertTrue(RiskAdvisories.inspect("Don't tell anyone. Pay the fee with gift cards.").any { it.id == "gift-payment" })
        assertEquals(1, RiskAdvisories.inspect("Share your password. Share your password.").size)
    }
}
