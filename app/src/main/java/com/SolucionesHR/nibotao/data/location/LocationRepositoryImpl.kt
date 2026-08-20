package com.SolucionesHR.nibotao.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.SolucionesHR.nibotao.domain.model.LocationPoint
import com.SolucionesHR.nibotao.domain.repository.LocationRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Envuelve FusedLocationProviderClient y aplica filtros de calidad:
 *  - Descarta puntos con precisión (accuracy) mayor a MAX_ACCEPTABLE_ACCURACY_M.
 *  - Descarta saltos físicamente imposibles (velocidad implícita > MAX_PLAUSIBLE_SPEED_KMH).
 * Esto evita sumar km falsos por ruido de GPS (ej. rebotes en interiores/túneles).
 */
class LocationRepositoryImpl(
    private val context: Context
) : LocationRepository {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val MAX_ACCEPTABLE_ACCURACY_M = 25f
        private const val MAX_PLAUSIBLE_SPEED_KMH = 150f
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val MIN_UPDATE_INTERVAL_MS = 1000L
        private const val MIN_DISPLACEMENT_M = 2f
    }

    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun observeLocation(): Flow<LocationPoint> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Falta permiso ACCESS_FINE_LOCATION"))
            return@callbackFlow
        }

        var lastAccepted: Location? = null

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_M)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (location.accuracy > MAX_ACCEPTABLE_ACCURACY_M) return // señal poco confiable

                val previous = lastAccepted
                if (previous != null) {
                    val deltaSeconds = (location.time - previous.time) / 1000.0
                    if (deltaSeconds > 0) {
                        val distanceMeters = previous.distanceTo(location)
                        val impliedSpeedKmh = (distanceMeters / deltaSeconds) * 3.6
                        if (impliedSpeedKmh > MAX_PLAUSIBLE_SPEED_KMH) return // salto imposible, descartar
                    }
                }

                lastAccepted = location
                trySend(
                    LocationPoint(
                        tripId = 0, // se asigna en el Service/UseCase antes de persistir
                        lat = location.latitude,
                        lng = location.longitude,
                        speedKmh = (location.speed * 3.6f),
                        timestamp = location.time,
                        accuracyMeters = location.accuracy
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
