package com.SolucionesHR.nibotao.domain.model

/**
 * Representa un recorrido completo, desde que el usuario declara la autonomía
 * inicial de la batería hasta que finaliza el trayecto.
 */
data class Trip(
    val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val initialAutonomyKm: Float,
    val distanceTraveledKm: Float = 0f,
    val batteryUsedPercent: Float? = null, // null si nunca hubo conexión Bluetooth
    val bluetoothDeviceName: String? = null,
    val estimatedErrorKm: Float? = null,   // diferencia entre estimado y real, para calibración
    val initialBatteryVoltage: Float? = null,
    val finalBatteryVoltage: Float? = null,
    val status: TripStatus = TripStatus.ACTIVE
)

enum class TripStatus { ACTIVE, PAUSED, FINISHED }

/**
 * Punto de ubicación capturado durante un recorrido.
 */
data class LocationPoint(
    val tripId: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
    val timestamp: Long,
    val accuracyMeters: Float
)

/**
 * Muestra de batería real obtenida vía Bluetooth (GATT Battery Service u otro).
 */
data class BatterySample(
    val tripId: Long,
    val batteryPercent: Int,
    val timestamp: Long,
    val kmAtSample: Float
)

/**
 * Resultado del cálculo de autonomía restante en un instante dado.
 */
data class AutonomyEstimate(
    val remainingKm: Float,
    val distanceTraveledKm: Float,
    val source: EstimateSource,
    val alertLevel: AlertLevel
)

enum class EstimateSource { GPS_ONLY, GPS_PLUS_BLUETOOTH }

enum class AlertLevel { OK, WARNING_20, CRITICAL_10 }

/**
 * Estado en vivo del dispositivo Bluetooth conectado (opcional).
 */
data class BluetoothBatteryState(
    val deviceName: String?,
    val connected: Boolean,
    val batteryPercent: Int? = null
)

/** Perfil de vehículo guardado para no reingresar autonomía cada vez (mejora sección 13). */
data class VehicleProfile(
    val id: Long = 0,
    val name: String,
    val averageKmPerPercent: Float? = null // rendimiento histórico calibrado
)
