package com.SolucionesHR.nibotao.data.repository

import com.SolucionesHR.nibotao.data.local.dao.BatterySampleDao
import com.SolucionesHR.nibotao.data.local.dao.LocationPointDao
import com.SolucionesHR.nibotao.data.local.dao.TripDao
import com.SolucionesHR.nibotao.data.local.entity.BatterySampleEntity
import com.SolucionesHR.nibotao.data.local.entity.LocationPointEntity
import com.SolucionesHR.nibotao.data.local.entity.TripEntity
import com.SolucionesHR.nibotao.domain.model.LocationPoint
import com.SolucionesHR.nibotao.domain.model.BatterySample
import com.SolucionesHR.nibotao.domain.model.Trip
import com.SolucionesHR.nibotao.domain.model.TripStatus
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TripRepositoryImpl(
    private val tripDao: TripDao,
    private val locationPointDao: LocationPointDao,
    private val batterySampleDao: BatterySampleDao
) : TripRepository {

    override suspend fun createTrip(trip: Trip): Long =
        tripDao.insert(trip.toEntity())

    override suspend fun updateTrip(trip: Trip) {
        tripDao.update(trip.toEntity())
    }

    override suspend fun finishTrip(
        tripId: Long,
        endTimestamp: Long,
        estimatedErrorKm: Float?,
        distanceTraveledKm: Float,
        batteryUsedPercent: Float?,
        finalBatteryVoltage: Float?
    ) {
        tripDao.finishTrip(
            tripId = tripId,
            endTimestamp = endTimestamp,
            estimatedErrorKm = estimatedErrorKm,
            distanceTraveledKm = distanceTraveledKm,
            batteryUsedPercent = batteryUsedPercent,
            finalBatteryVoltage = finalBatteryVoltage
        )
    }

    override suspend fun updateLiveMetrics(tripId: Long, distanceTraveledKm: Float, batteryUsedPercent: Float?) {
        tripDao.updateLiveMetrics(tripId, distanceTraveledKm, batteryUsedPercent)
    }

    override suspend fun getActiveTrip(): Trip? =
        tripDao.getActiveTrip()?.toDomain()

    override suspend fun getTripById(tripId: Long): Trip? =
        tripDao.getTripById(tripId)?.toDomain()

    override fun observeTrip(tripId: Long): Flow<Trip?> =
        tripDao.observeTrip(tripId).map { it?.toDomain() }

    override fun observeHistory(): Flow<List<Trip>> =
        tripDao.observeHistory().map { list -> list.map { it.toDomain() } }

    override suspend fun addLocationPoint(point: LocationPoint) {
        locationPointDao.insert(point.toEntity())
    }

    override fun observeActiveTrip(): Flow<Trip?> =
        tripDao.observeActiveTrip().map { it?.toDomain() }

    override suspend fun addBatterySample(sample: BatterySample) {
        batterySampleDao.insert(sample.toEntity())
    }

    override fun observeRoute(tripId: Long): Flow<List<LocationPoint>> =
        locationPointDao.observeRoute(tripId).map { list -> list.map { it.toDomain() } }
}

// ---- Mappers ----

private fun Trip.toEntity() = TripEntity(
    id = id,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    initialAutonomyKm = initialAutonomyKm,
    distanceTraveledKm = distanceTraveledKm,
    batteryUsedPercent = batteryUsedPercent,
    bluetoothDeviceName = bluetoothDeviceName,
    estimatedErrorKm = estimatedErrorKm,
    initialBatteryVoltage = initialBatteryVoltage,
    finalBatteryVoltage = finalBatteryVoltage,
    status = status.name
)

private fun TripEntity.toDomain() = Trip(
    id = id,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    initialAutonomyKm = initialAutonomyKm,
    distanceTraveledKm = distanceTraveledKm,
    batteryUsedPercent = batteryUsedPercent,
    bluetoothDeviceName = bluetoothDeviceName,
    estimatedErrorKm = estimatedErrorKm,
    initialBatteryVoltage = initialBatteryVoltage,
    finalBatteryVoltage = finalBatteryVoltage,
    status = TripStatus.valueOf(status)
)

private fun LocationPoint.toEntity() = LocationPointEntity(
    tripId = tripId, lat = lat, lng = lng, speedKmh = speedKmh,
    timestamp = timestamp, accuracyMeters = accuracyMeters
)

private fun LocationPointEntity.toDomain() = LocationPoint(
    tripId = tripId, lat = lat, lng = lng, speedKmh = speedKmh,
    timestamp = timestamp, accuracyMeters = accuracyMeters
)

private fun BatterySample.toEntity() = BatterySampleEntity(
    tripId = tripId, batteryPercent = batteryPercent, timestamp = timestamp, kmAtSample = kmAtSample
)
