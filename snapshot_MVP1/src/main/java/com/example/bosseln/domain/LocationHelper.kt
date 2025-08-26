package com.example.bosseln.domain

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    suspend fun getAccurateOnce(): LocationResultLite? = suspendCancellableCoroutine { cont ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) cont.resume(
                    LocationResultLite(loc.latitude, loc.longitude, loc.accuracy)
                ) else cont.resume(null)
            }
            .addOnFailureListener { cont.resume(null) }
    }
}

data class LocationResultLite(val lat: Double, val lon: Double, val accuracyM: Float)
