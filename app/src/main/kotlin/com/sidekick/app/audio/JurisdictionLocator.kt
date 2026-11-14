package com.sidekick.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import com.sidekick.core.RecordingJurisdiction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Which recording-consent rule applies here. Satellite positioning works without cell or Wi-Fi; the state lookup
 * uses borders bundled in the app. The position is used only for this lookup: never stored, logged or sent.
 * Any failure returns the strictest rule (RecordingJurisdiction.unknown).
 */
object JurisdictionLocator {
    suspend fun locate(context: Context): RecordingJurisdiction.Place = withContext(Dispatchers.IO) {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return@withContext RecordingJurisdiction.unknown("Location permission not granted")
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext RecordingJurisdiction.unknown("No location service")
        val location = runCatching { fresh(manager, fine) }.getOrNull() ?: runCatching { lastKnown(manager) }.getOrNull()
            ?: return@withContext RecordingJurisdiction.unknown("No location fix")
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        RecordingJurisdiction.locate(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toDouble() else null, ageMs)
    }

    @Suppress("MissingPermission")
    private suspend fun fresh(manager: LocationManager, fine: Boolean): Location? {
        if (Build.VERSION.SDK_INT < 30) return null
        val enabled = manager.getProviders(true)
        val provider = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            if (fine) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
        ).firstOrNull { it in enabled } ?: return null
        val signal = CancellationSignal()
        // A satellite fix without network help can take a while; never block listening on it.
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, { it.run() }) { continuation.resume(it) }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.elapsedRealtimeNanos }
}
