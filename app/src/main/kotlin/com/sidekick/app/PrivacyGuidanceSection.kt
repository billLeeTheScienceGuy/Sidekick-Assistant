package com.sidekick.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sidekick.core.PrivacyRegions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PrivacyGuidanceSection(settings: GuidanceSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val city by settings.city.collectAsState()
    val usePhone by settings.privacyPhoneRegion.collectAsState()
    val cached by settings.privacyRegion.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true; note = null
            try {
                val result = PrivacyRegionLookup(context).lookup()
                settings.setPrivacyRegion(result)
                if (result == null) note = "Can’t find your region. Your chosen city will be used instead."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { settings.setPrivacyRegion(null); note = "Can’t find your region. Check location access or enter a city and state." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { settings.setPrivacyPhoneRegion(true); refresh() }
        else note = "Location access is off. You can use a city and state instead."
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("privacy-guidance")) { Text("Listening & privacy") }
    if (!expanded) return
    Text(PrivacyRegions.policy)
    Text(PrivacyRegions.limits)
    val permitted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val phone = cached?.takeIf { usePhone && permitted && it.fresh(System.currentTimeMillis()) }
    val region = phone?.region ?: PrivacyRegions.fromCity(city)
    val guidance = PrivacyRegions.guidance(region)
    Text(guidance.label, style = MaterialTheme.typography.titleMedium)
    Text(if (phone != null) "Using your nearby region. Refresh after 24 hours."
        else "Using the city and state you chose, if recognized. A city name alone may be unclear.")
    Text(guidance.note)
    Row {
        Switch(usePhone, { enabled ->
            if (!enabled) settings.setPrivacyPhoneRegion(false)
            else if (permitted) settings.setPrivacyPhoneRegion(true)
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !busy, modifier = Modifier.testTag("privacy-phone-region").controlLabel("Use my nearby region"))
        Text("Use my nearby region", Modifier.weight(1f))
    }
    Text("Refresh may send approximate location to Android’s address service. Only country, state and check time stay on this phone for up to 24 hours. No location history or background tracking. Turning this off clears the saved region.")
    if (usePhone) TextButton(onClick = { if (permitted) refresh() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, enabled = !busy,
        modifier = Modifier.testTag("privacy-refresh-region")) { Text(if (busy) "Finding region…" else "Refresh my region") }
    note?.let { Text(it) }
    Text(PrivacyRegions.checked, style = MaterialTheme.typography.bodySmall)
    guidance.source?.let { source -> TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source))) } }) { Text("Read official source") } }
}
