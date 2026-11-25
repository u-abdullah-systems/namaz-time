package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class DeviceGeoLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

class LocationProvider(private val context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): DeviceGeoLocation? {
        if (!hasLocationPermission()) return null

        val lastLoc = suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }

        val resolvedLoc = lastLoc ?: suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return null

        val label = reverseGeocodeLabel(resolvedLoc.latitude, resolvedLoc.longitude)
        return DeviceGeoLocation(
            latitude = resolvedLoc.latitude,
            longitude = resolvedLoc.longitude,
            label = label
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeLabel(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext String.format(Locale.US, "%.2f, %.2f", lat, lon)
                }
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { list ->
                            cont.resume(list.firstOrNull())
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
                val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
                val region = address?.adminArea ?: address?.countryCode
                when {
                    !city.isNullOrBlank() && !region.isNullOrBlank() && city != region -> "$city, $region"
                    !city.isNullOrBlank() -> city
                    !region.isNullOrBlank() -> region
                    else -> String.format(Locale.US, "%.2f, %.2f", lat, lon)
                }
            } catch (_: Exception) {
                String.format(Locale.US, "%.2f, %.2f", lat, lon)
            }
        }
}
