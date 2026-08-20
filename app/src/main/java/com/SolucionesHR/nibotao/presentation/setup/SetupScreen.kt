package com.SolucionesHR.nibotao.presentation.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.SolucionesHR.nibotao.di.ServiceLocator
import com.SolucionesHR.nibotao.domain.repository.BleDeviceInfo
import com.SolucionesHR.nibotao.domain.repository.BleRepository
import com.SolucionesHR.nibotao.domain.usecase.StartTripUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(
    private val startTripUseCase: StartTripUseCase,
    private val bleRepository: BleRepository
) : ViewModel() {

    data class UiState(
        val autonomyKmInput: String = "",
        val initialVoltageInput: String = "",
        val wantsBluetooth: Boolean = false,
        val scannedDevices: List<BleDeviceInfo> = emptyList(),
        val selectedDeviceAddress: String? = null,
        val errorMessage: String? = null,
        val tripStarted: Long? = null // tripId cuando arranca
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun onAutonomyChanged(value: String) {
        _uiState.value = _uiState.value.copy(autonomyKmInput = value, errorMessage = null)
    }

    fun onInitialVoltageChanged(value: String) {
        _uiState.value = _uiState.value.copy(initialVoltageInput = value, errorMessage = null)
    }

    fun onWantsBluetoothChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(wantsBluetooth = value)
        if (value) startScan()
    }

    private fun startScan() = viewModelScope.launch {
        bleRepository.scanBatteryDevices().collect { devices ->
            _uiState.value = _uiState.value.copy(scannedDevices = devices)
        }
    }

    fun onDeviceSelected(address: String) {
        _uiState.value = _uiState.value.copy(selectedDeviceAddress = address)
    }

    fun onStartTripClicked() = viewModelScope.launch {
        val km = _uiState.value.autonomyKmInput.toFloatOrNull()
        if (km == null || km <= 0f) {
            _uiState.value = _uiState.value.copy(errorMessage = "Ingresa un número de km válido, mayor que 0")
            return@launch
        }

        val initialVoltage = _uiState.value.initialVoltageInput.toFloatOrNull()
        if (initialVoltage == null || initialVoltage <= 0f) {
            _uiState.value = _uiState.value.copy(errorMessage = "Ingresa un voltaje inicial válido, mayor que 0")
            return@launch
        }

        val deviceName = _uiState.value.scannedDevices
            .firstOrNull { it.address == _uiState.value.selectedDeviceAddress }?.name

        val tripId = startTripUseCase(km, initialVoltage, deviceName)
        _uiState.value = _uiState.value.copy(tripStarted = tripId)
    }
}

@Composable
fun SetupScreen(
    onTripStarted: (tripId: Long, autonomyKm: Float, initialVoltage: Float, deviceAddress: String?) -> Unit,
    viewModel: SetupViewModel = remember { ServiceLocator.createSetupViewModel() }
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.tripStarted) {
        state.tripStarted?.let { id ->
            onTripStarted(
                id,
                state.autonomyKmInput.toFloatOrNull() ?: 0f,
                state.initialVoltageInput.toFloatOrNull() ?: 0f,
                state.selectedDeviceAddress
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Autonomía", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ingresa los km y el voltaje actual de la batería al iniciar.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.autonomyKmInput,
            onValueChange = viewModel::onAutonomyChanged,
            label = { Text("Km de autonomía") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.errorMessage != null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.initialVoltageInput,
            onValueChange = viewModel::onInitialVoltageChanged,
            label = { Text("Voltaje inicial (V)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.errorMessage != null,
            supportingText = { state.errorMessage?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Conectar dispositivo de batería por Bluetooth (opcional)", modifier = Modifier.weight(1f))
            Switch(checked = state.wantsBluetooth, onCheckedChange = viewModel::onWantsBluetoothChanged)
        }

        if (state.wantsBluetooth) {
            Spacer(Modifier.height(12.dp))
            LazyColumnDevices(
                devices = state.scannedDevices,
                selectedAddress = state.selectedDeviceAddress,
                onSelect = viewModel::onDeviceSelected
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = viewModel::onStartTripClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar recorrido")
        }
    }
}

@Composable
private fun LazyColumnDevices(
    devices: List<BleDeviceInfo>,
    selectedAddress: String?,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (devices.isEmpty()) {
            Text("Buscando dispositivos cercanos…", style = MaterialTheme.typography.bodySmall)
        }
        devices.forEach { device ->
            ListItem(
                headlineContent = { Text(device.name ?: "Dispositivo desconocido") },
                supportingContent = { Text(device.address) },
                trailingContent = {
                    RadioButton(
                        selected = device.address == selectedAddress,
                        onClick = { onSelect(device.address) }
                    )
                }
            )
        }
    }
}
