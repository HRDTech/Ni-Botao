package com.SolucionesHR.nibotao.domain.usecase

import javax.inject.Inject
import kotlin.math.*

/**
 * Determina si con la autonomía restante alcanza para volver al punto de partida.
 * Usa distancia en línea recta (Haversine) multiplicada por un factor de corrección
 * (las rutas reales rara vez son líneas rectas).
 */
class CanReturnToStartUseCase @Inject constructor() {

    data class Result(val straightLineDistanceKm: Float, val estimatedReturnKm: Float, val canReturn: Boolean)

    operator fun invoke(
        currentLat: Double,
        currentLng: Double,
        startLat: Double,
        startLng: Double,
        remainingKm: Float,
        routeCorrectionFactor: Float = 1.3f // margen por curvas/calles vs línea recta
    ): Result {
        val straightLine = haversineKm(currentLat, currentLng, startLat, startLng)
        val estimatedReturn = straightLine * routeCorrectionFactor
        return Result(
            straightLineDistanceKm = straightLine.toFloat(),
            estimatedReturnKm = estimatedReturn.toFloat(),
            canReturn = estimatedReturn <= remainingKm
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
