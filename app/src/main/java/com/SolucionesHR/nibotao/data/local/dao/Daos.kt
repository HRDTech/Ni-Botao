package com.SolucionesHR.nibotao.data.local.dao

import androidx.room.*
import com.SolucionesHR.nibotao.data.local.entity.BatterySampleEntity
import com.SolucionesHR.nibotao.data.local.entity.LocationPointEntity
import com.SolucionesHR.nibotao.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' OR status = 'PAUSED' LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' OR status = 'PAUSED' LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTrip(tripId: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripById(tripId: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE status = 'FINISHED' ORDER BY startTimestamp DESC")
    fun observeHistory(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET distanceTraveledKm = :distanceTraveledKm, batteryUsedPercent = :batteryUsedPercent WHERE id = :tripId")
    suspend fun updateLiveMetrics(tripId: Long, distanceTraveledKm: Float, batteryUsedPercent: Float?)

    @Query("UPDATE trips SET status = 'FINISHED', endTimestamp = :endTimestamp, estimatedErrorKm = :estimatedErrorKm, distanceTraveledKm = :distanceTraveledKm, batteryUsedPercent = :batteryUsedPercent, finalBatteryVoltage = :finalBatteryVoltage WHERE id = :tripId")
    suspend fun finishTrip(
        tripId: Long,
        endTimestamp: Long,
        estimatedErrorKm: Float?,
        distanceTraveledKm: Float,
        batteryUsedPercent: Float?,
        finalBatteryVoltage: Float?
    )
}

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insert(point: LocationPointEntity)

    @Insert
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeRoute(tripId: Long): Flow<List<LocationPointEntity>>
}

@Dao
interface BatterySampleDao {
    @Insert
    suspend fun insert(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_samples WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeSamples(tripId: Long): Flow<List<BatterySampleEntity>>
}
