package com.sidekick.app.audio

import com.sidekick.app.R
import com.sidekick.app.tr

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
 * Any failure returns the strictest rule (RecordingJurisdiction.unknown), and so does a position from a mock-location
 * app. The city in Settings is never used here: only the phone's own position can loosen the rules.
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
            if (location.hasAccuracy()) location.accuracy.toDouble() else null, ageMs, precise = fine(context), simulated = simulated(location))
    }

    @Suppress("DEPRECATION")
    private fun simulated(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider

    suspend fun locate(context: Context, lowPower: Boolean = false): RecordingJurisdiction.Place = withContext(Dispatchers.IO) {
        try {
            val fine = fine(context)
            if (!fine && !coarse(context)) return@withContext RecordingJurisdiction.unknown(tr(R.string.location_permission_not_granted))
            val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext RecordingJurisdiction.unknown(tr(R.string.no_location_service))
            val location = runCatching { fresh(manager, fine, lowPower) }.getOrNull() ?: runCatching { lastKnown(manager) }.getOrNull()
                ?: return@withContext RecordingJurisdiction.unknown(tr(R.string.no_location_fix))
            place(context, location)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { RecordingJurisdiction.unknown(tr(R.string.location_lookup_failed)) }
    }

    /** Within this distance of another state: precise location (GPS when allowed). Back to low power beyond FAR. */
    const val NEAR_BORDER_METERS = 20_000.0
    const val FAR_BORDER_METERS = 25_000.0
    const val LOW_POWER_INTERVAL_MS = 5 * 60_000L

    /**
     * While listening: far from every state border, low-power location (Wi-Fi and cell, no GPS) at most every 5 minutes;
     * within 20 km of a border, precise updates (at least 500 m and 60 s apart), as before. A 5-minute poll in the same
     * mode keeps a standing phone's fix fresh. Low-power fixes report a larger accuracy, which widens the border buffer
     * (stricter rules when unsure). Every result, including any failure (strictest rule), goes to [onPlace]. Runs until
     * cancelled; the listener is always removed.
     */
    @Suppress("MissingPermission")
    suspend fun follow(context: Context, onPlace: (RecordingJurisdiction.Place) -> Unit) {
        var precise = true
        // Test switch (phone battery comparison only): the old behaviour, precise location everywhere.
        val legacy = (context.packageName.endsWith(".benchmark") || context.packageName.endsWith(".compacttest")) &&
            context.getSharedPreferences("test-switches", Context.MODE_PRIVATE).getBoolean("legacy_capture", false)
        fun nearBorder(location: Location): Boolean {
            if (legacy) return true
            val d = RecordingJurisdiction.nearestBorderMeters(location.latitude, location.longitude, FAR_BORDER_METERS)
            return if (precise) d != null else d != null && d <= NEAR_BORDER_METERS
        }
        val manager = context.getSystemService(LocationManager::class.java)
        var listener: LocationListener? = null
        fun register() {
            if (manager == null || !(fine(context) || coarse(context))) return
            listener?.let { runCatching { manager.removeUpdates(it) } }
            val l = LocationListener { location ->
                onPlace(try { place(context, location) } catch (_: Exception) { RecordingJurisdiction.unknown(tr(R.string.location_lookup_failed)) })
                val near = runCatching { nearBorder(location) }.getOrDefault(true)
                if (near != precise) { precise = near; register() }
            }
            val enabled = manager.getProviders(true)
            if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in enabled) {
                val request = android.location.LocationRequest.Builder(if (precise) UPDATE_MIN_TIME_MS else LOW_POWER_INTERVAL_MS)
                    .setQuality(if (precise) android.location.LocationRequest.QUALITY_HIGH_ACCURACY else android.location.LocationRequest.QUALITY_LOW_POWER)
                    .setMinUpdateDistanceMeters(UPDATE_MIN_DISTANCE_M).build()
                runCatching { manager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, request, context.mainExecutor, l); listener = l }
            } else {
                val provider = listOfNotNull(
                    if (precise && fine(context)) LocationManager.GPS_PROVIDER else null,
                    LocationManager.NETWORK_PROVIDER,
                    if (fine(context)) LocationManager.GPS_PROVIDER else null,
                ).firstOrNull { it in enabled }
                if (provider != null) runCatching {
                    manager.requestLocationUpdates(provider, if (precise) UPDATE_MIN_TIME_MS else LOW_POWER_INTERVAL_MS, UPDATE_MIN_DISTANCE_M, l, Looper.getMainLooper())
                    listener = l
                }
            }
        }
        try {
            onPlace(locate(context))
            // The first fix is precise; far from borders, switch to low power from here on.
            val start = runCatching { if (manager != null) lastKnown(manager) else null }.getOrNull()
            if (start != null && !legacy) precise = runCatching { RecordingJurisdiction.nearestBorderMeters(start.latitude, start.longitude, NEAR_BORDER_METERS) != null }.getOrDefault(true)
            withContext(Dispatchers.Main) { register() }
            while (true) {
                delay(FALLBACK_POLL_MS)
                onPlace(locate(context, lowPower = !precise))
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { listener?.let { l -> runCatching { manager?.removeUpdates(l) } } }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun fresh(manager: LocationManager, fine: Boolean, lowPower: Boolean = false): Location? {
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
                if (lowPower && Build.VERSION.SDK_INT >= 31 && provider == LocationManager.FUSED_PROVIDER)
                    manager.getCurrentLocation(provider, android.location.LocationRequest.Builder(0)
                        .setQuality(android.location.LocationRequest.QUALITY_LOW_POWER).build(), signal, { it.run() }) { continuation.resume(it) }
                else manager.getCurrentLocation(provider, signal, { it.run() }) { continuation.resume(it) }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.elapsedRealtimeNanos }
}
