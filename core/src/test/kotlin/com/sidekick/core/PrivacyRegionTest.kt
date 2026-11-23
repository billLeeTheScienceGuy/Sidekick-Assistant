package com.sidekick.core

import org.junit.Assert.*
import org.junit.Test

class PrivacyRegionTest {
    @Test fun ambiguousLocationsDoNotBecomeLegalDeterminations() {
        assertNull(PrivacyRegions.fromCity("Madison"))
        assertNull(PrivacyRegions.fromCity("Tel Aviv, IL"))
        assertNull(PrivacyRegions.fromCity("Chicago, IL"))
        assertEquals(PrivacyRegion("US", "WI"), PrivacyRegions.fromCity("Madison, WI"))
        assertEquals(PrivacyRegion("US", "IL"), PrivacyRegions.fromCity("Chicago, IL, USA"))
        assertEquals(PrivacyRegion("US", "IL"), PrivacyRegions.fromCity("Chicago, Illinois"))
        assertEquals(PrivacyRegion("US"), PrivacyRegions.fromCity("Seattle, WA, US"))
    }
    @Test fun staleAndFutureLocationsAreNotFresh() {
        val hint = CachedPrivacyRegion(PrivacyRegion("US", "WI"), 100L)
        assertTrue(hint.fresh(100L)); assertTrue(hint.fresh(86_400_099L))
        assertFalse(hint.fresh(99L)); assertFalse(hint.fresh(86_400_100L))
    }
    @Test fun everyRegionSaysWhatSideKickDoesWhileListeningAndUnknownIsExplicit() {
        assertTrue(PrivacyRegions.guidance(null).note.contains("strictest rules"))
        assertTrue(PrivacyRegions.policy.contains("never changes those rules"))  // the panel's city never decides the listening rules
        for (region in listOf(null, PrivacyRegion("US", "WI"), PrivacyRegion("US", "IL"), PrivacyRegion("US", "CA"), PrivacyRegion("KR"))) {
            val result = PrivacyRegions.guidance(region)
            assertTrue(result.note.contains("While listening")); assertFalse(result.note.contains("permitted to record"))
        }
        assertNull(PrivacyRegions.guidance(PrivacyRegion("KR")).source)
        assertTrue(PrivacyRegions.guidance(PrivacyRegion("US", "CA")).label.contains("not covered"))
    }
}
