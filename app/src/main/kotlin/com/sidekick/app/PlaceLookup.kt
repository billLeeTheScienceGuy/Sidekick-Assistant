package com.sidekick.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.net.Uri
import android.os.*
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

data class PlaceResults(val addresses: List<String> = emptyList(), val note: String = "")

class PlaceLookup(private val context: Context) {
    suspend fun suggest(name: String, city: String, usePhone: Boolean): PlaceResults {
        if (name.isBlank()) return PlaceResults()
        if (!Geocoder.isPresent()) return PlaceResults(note = "Address lookup is unavailable. Keep the spoken place name or check Maps.")
        return try {
            withTimeout(15_000) {
                val location = if (usePhone) currentLocation() else null
                if (usePhone && location == null && city.isBlank()) return@withTimeout PlaceResults(note = "Phone location is unavailable. Set a city or keep the place unknown.")
                if (!usePhone && city.isBlank()) return@withTimeout PlaceResults(note = "Set a city in Settings to help identify this place.")
                val query = listOf(name.trim().take(200), if (location == null) city.trim() else "").filter { it.isNotBlank() }.joinToString(", ")
                val addresses = geocode(query, location).mapNotNull { it.getAddressLine(0)?.take(500) }.distinct().take(3)
                PlaceResults(addresses, if (addresses.isEmpty()) "No reliable address found. Keep the spoken name or check Maps."
                    else "Possible addresses only. Check the result before using it.")
            }
        } catch (_: TimeoutCancellationException) { PlaceResults(note = "Place lookup timed out. The spoken name is still available.") }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { PlaceResults(note = "Place lookup is unavailable. The spoken name is still available.") }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    internal suspend fun currentLocation(): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        fun fresh(location: Location): Boolean = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0..600_000_000_000L
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter(::fresh).maxByOrNull { it.elapsedRealtimeNanos }?.let { return it }
        for (provider in providers) {
            val location = withTimeoutOrNull(8_000L / providers.size) { requestLocation(manager, provider) }
            if (location != null && fresh(location)) return location
        }
        return null
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestLocation(manager: LocationManager, provider: String): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) { manager.removeUpdates(this); continuation.resume(location) }
                        override fun onProviderDisabled(provider: String) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(query: String, location: Location?): List<Address> {
        val coder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT < 33) return withContext(Dispatchers.IO) {
            if (location == null) coder.getFromLocationName(query, 3).orEmpty()
            else coder.getFromLocationName(query, 3, (location.latitude - .2).coerceAtLeast(-90.0), (location.longitude - .2).coerceAtLeast(-180.0),
                (location.latitude + .2).coerceAtMost(90.0), (location.longitude + .2).coerceAtMost(180.0)).orEmpty()
        }
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) { continuation.resume(addresses) }
                override fun onError(errorMessage: String?) { continuation.resume(emptyList()) }
            }
            if (location == null) coder.getFromLocationName(query, 3, listener)
            else coder.getFromLocationName(query, 3, (location.latitude - .2).coerceAtLeast(-90.0), (location.longitude - .2).coerceAtLeast(-180.0),
                (location.latitude + .2).coerceAtMost(90.0), (location.longitude + .2).coerceAtMost(180.0), listener)
        }
    }

    companion object {
        fun mapsUri(name: String, city: String): Uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
            .appendQueryParameter("api", "1").appendQueryParameter("query", listOf(name, city).filter { it.isNotBlank() }.joinToString(", ")).build()
    }
}
