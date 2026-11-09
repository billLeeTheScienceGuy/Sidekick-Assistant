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
                if (result == null) note = "Region unavailable. The explicit city hint, if any, is used instead."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { settings.setPrivacyRegion(null); note = "Region unavailable. Check location access or use an explicit city and state." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { settings.setPrivacyPhoneRegion(true); refresh() }
        else note = "Location access is off. You can use an explicit city and state."
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("privacy-guidance")) { Text("Listening privacy & region guidance") }
    if (!expanded) return
    Text(PrivacyRegions.policy)
    Text(PrivacyRegions.limits)
    val permitted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val phone = cached?.takeIf { usePhone && permitted && it.fresh(System.currentTimeMillis()) }
    val region = phone?.region ?: PrivacyRegions.fromCity(city)
    val guidance = PrivacyRegions.guidance(region)
    Text(guidance.label, style = MaterialTheme.typography.titleMedium)
    Text(if (phone != null) "Source: approximate phone region, refreshed manually; expires after 24 hours."
        else "Source: explicit city/state in Settings, if recognized. A city alone is ambiguous.")
    Text(guidance.note)
    Row {
        Switch(usePhone, { enabled ->
            if (!enabled) settings.setPrivacyPhoneRegion(false)
            else if (permitted) settings.setPrivacyPhoneRegion(true)
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !busy, modifier = Modifier.testTag("privacy-phone-region").controlLabel("Use approximate phone region for privacy guidance"))
        Text("Use approximate phone region for guidance", Modifier.weight(1f))
    }
    Text("Optional: Refresh uses Android's location and address provider, which may send approximate coordinates online. Only country/state and the refresh time are cached locally; no street, coordinates or history are saved. No background location access. Turning this off clears the cache.")
    if (usePhone) TextButton(onClick = { if (permitted) refresh() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, enabled = !busy,
        modifier = Modifier.testTag("privacy-refresh-region")) { Text(if (busy) "Finding region…" else "Refresh phone region") }
    note?.let { Text(it) }
    Text(PrivacyRegions.checked, style = MaterialTheme.typography.bodySmall)
    guidance.source?.let { source -> TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source))) } }) { Text("Read official source") } }
}
