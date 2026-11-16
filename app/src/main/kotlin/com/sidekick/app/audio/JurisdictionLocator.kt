package com.sidekick.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import com.sidekick.core.RecordingJurisdiction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Which recording-consent and biometric rules apply here. Satellite positioning works without cell or Wi-Fi; the state
 * lookup uses borders bundled in the app. The position is used only for this lookup: never stored, logged or sent.
 * Any failure returns the strictest rule (RecordingJurisdiction.unknown).
 */
object JurisdictionLocator {
    const val UPDATE_MIN_TIME_MS = 60_000L
    const val UPDATE_MIN_DISTANCE_M = 500f
    const val FALLBACK_POLL_MS = 5 * 60_000L

    private fun fine(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun coarse(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun place(context: Context, location: Location): RecordingJurisdiction.Place {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        return RecordingJurisdiction.locate(location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else null, ageMs, precise = fine(context))
    }

    suspend fun locate(context: Context): RecordingJurisdiction.Place = withContext(Dispatchers.IO) {
        try {
            val fine = fine(context)
            if (!fine && !coarse(context)) return@withContext RecordingJurisdiction.unknown("Location permission not granted")
            val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext RecordingJurisdiction.unknown("No location service")
            val location = runCatching { fresh(manager, fine) }.getOrNull() ?: runCatching { lastKnown(manager) }.getOrNull()
                ?: return@withContext RecordingJurisdiction.unknown("No location fix")
            place(context, location)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { RecordingJurisdiction.unknown("Location lookup failed") }
    }

    /**
     * While listening: location updates (at least 500 m and 60 s apart) plus a 5-minute fallback poll. Every result,
     * including any failure (strictest rule), goes to [onPlace]. Runs until cancelled; the listener is always removed.
     */
    @Suppress("MissingPermission")
    suspend fun follow(context: Context, onPlace: (RecordingJurisdiction.Place) -> Unit) {
        onPlace(locate(context))
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location ->
            onPlace(try { place(context, location) } catch (_: Exception) { RecordingJurisdiction.unknown("Location lookup failed") })
        }
        var registered = false
        try {
            if (manager != null && (fine(context) || coarse(context))) {
                val enabled = manager.getProviders(true)
                val provider = listOfNotNull(
                    if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
                    if (fine(context)) LocationManager.GPS_PROVIDER else null,
                    LocationManager.NETWORK_PROVIDER,
                ).firstOrNull { it in enabled }
                if (provider != null) runCatching {
                    manager.requestLocationUpdates(provider, UPDATE_MIN_TIME_MS, UPDATE_MIN_DISTANCE_M, listener, Looper.getMainLooper())
                    registered = true
                }
            }
            while (true) {
                delay(FALLBACK_POLL_MS)
                onPlace(locate(context))
            }
        } finally {
            if (registered) runCatching { manager?.removeUpdates(listener) }
        }
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
