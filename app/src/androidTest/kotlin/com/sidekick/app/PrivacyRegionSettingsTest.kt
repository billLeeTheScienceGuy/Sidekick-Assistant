package com.sidekick.app

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.core.PrivacyRegion
import org.junit.Assert.*
import org.junit.Test

class PrivacyRegionSettingsTest {
    @Test fun phoneRegionIsOptInCoarseExpiringAndClearedWhenDisabled() {
        val root = InstrumentationRegistry.getInstrumentation().targetContext
        check(root.packageName.endsWith(".benchmark") || root.packageName.endsWith(".compacttest"))
        val name = "region-test-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(root) {
            override fun getSharedPreferences(key: String, mode: Int) = root.getSharedPreferences(name, mode)
        }
        try {
            val settings = GuidanceSettings(context)
            assertFalse(settings.privacyPhoneRegion.value); assertNull(settings.privacyRegion.value)
            settings.setPrivacyRegion(PrivacyRegion("US", "WI")); assertNull(settings.privacyRegion.value)
            settings.setPrivacyPhoneRegion(true); settings.setPrivacyRegion(PrivacyRegion("US", "WI"))
            val restored = GuidanceSettings(context)
            assertEquals(PrivacyRegion("US", "WI"), restored.privacyRegion.value!!.region)
            assertTrue(restored.privacyRegion.value!!.fresh(System.currentTimeMillis()))
            assertEquals(setOf("privacy_phone_region", "privacy_country", "privacy_area", "privacy_region_at", "speech_quality"), root.getSharedPreferences(name, 0).all.keys)
            settings.setPrivacyPhoneRegion(false)
            assertNull(settings.privacyRegion.value); assertNull(GuidanceSettings(context).privacyRegion.value)
            assertFalse(root.getSharedPreferences(name, 0).contains("privacy_country"))
        } finally { root.deleteSharedPreferences(name) }
    }

    @Test fun calendarLinksCanOnlyOpenAnOwnSavedIdAndCannotExecuteActions() {
        val pkg = "com.sidekick.app.compacttest"
        assertEquals("abc-123", CalendarTarget.parse(Uri.parse(CalendarTarget.uri(pkg, "abc-123")), pkg))
        for (bad in listOf("sidekick://other.app/event/abc", "sidekick://$pkg/event/abc/cancel",
            "sidekick://$pkg/event/abc?cancel=true", "sidekick://$pkg/event/abc#delete", "https://$pkg/event/abc",
            "sidekick://$pkg/event/a%2Fb", "sidekick://$pkg:80/event/abc", "sidekick://$pkg/event/"))
            assertNull(bad, CalendarTarget.parse(Uri.parse(bad), pkg))
    }
}
