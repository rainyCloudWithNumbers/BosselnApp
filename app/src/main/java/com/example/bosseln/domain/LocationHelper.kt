package com.example.bosseln.domain

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class SimpleFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float? = null,
    val timeMs: Long = System.currentTimeMillis()
)

class LocationHelper(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Schneller Einzel-Fix (kann null liefern).
     */
    @SuppressLint("MissingPermission")
    suspend fun getAccurateOnce(): SimpleFix? {
        // getCurrentLocation bevorzugen, sonst letzte bekannte
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc.toSimpleFix())
                    } else {
                        // fallback: lastLocation
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                cont.resume(last?.toSimpleFix())
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }

            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    /**
     * Sammelt für ein kurzes Zeitfenster mehrere Fixes und gibt einen robust gemittelten Fix zurück.
     * - Timeout: [timeoutMs]
     * - Zielgenauigkeit: [desiredAccM]
     * - Max. Stichproben: [maxSamples]
     */
    @SuppressLint("MissingPermission")
    suspend fun getBestFixWithin(
        timeoutMs: Long = 3000L,
        desiredAccM: Float = 15f,
        maxSamples: Int = 12
    ): SimpleFix? {

        val samples = mutableListOf<SimpleFix>()

        // Optional: initialer schneller Fix
        getAccurateOnce()?.let { samples += it }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, /*interval*/ 200L)
            .setMinUpdateIntervalMillis(100L)
            .setMaxUpdates(maxSamples)
            .build()

        val finished = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        for (l in result.locations) {
                            val fix = l.toSimpleFix()
                            samples += fix
                            // Frühabbruch bei Zielgenauigkeit oder wenn genug Samples
                            if ((fix.accuracyM ?: Float.MAX_VALUE) <= desiredAccM ||
                                samples.size >= maxSamples
                            ) {
                                client.removeLocationUpdates(this)
                                if (cont.isActive) cont.resume(Unit)
                                return
                            }
                        }
                    }
                }
                client.requestLocationUpdates(req, cb, Looper.getMainLooper())
                cont.invokeOnCancellation { client.removeLocationUpdates(cb) }
            }
        }

        // Sicherheit: Updates stoppen, falls Timeout ausgelöst hat
        // (wenn wir hier sind, sind Updates ohnehin abgemeldet, aber doppelt hält besser)
        // -> kein action nötig

        if (samples.isEmpty()) return null

        // robuste Auswahl/Mittelung:
        val best = samples.minByOrNull { it.accuracyM ?: Float.MAX_VALUE }

        // nur sinnvolle Werte mitteln
        val good = samples.filter { it.accuracyM != null && it.accuracyM!! < 80f }
        if (good.size < 3) return best

        // Gewichteter Mittelwert (Gewicht = 1/acc^2)
        var sw = 0.0
        var slat = 0.0
        var slon = 0.0
        var minAcc = Float.MAX_VALUE
        for (s in good) {
            val acc = s.accuracyM!!.toDouble().coerceAtLeast(1.0)
            val w = 1.0 / (acc * acc)
            sw += w
            slat += w * s.lat
            slon += w * s.lon
            if (s.accuracyM!! < minAcc) minAcc = s.accuracyM!!
        }
        return SimpleFix(slat / sw, slon / sw, minAcc)
    }

    private fun Location.toSimpleFix(): SimpleFix =
        SimpleFix(latitude, longitude, accuracy, time)
}
