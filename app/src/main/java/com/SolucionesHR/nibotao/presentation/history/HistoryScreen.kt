package com.SolucionesHR.nibotao.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SolucionesHR.nibotao.di.ServiceLocator
import com.SolucionesHR.nibotao.domain.model.Trip
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(
    tripRepository: TripRepository
) : ViewModel() {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips = _trips.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeHistory().collect { _trips.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    viewModel: HistoryViewModel = remember { ServiceLocator.createHistoryViewModel() }
) {
    val trips by viewModel.trips.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val totalKm = remember(trips) { trips.sumOf { it.distanceTraveledKm.toDouble() }.toFloat() }
    val averageKm = remember(trips, totalKm) { if (trips.isNotEmpty()) totalKm / trips.size else 0f }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<-", style = MaterialTheme.typography.titleMedium)
                    }
                },
                title = { Text("Historial") }
            )
        }
    ) { innerPadding ->
        if (trips.isEmpty()) {
            EmptyHistoryState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HistorySummaryCard(totalTrips = trips.size, totalKm = totalKm, averageKm = averageKm)
            }
            items(trips, key = { it.id }) { trip ->
                HistoryTripCard(trip = trip, dateFormat = dateFormat)
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sin recorridos todavía", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Cuando finalices viajes aparecerán aquí con sus métricas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(totalTrips: Int, totalKm: Float, averageKm: Float) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resumen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    label = "Recorridos",
                    value = totalTrips.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Km totales",
                    value = "%.1f".format(totalKm),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "Promedio",
                    value = "%.1f km".format(averageKm),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryTripCard(trip: Trip, dateFormat: SimpleDateFormat) {
    val consumedVoltage = if (trip.initialBatteryVoltage != null && trip.finalBatteryVoltage != null) {
        (trip.initialBatteryVoltage - trip.finalBatteryVoltage).coerceAtLeast(0f)
    } else null
    val kmPerVoltage = consumedVoltage?.takeIf { it > 0f }?.let { trip.distanceTraveledKm / it }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = dateFormat.format(Date(trip.startTimestamp)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(onClick = {}, label = { Text("Finalizado") })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric("Autonomía", "${"%.1f".format(trip.initialAutonomyKm)} km", modifier = Modifier.weight(1f))
                SummaryMetric("Recorrido", "${"%.1f".format(trip.distanceTraveledKm)} km", modifier = Modifier.weight(1f))
            }

            trip.batteryUsedPercent?.let {
                Text("Batería consumida: ${"%.0f".format(it)}%", style = MaterialTheme.typography.bodyMedium)
            }
            if (trip.initialBatteryVoltage != null || trip.finalBatteryVoltage != null) {
                Text(
                    "Voltaje: ${trip.initialBatteryVoltage?.let { "${"%.2f".format(it)} V" } ?: "-"} -> ${trip.finalBatteryVoltage?.let { "${"%.2f".format(it)} V" } ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            consumedVoltage?.let {
                Text("Consumo por voltaje: ${"%.2f".format(it)} V", style = MaterialTheme.typography.bodyMedium)
            }
            kmPerVoltage?.let {
                Text("Rendimiento: ${"%.2f".format(it)} km/V", style = MaterialTheme.typography.bodyMedium)
            }
            trip.estimatedErrorKm?.let {
                Text(
                    "Error de estimación: ${"%.1f".format(it)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
