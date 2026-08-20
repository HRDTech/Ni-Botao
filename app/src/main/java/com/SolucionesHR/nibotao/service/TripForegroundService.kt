package com.SolucionesHR.nibotao.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import androidx.core.app.NotificationCompat
import com.SolucionesHR.nibotao.NiBotaoApp
import com.SolucionesHR.nibotao.MainActivity
import com.SolucionesHR.nibotao.R
import com.SolucionesHR.nibotao.di.ServiceLocator
import com.SolucionesHR.nibotao.domain.model.*
import com.SolucionesHR.nibotao.domain.repository.BleRepository
import com.SolucionesHR.nibotao.domain.repository.LocationRepository
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import com.SolucionesHR.nibotao.domain.usecase.CalculateRemainingKmUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Servicio en primer plano que:
 *  - Suscribe la ubicación (GPS) y va acumulando distancia recorrida.
 *  - Si hay Bluetooth conectado, escucha el % de batería real.
 *  - Publica el estado combinado vía StateFlow para que la UI (Dashboard) lo consuma.
 *  - Persiste periódicamente los puntos en Room (batch, no en cada punto).
 *  - Actualiza la notificación persistente con los km restantes.
 *
 * START_STICKY: si el sistema mata el proceso por presión de memoria, se reintenta
 * reiniciar el servicio. El Trip activo se puede recuperar leyendo la BD (observeActiveTrip).
 */
class TripForegroundService : Service() {

    private lateinit var locationRepository: LocationRepository
    private lateinit var bleRepository: BleRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var calculateRemainingKmUseCase: CalculateRemainingKmUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentTrip: Trip? = null
    private var lastLocation: Location? = null
    private var totalDistanceKm = 0f
    private var lastBatteryPercent: Int? = null
    private var initialBatteryPercent: Int? = null
    private var lastLiveMetricsPersistAt = 0L

    private var locationJob: Job? = null
    private var bluetoothJob: Job? = null
    private var tripStatusJob: Job? = null

    private val pendingPoints = mutableListOf<LocationPoint>()

    private val _state = MutableStateFlow(TripLiveState())
    val state = _state.asStateFlow()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_INITIAL_AUTONOMY = "extra_initial_autonomy"
        const val EXTRA_BLUETOOTH_ADDRESS = "extra_bluetooth_address"
        private const val PERSIST_BATCH_SIZE = 5
        private const val LIVE_METRICS_PERSIST_INTERVAL_MS = 5_000L
        private const val MIN_DISTANCE_STEP_METERS = 3f

        private val _sharedState = MutableStateFlow(TripLiveState())
        val sharedState = _sharedState.asStateFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Initialize dependencies from ServiceLocator
        locationRepository = ServiceLocator.locationRepository
        bleRepository = ServiceLocator.bleRepository
        tripRepository = ServiceLocator.tripRepository
        calculateRemainingKmUseCase = CalculateRemainingKmUseCase()

        startForeground(NOTIFICATION_ID, buildNotification(0f, 0f))

        serviceScope.launch {
            startOrRestoreSession(intent)
        }

