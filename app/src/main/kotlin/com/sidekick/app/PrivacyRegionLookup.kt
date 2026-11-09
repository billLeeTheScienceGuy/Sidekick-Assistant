package com.sidekick.app

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.sidekick.core.PrivacyRegion
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

/** User-triggered approximate lookup. Only country/admin area leave this function. No background tracking. */
class PrivacyRegionLookup(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun lookup(): PrivacyRegion? = withTimeoutOrNull(12_000) {
        if (!Geocoder.isPresent()) return@withTimeoutOrNull null
        val location = PlaceLookup(context).currentLocation() ?: return@withTimeoutOrNull null
        val coder = Geocoder(context, Locale.ENGLISH)
        val addresses = if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { continuation ->
            coder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) { if (continuation.isActive) continuation.resume(addresses) }
                override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(emptyList()) }
            })
        } else withContext(Dispatchers.IO) { coder.getFromLocation(location.latitude, location.longitude, 1).orEmpty() }
        addresses.firstOrNull()?.let { address ->
            val country = address.countryCode?.uppercase(Locale.ROOT).orEmpty()
            if (country.matches(Regex("[A-Z]{2}"))) PrivacyRegion(country, address.adminArea.orEmpty().take(80)) else null
        }
    }
}
