package com.sidekick.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.sidekick.app.audio.ListeningService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host revokes access after an armed emulator session, outside the killed app process. */
class PermissionRecoveryTest {
    @Test fun revokedMicrophoneAccessClearsPersistedRecoveryAndRegrantDoesNotRearm() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyRevokedMicrophone") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as SidekickApplication
        check(app.packageName.endsWith(".benchmark"))
        assertNotEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        assertFalse(app.assistant.recover())
        assertFalse(app.assistant.armed)
        assertFalse(ListeningService.state.value.recording)
        i.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        assertFalse("Permission restoration is not a new listening start", app.assistant.recover())
        assertFalse(app.assistant.armed)
    }
}
