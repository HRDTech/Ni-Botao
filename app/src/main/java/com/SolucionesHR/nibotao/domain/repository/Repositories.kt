package com.SolucionesHR.nibotao.domain.repository

import com.SolucionesHR.nibotao.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    suspend fun createTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun finishTrip(
        tripId: Long,
        endTimestamp: Long,
        estimatedErrorKm: Float?,
        distanceTraveledKm: Float,
        batteryUsedPercent: Float?,
        finalBatteryVoltage: Float?
    )
    suspend fun updateLiveMetrics(tripId: Long, distanceTraveledKm: Float, batteryUsedPercent: Float?)
    suspend fun getActiveTrip(): Trip?
    suspend fun getTripById(tripId: Long): Trip?
    fun observeActiveTrip(): Flow<Trip?>
    fun observeTrip(tripId: Long): Flow<Trip?>
    fun observeHistory(): Flow<List<Trip>>
    suspend fun addLocationPoint(point: LocationPoint)
    suspend fun addBatterySample(sample: BatterySample)
    fun observeRoute(tripId: Long): Flow<List<LocationPoint>>
}

interface LocationRepository {
    /** Emite un flujo de puntos de ubicación válidos (ya filtrados de outliers). */
    fun observeLocation(): Flow<LocationPoint>
    fun hasLocationPermission(): Boolean
}

interface BleRepository {
    fun scanBatteryDevices(): Flow<List<BleDeviceInfo>>
    fun connect(deviceAddress: String): Flow<BluetoothBatteryState>
    fun disconnect()
}

data class BleDeviceInfo(val name: String?, val address: String, val rssi: Int)
