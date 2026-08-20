package com.SolucionesHR.nibotao.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val initialAutonomyKm: Float,
    val distanceTraveledKm: Float,
    val batteryUsedPercent: Float?,
    val bluetoothDeviceName: String?,
    val estimatedErrorKm: Float?,
    val initialBatteryVoltage: Float?,
    val finalBatteryVoltage: Float?,
    val status: String
)

@Entity(
    tableName = "location_points",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
    val timestamp: Long,
    val accuracyMeters: Float
)

@Entity(
    tableName = "battery_samples",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val batteryPercent: Int,
    val timestamp: Long,
    val kmAtSample: Float
)