        return START_STICKY
    }

    private suspend fun startOrRestoreSession(intent: Intent?) {
        val inputTripId = intent?.getLongExtra(EXTRA_TRIP_ID, -1L) ?: -1L
        val inputAutonomy = intent?.getFloatExtra(EXTRA_INITIAL_AUTONOMY, -1f) ?: -1f
        val bluetoothAddress = intent?.getStringExtra(EXTRA_BLUETOOTH_ADDRESS)

        val activeTrip = if (inputTripId > 0) {
            tripRepository.getTripById(inputTripId)
        } else {
            tripRepository.getActiveTrip()
        }

        val trip = activeTrip ?: run {
            stopSelf()
            return
        }

        currentTrip = trip
        totalDistanceKm = max(totalDistanceKm, trip.distanceTraveledKm)
        val initialAutonomy = if (inputAutonomy > 0f) inputAutonomy else trip.initialAutonomyKm

        publishState(
            _state.value.copy(
                remainingKm = (initialAutonomy - totalDistanceKm).coerceAtLeast(0f),
                distanceTraveledKm = totalDistanceKm,
                initialBatteryPercent = initialBatteryPercent
            )
        )

        locationJob?.cancel()
        bluetoothJob?.cancel()
        tripStatusJob?.cancel()

        tripStatusJob = observeTripStatus(trip.id)

        locationJob = observeLocationUpdates(trip.id, initialAutonomy)
        if (!bluetoothAddress.isNullOrBlank() && bluetoothAddress != "none") {
            bluetoothJob = observeBluetoothUpdates(trip.id, bluetoothAddress)
        }
    }

    private fun observeTripStatus(tripId: Long): Job = serviceScope.launch {
        tripRepository.observeTrip(tripId).collect { trip ->
            currentTrip = trip ?: currentTrip
            if (trip?.status != TripStatus.ACTIVE) {
                // Evita sumar un salto grande al reanudar despues de una pausa.
                lastLocation = null
            }
        }
    }

    private fun observeLocationUpdates(tripId: Long, initialAutonomy: Float): Job = serviceScope.launch {
        locationRepository.observeLocation().collect { rawPoint ->
            val point = rawPoint.copy(tripId = tripId)
            val newLocation = Location("gps").apply { latitude = point.lat; longitude = point.lng }

            if (currentTrip?.status != TripStatus.ACTIVE) {
                lastLocation = newLocation
                publishState(_state.value.copy(currentLat = point.lat, currentLng = point.lng, speedKmh = 0f))
                return@collect
            }

            val previous = lastLocation
            if (previous != null) {
                val stepMeters = previous.distanceTo(newLocation)
                val minAcceptedStep = max(MIN_DISTANCE_STEP_METERS, point.accuracyMeters * 0.35f)
                if (stepMeters >= minAcceptedStep) {
                    totalDistanceKm += stepMeters / 1000f
                }
            }
            lastLocation = newLocation

            pendingPoints.add(point)
            if (pendingPoints.size >= PERSIST_BATCH_SIZE) {
                val toPersist = pendingPoints.toList()
                pendingPoints.clear()
                toPersist.forEach { tripRepository.addLocationPoint(it) }
            }

            val estimate = calculateRemainingKmUseCase(
                initialAutonomyKm = initialAutonomy,
                distanceTraveledKm = totalDistanceKm,
                batteryPercentConsumed = initialBatteryPercent?.let { init -> lastBatteryPercent?.let { init - it } },
                batteryPercentRemaining = lastBatteryPercent
            )

            publishState(_state.value.copy(
                remainingKm = estimate.remainingKm,
                distanceTraveledKm = totalDistanceKm,
                speedKmh = point.speedKmh,
                alertLevel = estimate.alertLevel,
                currentLat = point.lat,
                currentLng = point.lng,
                initialBatteryPercent = initialBatteryPercent
            ))

            maybePersistLiveMetrics(tripId)

            updateNotification(estimate.remainingKm, point.speedKmh)
        }
    }

    private fun observeBluetoothUpdates(tripId: Long, deviceAddress: String): Job = serviceScope.launch {
        bleRepository.connect(deviceAddress).collect { batteryState ->
            publishState(_state.value.copy(
                bluetoothConnected = batteryState.connected,
                bluetoothDeviceName = batteryState.deviceName,
                batteryPercent = batteryState.batteryPercent,
                initialBatteryPercent = initialBatteryPercent
            ))
            batteryState.batteryPercent?.let { percent ->
                if (initialBatteryPercent == null) initialBatteryPercent = percent
                lastBatteryPercent = percent
                serviceScope.launch {
                    tripRepository.addBatterySample(
                        BatterySample(
                            tripId = tripId,
                            batteryPercent = percent,
                            timestamp = System.currentTimeMillis(),
                            kmAtSample = totalDistanceKm
                        )
                    )
                    tripRepository.updateLiveMetrics(tripId, totalDistanceKm, calculateBatteryUsedPercent())
                }
            }
        }
    }

    private fun publishState(state: TripLiveState) {
        _state.value = state
        _sharedState.value = state
    }

    private suspend fun maybePersistLiveMetrics(tripId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastLiveMetricsPersistAt < LIVE_METRICS_PERSIST_INTERVAL_MS) return
        lastLiveMetricsPersistAt = now
        tripRepository.updateLiveMetrics(tripId, totalDistanceKm, calculateBatteryUsedPercent())
    }

    private fun calculateBatteryUsedPercent(): Float? {
        val initial = initialBatteryPercent ?: return null
        val current = lastBatteryPercent ?: return null
        return (initial - current).coerceAtLeast(0).toFloat()
    }

    private fun buildNotification(remainingKm: Float, speedKmh: Float): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NiBotaoApp.TRIP_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recorrido en curso")
            .setContentText("Restan ${"%.1f".format(remainingKm)} km · ${"%.0f".format(speedKmh)} km/h")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(remainingKm: Float, speedKmh: Float) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(remainingKm, speedKmh))
    }

    override fun onDestroy() {
        // Persistir cualquier punto pendiente antes de morir
        val tripId = currentTrip?.id
        if (pendingPoints.isNotEmpty()) {
            val toPersist = pendingPoints.toList()
            pendingPoints.clear()
            runBlocking(Dispatchers.IO) {
                toPersist.forEach { tripRepository.addLocationPoint(it) }
            }
        }
        if (tripId != null) {
            runBlocking(Dispatchers.IO) {
                tripRepository.updateLiveMetrics(tripId, totalDistanceKm, calculateBatteryUsedPercent())
            }
        }
        bleRepository.disconnect()
        locationJob?.cancel()
        bluetoothJob?.cancel()
        tripStatusJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

data class TripLiveState(
    val remainingKm: Float = 0f,
    val distanceTraveledKm: Float = 0f,
    val speedKmh: Float = 0f,
    val alertLevel: AlertLevel = AlertLevel.OK,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val bluetoothConnected: Boolean = false,
    val bluetoothDeviceName: String? = null,
    val batteryPercent: Int? = null,
    val initialBatteryPercent: Int? = null
)
