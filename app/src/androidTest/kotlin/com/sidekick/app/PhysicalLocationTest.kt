package com.sidekick.app

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly invoked physical-device check. Does not print coordinates or save a fix. */
@SdkSuppress(minSdkVersion = 28)
class PhysicalLocationTest {
    @Test fun foregroundAppReceivesAFreshRealPhoneLocation() = runBlocking {
        assumeTrue(Build.HARDWARE !in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Real location checks require explicit opt-in", InstrumentationRegistry.getArguments().getString("verifyPhysicalLocation") == "true")
        val app = instrumentation.targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        val manager = app.getSystemService(LocationManager::class.java)
        println("PHYSICAL_LOCATION enabled=${manager.isLocationEnabled}; hardware=${Build.MODEL}")
        assertTrue("Enable Android Location before checking a real fix", manager.isLocationEnabled)
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            var fix: Location? = null
            withTimeout(35_000) {
                repeat(3) {
                    if (fix == null) { fix = PlaceLookup(app).currentLocation(); if (fix == null) delay(1000) }
                }
            }
            assertNotNull("No fresh physical phone fix after foreground location requests", fix)
            val location = fix!!
            val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000L
            @Suppress("DEPRECATION")
            val mock = location.isFromMockProvider
            println("PHYSICAL_LOCATION provider=${location.provider}; ageSeconds=$age; accuracyMeters=${location.accuracy}; mock=$mock")
            assertFalse("Physical verification must not use a mock provider", mock)
            assertTrue("Location must meet the app's freshness limit", age in 0..600)
            val result = PlaceLookup(app).suggest("The James", "", true)
            println("PHYSICAL_PLACE_LOOKUP candidates=${result.addresses.size}; ${result.note}")
            assertFalse("Lookup must accept the real coordinate fix without a city fallback", result.note.contains("location is unavailable"))
        } finally { activity.close() }
    }
}
