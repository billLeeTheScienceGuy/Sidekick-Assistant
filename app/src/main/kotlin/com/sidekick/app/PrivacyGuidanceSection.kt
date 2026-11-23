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
                if (result == null) note = tr(R.string.can_t_find_your_region_your)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { settings.setPrivacyRegion(null); note = tr(R.string.can_t_find_your_region_check) }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { settings.setPrivacyPhoneRegion(true); refresh() }
        else note = tr(R.string.location_access_is_off_you_can)
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("privacy-guidance")) { Text(tr(R.string.listening_privacy)) }
    if (!expanded) return
    Text(CoreText.localize(PrivacyRegions.policy))
    Text(CoreText.localize(PrivacyRegions.limits))
    val permitted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val phone = cached?.takeIf { usePhone && permitted && it.fresh(System.currentTimeMillis()) }
    val region = phone?.region ?: PrivacyRegions.fromCity(city)
    val guidance = PrivacyRegions.guidance(region)
    Text(CoreText.localize(guidance.label), style = MaterialTheme.typography.titleMedium)
    Text(if (phone != null) tr(R.string.using_your_nearby_region_refresh_after)
        else tr(R.string.using_the_city_and_state_you))
    Text(CoreText.localize(guidance.note))
    Row {
        Switch(usePhone, { enabled ->
            if (!enabled) settings.setPrivacyPhoneRegion(false)
            else if (permitted) settings.setPrivacyPhoneRegion(true)
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !busy, modifier = Modifier.testTag("privacy-phone-region").controlLabel(tr(R.string.use_my_nearby_region)))
        Text(tr(R.string.use_my_nearby_region), Modifier.weight(1f))
    }
    Text(tr(R.string.refresh_may_send_your_rough_location))
    if (usePhone) TextButton(onClick = { if (permitted) refresh() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, enabled = !busy,
        modifier = Modifier.testTag("privacy-refresh-region")) { Text(if (busy) tr(R.string.finding_region) else tr(R.string.refresh_my_region)) }
    note?.let { Text(it) }
    Text(CoreText.localize(PrivacyRegions.checked), style = MaterialTheme.typography.bodySmall)
    guidance.source?.let { source -> TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source))) } }) { Text(tr(R.string.read_official_source)) } }
}
