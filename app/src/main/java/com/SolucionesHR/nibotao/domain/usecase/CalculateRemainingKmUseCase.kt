package com.SolucionesHR.nibotao.domain.usecase

import com.SolucionesHR.nibotao.domain.model.AlertLevel
import com.SolucionesHR.nibotao.domain.model.AutonomyEstimate
import com.SolucionesHR.nibotao.domain.model.EstimateSource
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Calcula los km restantes de autonomía.
 *
 * Modo base (solo GPS):
 *   remaining = initialAutonomyKm - distanceTraveledKm
 *
 * Modo mejorado (con Bluetooth):
 *   Se usa el % de batería realmente consumido para derivar un ratio km/%
 *   real del trayecto actual, y se proyecta cuánto falta según el % restante.
 *   Ese resultado se pondera contra la estimación por GPS, dando cada vez más
 *   peso al dato real conforme avanza el recorrido (más km recorridos = más
 *   confianza en el ratio real observado).
 */
class CalculateRemainingKmUseCase @Inject constructor() {

    operator fun invoke(
        initialAutonomyKm: Float,
        distanceTraveledKm: Float,
        batteryPercentConsumed: Int?, // null si no hay Bluetooth
        batteryPercentRemaining: Int?,
        warningThresholdPercent: Float = 20f,
        criticalThresholdPercent: Float = 10f
    ): AutonomyEstimate {
        require(initialAutonomyKm > 0f) { "La autonomía inicial debe ser mayor a 0" }

        val gpsEstimate = max(0f, initialAutonomyKm - distanceTraveledKm)

        val finalEstimate: Float
        val source: EstimateSource

        if (batteryPercentConsumed != null && batteryPercentConsumed > 0 &&
            batteryPercentRemaining != null && distanceTraveledKm > 0f
        ) {
            // Ratio real observado: km recorridos por cada punto porcentual consumido.
            val realRatioKmPerPercent = distanceTraveledKm / batteryPercentConsumed
            val bluetoothEstimate = realRatioKmPerPercent * batteryPercentRemaining

            // Peso del dato real crece con la distancia recorrida (más muestra = más confianza),
            // hasta un máximo de 80% de peso para no descartar nunca el GPS por completo.
            val bluetoothWeight = min(0.8f, distanceTraveledKm / initialAutonomyKm)
            val gpsWeight = 1f - bluetoothWeight

            finalEstimate = (bluetoothEstimate * bluetoothWeight) + (gpsEstimate * gpsWeight)
            source = EstimateSource.GPS_PLUS_BLUETOOTH
        } else {
            finalEstimate = gpsEstimate
            source = EstimateSource.GPS_ONLY
        }

        val remainingPercentOfInitial = (finalEstimate / initialAutonomyKm) * 100f
        val alertLevel = when {
            remainingPercentOfInitial <= criticalThresholdPercent -> AlertLevel.CRITICAL_10
            remainingPercentOfInitial <= warningThresholdPercent -> AlertLevel.WARNING_20
            else -> AlertLevel.OK
        }

        return AutonomyEstimate(
            remainingKm = finalEstimate,
            distanceTraveledKm = distanceTraveledKm,
            source = source,
            alertLevel = alertLevel
        )
    }
}
