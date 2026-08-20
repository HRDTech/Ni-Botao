package com.SolucionesHR.nibotao.presentation.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SolucionesHR.nibotao.domain.model.AlertLevel

@Composable
fun AutonomyGauge(
    remainingKm: Float,
    initialAutonomyKm: Float,
    alertLevel: AlertLevel,
    modifier: Modifier = Modifier
) {
    val progress = (remainingKm / initialAutonomyKm).coerceIn(0f, 1f)
    val color = when (alertLevel) {
        AlertLevel.OK -> Color(0xFF2E7D32)
        AlertLevel.WARNING_20 -> Color(0xFFF9A825)
        AlertLevel.CRITICAL_10 -> Color(0xFFC62828)
    }

    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 22f
            drawArc(
                color = Color(0xFFE0E0E0),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${"%.1f".format(remainingKm)} km",
                fontSize = 32.sp,
                textAlign = TextAlign.Center
            )
            Text(text = "restantes", fontSize = 14.sp, color = Color.Gray)
        }
    }
}
