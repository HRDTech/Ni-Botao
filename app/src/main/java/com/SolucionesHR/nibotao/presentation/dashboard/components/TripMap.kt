package com.SolucionesHR.nibotao.presentation.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.SolucionesHR.nibotao.domain.model.AlertLevel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Mapa en vivo con:
 *  - Polyline de la ruta recorrida, coloreada según el nivel de alerta de autonomía.
 *  - Marcador de posición actual.
 *  - Círculo de "radio de alcance" alrededor del punto de partida según los km restantes.
 *
 * Usa OSMDroid vía AndroidView porque, a la fecha, no tiene un wrapper Compose oficial maduro.
 * Todo el ciclo de vida del MapView se maneja con DisposableEffect para evitar memory leaks.
 */
@Composable
fun TripMap(
    routePoints: List<Pair<Double, Double>>,
    currentPosition: Pair<Double, Double>?,
    startPosition: Pair<Double, Double>?,
    remainingRadiusMeters: Double,
    alertLevel: AlertLevel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val routeColor = when (alertLevel) {
        AlertLevel.OK -> ComposeColor(0xFF2E7D32)
        AlertLevel.WARNING_20 -> ComposeColor(0xFFF9A825)
        AlertLevel.CRITICAL_10 -> ComposeColor(0xFFC62828)
    }

    DisposableEffect(Unit) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
        onDispose { mapView.onDetach() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { view ->
                view.overlays.clear()

                if (routePoints.isNotEmpty()) {
                    val polyline = Polyline().apply {
                        outlinePaint.color = routeColor.toArgb()
                        outlinePaint.strokeWidth = 10f
                        setPoints(routePoints.map { GeoPoint(it.first, it.second) })
                    }
                    view.overlays.add(polyline)
                }

                currentPosition?.let { (lat, lng) ->
                    val marker = Marker(view).apply {
                        position = GeoPoint(lat, lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Tu posición"
                    }
                    view.overlays.add(marker)
                    view.controller.animateTo(GeoPoint(lat, lng))
                }

                startPosition?.let { (lat, lng) ->
                    val circle = org.osmdroid.views.overlay.Polygon().apply {
                        points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(
                            GeoPoint(lat, lng), remainingRadiusMeters
                        )
                        fillPaint.color = routeColor.copy(alpha = 0.12f).toArgb()
                        outlinePaint.color = routeColor.toArgb()
                        outlinePaint.strokeWidth = 3f
                    }
                    view.overlays.add(circle)
                }

                view.invalidate()
            }
        )
    }
}
