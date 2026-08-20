package com.SolucionesHR.nibotao.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.SolucionesHR.nibotao.domain.model.BluetoothBatteryState
import com.SolucionesHR.nibotao.domain.repository.BleDeviceInfo
import com.SolucionesHR.nibotao.domain.repository.BleRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Interfaz de abstracción para cualquier fuente de datos de batería por Bluetooth.
 * Permite en el futuro agregar parsers específicos por marca de controlador de
 * e-bike/e-scooter sin tocar el resto de la app (Open/Closed Principle).
 */
interface BatteryDataSource {
    fun parseBatteryPercent(characteristicValue: ByteArray): Int?
    val serviceUuid: UUID
    val characteristicUuid: UUID
}

/**
 * Implementación del perfil GATT ESTÁNDAR "Battery Service" (0x180F) /
 * "Battery Level" (0x2A19). La mayoría de wearables y algunos BMS lo exponen.
 */
class StandardGattBatteryDataSource : BatteryDataSource {
    override val serviceUuid: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    override val characteristicUuid: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    override fun parseBatteryPercent(characteristicValue: ByteArray): Int? {
        if (characteristicValue.isEmpty()) return null
        return characteristicValue[0].toInt() and 0xFF // 0-100 según spec BLE
    }
}

class BleRepositoryImpl(
    private val context: Context,
    private val batteryDataSource: BatteryDataSource = StandardGattBatteryDataSource()
) : BleRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override fun scanBatteryDevices(): Flow<List<BleDeviceInfo>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth no disponible en este dispositivo"))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, BleDeviceInfo>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                found[device.address] = BleDeviceInfo(
                    name = device.name,
                    address = device.address,
                    rssi = result.rssi
                )
                trySend(found.values.toList())
            }
        }

        scanner.startScan(callback)
        awaitClose { scanner.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    override fun connect(deviceAddress: String): Flow<BluetoothBatteryState> = callbackFlow {
        val device = adapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            close(IllegalArgumentException("Dispositivo no encontrado: $deviceAddress"))
            return@callbackFlow
        }

        trySend(BluetoothBatteryState(deviceName = device.name, connected = false))

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        trySend(BluetoothBatteryState(deviceName = device.name, connected = true))
                        g.discoverServices()
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        trySend(BluetoothBatteryState(deviceName = device.name, connected = false))
                        // Reconexión automática best-effort
                        g.connect()
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(batteryDataSource.serviceUuid)
                    ?.getCharacteristic(batteryDataSource.characteristicUuid) ?: return
                g.setCharacteristicNotification(characteristic, true)
                g.readCharacteristic(characteristic)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val percent = batteryDataSource.parseBatteryPercent(value) ?: return
                trySend(BluetoothBatteryState(deviceName = device.name, connected = true, batteryPercent = percent))
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                val percent = batteryDataSource.parseBatteryPercent(value) ?: return
                trySend(BluetoothBatteryState(deviceName = device.name, connected = true, batteryPercent = percent))
            }
        }

        gatt = device.connectGatt(context, true, callback)

        awaitClose {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
