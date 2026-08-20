package com.SolucionesHR.nibotao.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SolucionesHR.nibotao.di.ServiceLocator
import com.SolucionesHR.nibotao.domain.model.AlertLevel
import com.SolucionesHR.nibotao.domain.model.Trip
import com.SolucionesHR.nibotao.domain.model.TripStatus
import com.SolucionesHR.nibotao.domain.repository.TripRepository
import com.SolucionesHR.nibotao.domain.usecase.CanReturnToStartUseCase
import com.SolucionesHR.nibotao.domain.usecase.SetTripStatusUseCase
import com.SolucionesHR.nibotao.domain.usecase.StopTripUseCase
import com.SolucionesHR.nibotao.presentation.dashboard.components.AutonomyGauge
import com.SolucionesHR.nibotao.presentation.dashboard.components.TripMap
import com.SolucionesHR.nibotao.service.TripLiveState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.SolucionesHR.nibotao.service.TripForegroundService
import kotlin.math.max

data class DashboardUiState(
    val trip: Trip? = null,
    val liveState: TripLiveState = TripLiveState(),
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    val startPosition: Pair<Double, Double>? = null,
    val isFinishingTrip: Boolean = false
)

class DashboardViewModel(
    tripRepository: TripRepository,
    private val stopTripUseCase: StopTripUseCase,
    private val setTripStatusUseCase: SetTripStatusUseCase,
    private val canReturnToStartUseCase: CanReturnToStartUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val repository = tripRepository

    private var tripJob: Job? = null
    private var routeJob: Job? = null
    private var observedTripId: Long? = null

    private var startLat: Double? = null
    private var startLng: Double? = null

    fun observeTrip(tripId: Long) {
        if (observedTripId == tripId) return
        observedTripId = tripId
        tripJob?.cancel()
        routeJob?.cancel()

        tripJob = viewModelScope.launch {
            repository.observeTrip(tripId).collect { trip ->
                _uiState.update { it.copy(trip = trip) }
            }
        }

        routeJob = viewModelScope.launch {
            repository.observeRoute(tripId).collect { points ->
                val route = points.map { it.lat to it.lng }
                if (startLat == null && points.isNotEmpty()) {
                    startLat = points.first().lat
                    startLng = points.first().lng
                }
                _uiState.update {
                    it.copy(
                        routePoints = route,
                        startPosition = startLat?.let { lat -> startLng?.let { lng -> lat to lng } }
                    )
                }
            }
        }
    }

    fun updateLiveState(newState: TripLiveState) {
        if (startLat == null) {
            startLat = newState.currentLat
            startLng = newState.currentLng
        }
        _uiState.update {
            it.copy(
                liveState = newState,
                startPosition = startLat?.let { lat -> startLng?.let { lng -> lat to lng } }
            )
        }
    }

    fun canReturnToStart(remainingKm: Float): CanReturnToStartUseCase.Result? {
        val lat = _uiState.value.liveState.currentLat ?: return null
        val lng = _uiState.value.liveState.currentLng ?: return null
        val sLat = startLat ?: return null
        val sLng = startLng ?: return null
        return canReturnToStartUseCase(lat, lng, sLat, sLng, remainingKm)
    }

    fun pauseResume() = viewModelScope.launch {
        val trip = _uiState.value.trip ?: return@launch
        val newStatus = if (trip.status == TripStatus.ACTIVE) TripStatus.PAUSED else TripStatus.ACTIVE
        setTripStatusUseCase(trip, newStatus)
    }

    fun parkTrip(onDone: () -> Unit) = viewModelScope.launch {
        val trip = _uiState.value.trip ?: return@launch
        if (trip.status == TripStatus.ACTIVE) {
            setTripStatusUseCase(trip, TripStatus.PAUSED)
        }
        onDone()
    }

    fun finishTrip(estimatedErrorKm: Float?, finalBatteryVoltage: Float, onFinished: () -> Unit) = viewModelScope.launch {
        val trip = _uiState.value.trip ?: return@launch
        _uiState.update { it.copy(isFinishingTrip = true) }
        val distance = max(_uiState.value.liveState.distanceTraveledKm, trip.distanceTraveledKm)
        val batteryUsed = calculateBatteryUsedPercent(_uiState.value.liveState)
        stopTripUseCase(trip.id, estimatedErrorKm, distance, batteryUsed, finalBatteryVoltage)
        onFinished()
    }

    private fun calculateBatteryUsedPercent(liveState: TripLiveState): Float? {
        val initial = liveState.initialBatteryPercent ?: return _uiState.value.trip?.batteryUsedPercent
        val current = liveState.batteryPercent ?: return _uiState.value.trip?.batteryUsedPercent
        return (initial - current).coerceAtLeast(0).toFloat()
    }
}

@Composable
fun DashboardRoute(
    tripId: Long,
    onStopService: () -> Unit,
    onOpenHistory: () -> Unit,
    onParkAndCloseApp: () -> Unit,
    viewModel: DashboardViewModel = remember { ServiceLocator.createDashboardViewModel() }
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tripId) {
        viewModel.observeTrip(tripId)
    }

    LaunchedEffect(Unit) {
        TripForegroundService.sharedState.collect { serviceState ->
            viewModel.updateLiveState(serviceState)
        }
    }

    val trip = state.trip
    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    DashboardScreen(
        trip = trip,
        liveState = state.liveState,
        routePoints = state.routePoints,
        startPosition = state.startPosition,
        isFinishingTrip = state.isFinishingTrip,
        onPauseResume = viewModel::pauseResume,
        onOpenHistory = onOpenHistory,
        onParkAndCloseApp = {
            viewModel.parkTrip {
                onStopService()
                onParkAndCloseApp()
            }
        }
    )
}

