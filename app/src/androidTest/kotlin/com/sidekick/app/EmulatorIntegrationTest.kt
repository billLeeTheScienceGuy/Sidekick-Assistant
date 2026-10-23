package com.sidekick.app

import android.Manifest
import android.app.*
import android.content.ComponentName
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/** Real Android service delivery using synthetic data, restricted to the emulator. */
@SdkSuppress(minSdkVersion = 33)
class EmulatorIntegrationTest {
    private fun application(): SidekickApplication {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SidekickApplication
        assumeTrue(app.packageName.endsWith(".benchmark"))
        return app
    }

    @Test fun androidDeliversNotificationUpdatesAndDisabledScanningStopsThem() = runBlocking {
        val app = application()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = app.getSystemService(NotificationManager::class.java)
        val listener = ComponentName(app, MessageNotificationListener::class.java).flattenToString()
        val oldEnabled = app.guidance.messageNotifications.value
        val oldSelected = app.packageName in app.guidance.messageApps.value
        val oldInclude = app.guidance.includeInformational.value
        val before = app.temporary.drafts.value.map { it.id }.toSet()
        val hadAccess = manager.isNotificationListenerAccessGranted(ComponentName(app, MessageNotificationListener::class.java))
        fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
        val sender = Person.Builder().setName("Synthetic sender").build()
        val first = "There's a party ${LocalDate.now().plusDays(1)} at 8pm at The James."
        val style = Notification.MessagingStyle(Person.Builder().setName("Synthetic receiver").build())
            .setConversationTitle("SideKick emulator fixture")
        fun post(text: String) {
            style.addMessage(text, System.currentTimeMillis(), sender)
            manager.notify(8877, Notification.Builder(app, "emulator-messages").setSmallIcon(android.R.drawable.ic_dialog_email)
                .setCategory(Notification.CATEGORY_MESSAGE).setStyle(style).setOnlyAlertOnce(true).build())
        }
        try {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
            manager.createNotificationChannel(NotificationChannel("emulator-messages", "Emulator fixtures", NotificationManager.IMPORTANCE_DEFAULT))
            app.guidance.setMessageNotifications(false); app.guidance.setMessageNotifications(true)
            app.guidance.setMessageApp(app.packageName, true); app.guidance.setIncludeInformational(true)
            shell("cmd notification allow_listener $listener")
            withTimeout(10_000) { while (!manager.isNotificationListenerAccessGranted(ComponentName(app, MessageNotificationListener::class.java))) delay(100) }
            // Access grant and service binding are separate asynchronous Android operations.
            post(first)
            withTimeout(15_000) {
                while (app.temporary.drafts.value.none { it.id !in before && it.sourceKey != null }) { delay(500); post(first) }
            }
            val initial = app.temporary.drafts.value.single { it.id !in before }
            assertTrue(initial.informational); assertEquals("The James", initial.placeName)
            post("It's at 432 W Gorham Street. It ends around midnight.")
            withTimeout(10_000) { while (app.temporary.drafts.value.find { it.id == initial.id }?.placeAddress == null) delay(100) }
            val updated = app.temporary.drafts.value.single { it.id == initial.id }
            assertEquals("432 W Gorham Street", updated.placeAddress); assertNotNull(updated.activityEndAt)
            app.guidance.setMessageNotifications(false)
            post("There's a concert tomorrow at 7pm.")
            delay(1000)
            assertEquals(setOf(initial.id), app.temporary.drafts.value.filter { it.id !in before }.map { it.id }.toSet())
        } finally {
            manager.cancel(8877)
            if (!hadAccess) shell("cmd notification disallow_listener $listener")
            app.guidance.setMessageNotifications(oldEnabled); app.guidance.setMessageApp(app.packageName, oldSelected)
            app.guidance.setIncludeInformational(oldInclude)
            app.temporary.drafts.value.filter { it.id !in before }.forEach { app.temporary.remove(it.id) }
        }
    }

    @Test fun realGeocoderReturnsCandidatesOrPreservesAnUnknownAddress() = runBlocking {
        val app = application()
        val activity = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
        val city = withTimeout(20_000) { PlaceLookup(app).suggest("The James", "Madison, Wisconsin", false) }
        println("EMULATOR_CITY_LOOKUP candidates=${city.addresses.size}; ${city.note}")
        assertTrue(city.addresses.isNotEmpty() || city.note.isNotBlank())
        val known = withTimeout(20_000) { PlaceLookup(app).suggest("1600 Amphitheatre Parkway", "Mountain View, California", false) }
        println("EMULATOR_KNOWN_ADDRESS candidates=${known.addresses.size}; ${known.note}")
        assertTrue(known.addresses.isNotEmpty() || known.note.isNotBlank())
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        val phone = withTimeout(20_000) { PlaceLookup(app).suggest("The James", "", true) }
        println("EMULATOR_PHONE_LOOKUP candidates=${phone.addresses.size}; ${phone.note}")
        assertTrue(phone.addresses.isNotEmpty() || phone.note.isNotBlank())
        } finally { activity.close() }
    }

    @Suppress("DEPRECATION")
    @Test fun phoneLocationModeUsesNetworkDataWhenFusedHasNoFix() = runBlocking {
        val app = application()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = app.getSystemService(android.location.LocationManager::class.java)
        fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        shell("appops set ${app.packageName} android:mock_location allow")
        val provider = android.location.LocationManager.NETWORK_PROVIDER
        val fused = android.location.LocationManager.FUSED_PROVIDER
        val activity = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            manager.addTestProvider(fused, false, false, false, false, true, true, true,
                android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_COARSE)
            manager.setTestProviderEnabled(fused, true)
            manager.addTestProvider(provider, false, false, false, false, true, true, true,
                android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_COARSE)
            manager.setTestProviderEnabled(provider, true)
            manager.setTestProviderLocation(provider, android.location.Location(provider).apply {
                latitude = 43.0731; longitude = -89.4012; accuracy = 100f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            })
            withTimeout(5000) { while (manager.getLastKnownLocation(provider) == null) delay(100) }
            val result = withTimeout(20_000) { PlaceLookup(app).suggest("The James", "", true) }
            println("EMULATOR_MOCK_LOCATION candidates=${result.addresses.size}; ${result.note}")
            assertFalse("Network location must work when fused has no fix", result.note.contains("location is unavailable"))
        } finally {
            runCatching { manager.removeTestProvider(provider) }
            runCatching { manager.removeTestProvider(fused) }
            shell("appops set ${app.packageName} android:mock_location default")
            activity.close()
        }
    }
}
