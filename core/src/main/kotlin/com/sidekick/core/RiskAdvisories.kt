package com.sidekick.core

/** One scam warning shown in the app and as a notification. Never contains the words that were heard. */
data class RiskAdvisory(val id: String, val title: String, val explanation: String)
