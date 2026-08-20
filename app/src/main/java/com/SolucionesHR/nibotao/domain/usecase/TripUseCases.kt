package com.SolucionesHR.nibotao.domain.usecase

import com.SolucionesHR.nibotao.domain.model.Trip
import com.SolucionesHR.nibotao.domain.model.TripStatus
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import javax.inject.Inject

class StartTripUseCase @Inject constructor(
    private val repository: TripRepository
) {
    suspend operator fun invoke(initialAutonomyKm: Float, initialBatteryVoltage: Float, bluetoothDeviceName: String?): Long {
        require(initialAutonomyKm > 0f) { "Ingresa un valor de autonomía mayor a 0 km" }
        require(initialBatteryVoltage > 0f) { "Ingresa un voltaje inicial mayor a 0" }
        val trip = Trip(
            startTimestamp = System.currentTimeMillis(),
            initialAutonomyKm = initialAutonomyKm,
            bluetoothDeviceName = bluetoothDeviceName,
            initialBatteryVoltage = initialBatteryVoltage,
            status = TripStatus.ACTIVE
        )
        return repository.createTrip(trip)
    }
}

class StopTripUseCase @Inject constructor(
    private val repository: TripRepository
) {
    suspend operator fun invoke(
        tripId: Long,
        estimatedErrorKm: Float?,
        distanceTraveledKm: Float,
        batteryUsedPercent: Float?,
        finalBatteryVoltage: Float?
    ) {
        repository.finishTrip(
            tripId = tripId,
            endTimestamp = System.currentTimeMillis(),
            estimatedErrorKm = estimatedErrorKm,
            distanceTraveledKm = distanceTraveledKm,
            batteryUsedPercent = batteryUsedPercent,
            finalBatteryVoltage = finalBatteryVoltage
        )
    }
}

class SetTripStatusUseCase @Inject constructor(
    private val repository: TripRepository
) {
    suspend operator fun invoke(trip: Trip, status: TripStatus) {
        repository.updateTrip(trip.copy(status = status))
    }
}