@Composable
fun DashboardScreen(
    trip: Trip,
    liveState: TripLiveState,
    routePoints: List<Pair<Double, Double>>,
    startPosition: Pair<Double, Double>?,
    isFinishingTrip: Boolean,
    onPauseResume: () -> Unit,
    onOpenHistory: () -> Unit,
    onParkAndCloseApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showParkConfirmDialog by remember { mutableStateOf(false) }
    val displayedDistanceKm = max(liveState.distanceTraveledKm, trip.distanceTraveledKm)

    if (showParkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showParkConfirmDialog = false },
            title = { Text("Parquear y cerrar") },
            text = { Text("Se guardarán los datos y se cerrará la aplicación. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showParkConfirmDialog = false
                    onParkAndCloseApp()
                }) {
                    Text("Sí, continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showParkConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Mapa a pantalla completa
        TripMap(
            routePoints = routePoints,
            currentPosition = liveState.currentLat?.let { lat -> liveState.currentLng?.let { lng -> lat to lng } },
            startPosition = startPosition,
            remainingRadiusMeters = liveState.remainingKm * 1000.0,
            alertLevel = liveState.alertLevel,
            modifier = Modifier.fillMaxSize()
        )

        // Panel transparente flotante en la parte inferior
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (liveState.alertLevel != AlertLevel.OK) {
                    AlertBanner(liveState.alertLevel)
                    Spacer(Modifier.height(8.dp))
                }

                AutonomyGauge(
                    remainingKm = liveState.remainingKm,
                    initialAutonomyKm = trip.initialAutonomyKm,
                    alertLevel = liveState.alertLevel
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem("Velocidad", "${"%.0f".format(liveState.speedKmh)} km/h")
                    MetricItem("Recorrido", "${"%.1f".format(displayedDistanceKm)} km")
                    BatteryMetricItem(liveState)
                }

                Spacer(Modifier.height(8.dp))
                LiveConsumptionStats(trip = trip, liveState = liveState, distanceKm = displayedDistanceKm)

                Spacer(Modifier.height(16.dp))

                // Grupo de acciones principal con jerarquía visual más clara.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onPauseResume,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (trip.status == TripStatus.ACTIVE) "Pausar" else "Reanudar")
                        }
                        Button(
                            onClick = onOpenHistory,
                            enabled = !isFinishingTrip,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Historial")
                        }
                    }

                    OutlinedButton(
                        onClick = { showParkConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Parquear y cerrar")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(level: AlertLevel) {
    val (text, color) = when (level) {
        AlertLevel.WARNING_20 -> "Te queda poca autonomía (20 %)" to MaterialTheme.colorScheme.tertiaryContainer
        AlertLevel.CRITICAL_10 -> "Autonomía crítica (10 %) - busca dónde cargar" to MaterialTheme.colorScheme.errorContainer
        else -> "" to MaterialTheme.colorScheme.surface
    }
    if (text.isNotEmpty()) {
        Surface(color = color, modifier = Modifier.fillMaxWidth()) {
            Text(text = text, modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BatteryMetricItem(liveState: TripLiveState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (liveState.bluetoothConnected) "BT activo" else "BT inactivo",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = liveState.batteryPercent?.let { "$it%" } ?: "Sin BT",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LiveConsumptionStats(trip: Trip, liveState: TripLiveState, distanceKm: Float) {
    val initialPercent = liveState.initialBatteryPercent
    val currentPercent = liveState.batteryPercent
    val consumedPercent = if (initialPercent != null && currentPercent != null) {
        (initialPercent - currentPercent).coerceAtLeast(0)
    } else {
        null
    }
    val kmPerPercent = consumedPercent?.takeIf { it > 0 }?.let { distanceKm / it }

    val initialVoltage = trip.initialBatteryVoltage
    val estimatedCurrentVoltage = if (initialVoltage != null && currentPercent != null) {
        initialVoltage * (currentPercent / 100f)
    } else {
        null
    }
    val estimatedConsumedVoltage = if (initialVoltage != null && consumedPercent != null) {
        initialVoltage * (consumedPercent / 100f)
    } else {
        null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Consumo en vivo", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            consumedPercent?.let { "Batería consumida: $it%" } ?: "Batería consumida: sin datos BT",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            kmPerPercent?.let { "Rendimiento actual: ${"%.2f".format(it)} km/%" } ?: "Rendimiento actual: sin datos",
            style = MaterialTheme.typography.bodySmall
        )
        if (initialVoltage != null) {
            Text("Voltaje inicial: ${"%.2f".format(initialVoltage)} V", style = MaterialTheme.typography.bodySmall)
            Text(
                estimatedCurrentVoltage?.let { "Voltaje estimado actual: ${"%.2f".format(it)} V" }
                    ?: "Voltaje estimado actual: sin datos",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                estimatedConsumedVoltage?.let { "Consumo estimado: ${"%.2f".format(it)} V" }
                    ?: "Consumo estimado: sin datos",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

