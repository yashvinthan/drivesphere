package com.example.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

class BluetoothHubRepository : HubRepository {

    companion object {
        // Standard Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val TARGET_DEVICE_NAME = "DriveSphere-Hub"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _status = MutableStateFlow("Bluetooth Idle")
    private val _connected = MutableStateFlow(false)
    private val _hardwareSos = MutableStateFlow(false)
    private val _telemetry = MutableStateFlow(HubTelemetry())

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun getHubStatus(): Flow<String> = _status.asStateFlow()
    override fun isHardwareConnected(): Flow<Boolean> = _connected.asStateFlow()
    override fun isHardwareSosTriggered(): Flow<Boolean> = _hardwareSos.asStateFlow()
    override fun getHubTelemetry(): Flow<HubTelemetry> = _telemetry.asStateFlow()

    override fun setHubIpAddress(ip: String) {
        // Not used in Bluetooth transport
    }

    override fun getCameraStreamUrl(): String = "http://192.168.4.1:81/stream"

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<Pair<String, String>> {
        return try {
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            bonded.map { it.name ?: "Unknown" to it.address }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(deviceAddress: String? = null): Result<String> = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null) {
            _status.value = "Bluetooth not supported on this phone"
            return@withContext Result.failure(Exception("Bluetooth not supported"))
        }

        if (!bluetoothAdapter.isEnabled) {
            _status.value = "Bluetooth is turned off"
            return@withContext Result.failure(Exception("Bluetooth disabled"))
        }

        try {
            disconnect()

            val targetDevice: BluetoothDevice? = if (!deviceAddress.isNullOrBlank()) {
                bluetoothAdapter.getRemoteDevice(deviceAddress)
            } else {
                bluetoothAdapter.bondedDevices?.firstOrNull { 
                    it.name?.contains(TARGET_DEVICE_NAME, ignoreCase = true) == true 
                }
            }

            if (targetDevice == null) {
                _status.value = "Device 'DriveSphere-Hub' not paired yet"
                return@withContext Result.failure(
                    Exception("Device 'DriveSphere-Hub' not found in paired list. Please pair it in your phone's Bluetooth settings first.")
                )
            }

            _status.value = "Connecting to ${targetDevice.name}..."
            bluetoothAdapter.cancelDiscovery()

            var newSocket: BluetoothSocket? = null
            try {
                // Try standard SPP UUID
                newSocket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                newSocket.connect()
            } catch (firstException: Exception) {
                // Dual-try fallback: use reflection on channel 1 (standard microcontroller SPP)
                try {
                    newSocket?.close()
                } catch (_: Exception) {}

                try {
                    val m = targetDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    newSocket = m.invoke(targetDevice, 1) as BluetoothSocket
                    newSocket.connect()
                } catch (fallbackException: Exception) {
                    throw Exception("Bluetooth connection refused by ESP32 (${fallbackException.localizedMessage ?: firstException.localizedMessage}). Make sure the ESP32 is powered and not in flashing mode.")
                }
            }

            socket = newSocket
            outputStream = newSocket.outputStream
            _connected.value = true
            _status.value = "Bluetooth Linked: ${targetDevice.name}"

            startReaderLoop(newSocket)
            Result.success("Connected to ${targetDevice.name} via Bluetooth SPP")
        } catch (e: SecurityException) {
            _connected.value = false
            _status.value = "Missing Bluetooth Permission"
            Result.failure(Exception("Bluetooth permission denied by system. Please grant 'Nearby devices' permission in Android Settings."))
        } catch (e: Exception) {
            _connected.value = false
            _status.value = "BT Connection Failed: ${e.localizedMessage ?: "Unknown"}"
            Result.failure(e)
        }
    }

    private fun startReaderLoop(activeSocket: BluetoothSocket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(activeSocket.inputStream))
                while (isActive && activeSocket.isConnected) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.startsWith("{")) {
                        try {
                            val json = JSONObject(trimmed)
                            if (json.has("sosTriggered")) {
                                _hardwareSos.value = json.getBoolean("sosTriggered")
                            }

                            val sos = json.optBoolean("sosTriggered", false)
                            val tamper = json.optBoolean("tamperDetected", false)
                            val crash = json.optBoolean("crashDetected", false)
                            val guardArmed = json.optBoolean("guardArmed", false)
                            val spd = json.optInt("speed", 0)
                            val scr = json.optInt("score", 100)
                            val vMode = json.optString("vehicleMode", "BIKE")
                            val gl = json.optString("glyph", "IDLE")

                            // Parse GPS sub-object
                            val gpsObj = json.optJSONObject("gps")
                            val gpsFix = gpsObj?.optBoolean("fix", false) ?: false
                            val gpsLat = if (gpsFix) gpsObj?.optDouble("lat", 0.0) else null
                            val gpsLng = if (gpsFix) gpsObj?.optDouble("lng", 0.0) else null
                            val gpsSpeed = gpsObj?.optDouble("speed", 0.0)?.toFloat() ?: 0f
                            val gpsSats = gpsObj?.optInt("sats", 0) ?: 0
                            val gpsAlt = gpsObj?.optDouble("alt", 0.0)?.toFloat() ?: 0f

                            // Parse IMU sub-object
                            val imuObj = json.optJSONObject("imu")
                            val lean = imuObj?.optDouble("leanAngle", 0.0)?.toFloat() ?: 0f
                            val pitch = imuObj?.optDouble("pitch", 0.0)?.toFloat() ?: 0f
                            val accelG = imuObj?.optDouble("accelG", 1.0)?.toFloat() ?: 1.0f
                            val mpuAvail = imuObj?.optBoolean("available", false) ?: false

                            _telemetry.value = HubTelemetry(
                                isConnected = true,
                                isSosTriggered = sos,
                                isTamperDetected = tamper,
                                isCrashDetected = crash,
                                isGuardArmed = guardArmed,
                                speedKmH = spd,
                                score = scr,
                                vehicleMode = vMode,
                                glyph = gl,
                                gpsFix = gpsFix,
                                gpsLatitude = gpsLat,
                                gpsLongitude = gpsLng,
                                gpsSpeedKmH = gpsSpeed,
                                gpsSats = gpsSats,
                                gpsAltM = gpsAlt,
                                leanAngleDeg = lean,
                                pitchDeg = pitch,
                                accelG = accelG,
                                mpuAvailable = mpuAvail
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                _connected.value = false
                _status.value = "Bluetooth Disconnected"
            }
        }
    }

    override suspend fun pingHardware(): Result<String> = withContext(Dispatchers.IO) {
        if (_connected.value && socket?.isConnected == true) {
            try {
                outputStream?.write("STATUS\n".toByteArray())
                outputStream?.flush()
                Result.success("Bluetooth SPP Connected & Ready")
            } catch (e: Exception) {
                _connected.value = false
                Result.failure(e)
            }
        } else {
            connectToDevice()
        }
    }

    override suspend fun updateOledDisplay(
        text: String,
        mode: String,
        speed: Int,
        score: Int,
        glyph: String,
        vehicleMode: String
    ) = withContext(Dispatchers.IO) {
        try {
            val payload = "SPEED:$speed\nSCORE:$score\nMODE:$vehicleMode\nGLYPH:$glyph\n"
            outputStream?.write(payload.toByteArray())
            outputStream?.flush()
        } catch (_: Exception) {
            _connected.value = false
        }
    }

    override suspend fun resetHardwareSos() = withContext(Dispatchers.IO) {
        try {
            outputStream?.write("RESET_SOS\n".toByteArray())
            outputStream?.flush()
            _hardwareSos.value = false
        } catch (_: Exception) {}
    }

    fun disconnect() {
        readerJob?.cancel()
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        _connected.value = false
        _status.value = "Disconnected"
    }
}
