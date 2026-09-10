package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Esp32HubRepository
import com.example.data.HubRepository
import com.example.data.LiveTripRepository
import com.example.data.TripRepository
import com.example.data.TripSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.Job

enum class VehicleType {
    TWO_WHEELER, FOUR_WHEELER
}

enum class TripEvent {
    NONE, OVERSPEED, DISTRACTION, HELMET_RISK, FALL, SEATBELT_RISK, CRASH_IMPACT
}

enum class HardwareTransport {
    WIFI, BLUETOOTH
}

data class AppState(
    val summary: TripSummary = TripSummary(),
    val hubStatus: String = "Connecting to Guardian Hub...",
    val isEsp32Connected: Boolean = false,
    val hardwareTransport: HardwareTransport = HardwareTransport.WIFI,
    val bluetoothDeviceName: String = "DriveSphere-Hub",
    val esp32IpAddress: String = "192.168.4.1",
    // NEO-M8N GPS Telemetry
    val hubGpsLatitude: Double? = null,
    val hubGpsLongitude: Double? = null,
    val hubGpsSpeedKmH: Float = 0f,
    val hubGpsSats: Int = 0,
    val hubGpsAltM: Float = 0f,
    val hubGpsFix: Boolean = false,
    // MPU-6050 IMU Telemetry
    val hubLeanAngleDeg: Float = 0f,
    val hubPitchDeg: Float = 0f,
    val hubAccelG: Float = 1.0f,
    val isMpuAvailable: Boolean = false,
    val activeEvent: TripEvent = TripEvent.NONE,
    val isTripActive: Boolean = false,
    val activeTripDistanceKm: Double = 0.0,
    val activeTripDurationSeconds: Long = 0L,
    val activeTripSpeedKmH: Float = 0f,
    val lastTripDistanceKm: Double = 0.0,
    val lastTripDurationMins: Int = 0,
    val lastTripScore: Int = 100,
    val userName: String = "Arjun",
    val vehicleType: VehicleType = VehicleType.TWO_WHEELER,
    val vehicleRegNumber: String = "DL-01-AB-1234",
    val isVehicleGuardArmed: Boolean = false,
    val isTamperAlertActive: Boolean = false,
    val parkedLocation: String = "Campus Main Parking (13.0827° N, 80.2707° E)",
    val isDarkMode: Boolean? = null,
    val activeHazard: String? = "Heavy rain expected in 2 km. Reduce speed.",
    val idleGlyph: String = "IDLE_FACE",
    val isCharging: Boolean = false,
    val hapticSync: Boolean = true,
    val driveCoins: Int = 1250,
    val isNightGlowMode: Boolean = false,
    val hapticMappings: Map<String, String> = mapOf(
        "Navigation Alert" to "Short Pulse",
        "Hazard Detected" to "Double Pulse",
        "Battery Low" to "Continuous"
    )
)

class MainViewModel(
    private val wifiHubRepository: Esp32HubRepository = Esp32HubRepository(),
    private val bluetoothHubRepository: com.example.data.BluetoothHubRepository = com.example.data.BluetoothHubRepository(),
    private val tripRepository: TripRepository = LiveTripRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private var activeHubRepository: HubRepository = wifiHubRepository
    private var hubStatusJob: Job? = null
    private var hubConnectedJob: Job? = null
    private var hubSosJob: Job? = null
    private var hubTelemetryJob: Job? = null

    init {
        // Collect Live Trip Summary & Scoring
        viewModelScope.launch {
            tripRepository.getSummary().collect { summary ->
                _uiState.update { it.copy(summary = summary) }
            }
        }
        bindHubListeners(activeHubRepository)
    }

    private fun bindHubListeners(repo: HubRepository) {
        hubStatusJob?.cancel()
        hubConnectedJob?.cancel()
        hubSosJob?.cancel()
        hubTelemetryJob?.cancel()

        hubStatusJob = viewModelScope.launch {
            repo.getHubStatus().collect { status ->
                _uiState.update { it.copy(hubStatus = status) }
            }
        }
        hubConnectedJob = viewModelScope.launch {
            repo.isHardwareConnected().collect { connected ->
                _uiState.update { it.copy(isEsp32Connected = connected) }
            }
        }
        hubSosJob = viewModelScope.launch {
            repo.isHardwareSosTriggered().collect { hardwareSos ->
                if (hardwareSos) {
                    val emergencyEvent = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) {
                        TripEvent.FALL
                    } else {
                        TripEvent.CRASH_IMPACT
                    }
                    _uiState.update { it.copy(activeEvent = emergencyEvent) }
                }
            }
        }
        hubTelemetryJob = viewModelScope.launch {
            repo.getHubTelemetry().collect { telemetry ->
                _uiState.update { current ->
                    current.copy(
                        hubGpsFix = telemetry.gpsFix,
                        hubGpsLatitude = telemetry.gpsLatitude,
                        hubGpsLongitude = telemetry.gpsLongitude,
                        hubGpsSpeedKmH = telemetry.gpsSpeedKmH,
                        hubGpsSats = telemetry.gpsSats,
                        hubGpsAltM = telemetry.gpsAltM,
                        hubLeanAngleDeg = telemetry.leanAngleDeg,
                        hubPitchDeg = telemetry.pitchDeg,
                        hubAccelG = telemetry.accelG,
                        isMpuAvailable = telemetry.mpuAvailable
                    )
                }

                // Automatic Tamper Alert if armed and physical motion sensed
                if (telemetry.isTamperDetected && _uiState.value.isVehicleGuardArmed) {
                    triggerTamperAlert()
                }

                // Automatic Fall / Crash Alert if hardware detected impact
                if (telemetry.isCrashDetected || telemetry.isSosTriggered) {
                    val emergencyEvent = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) {
                        TripEvent.FALL
                    } else {
                        TripEvent.CRASH_IMPACT
                    }
                    _uiState.update { it.copy(activeEvent = emergencyEvent) }
                }

                // Real-time speed sync if GPS fix is active and trip is active
                if (_uiState.value.isTripActive && telemetry.gpsFix && telemetry.gpsSpeedKmH > 1.0f) {
                    _uiState.update { it.copy(activeTripSpeedKmH = telemetry.gpsSpeedKmH) }
                }
            }
        }
    }

    fun setHardwareTransport(transport: HardwareTransport) {
        _uiState.update { it.copy(hardwareTransport = transport) }
        activeHubRepository = if (transport == HardwareTransport.WIFI) wifiHubRepository else bluetoothHubRepository
        bindHubListeners(activeHubRepository)
    }

    fun getPairedBluetoothDevices(): List<Pair<String, String>> {
        return bluetoothHubRepository.getPairedDevices()
    }

    fun connectBluetoothDevice(address: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = bluetoothHubRepository.connectToDevice(address)
            if (res.isSuccess) {
                _uiState.update { it.copy(hardwareTransport = HardwareTransport.BLUETOOTH) }
                activeHubRepository = bluetoothHubRepository
                bindHubListeners(bluetoothHubRepository)
                onResult(true, res.getOrDefault("Connected via Bluetooth"))
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to connect via Bluetooth")
            }
        }
    }

    fun setEsp32IpAddress(ip: String) {
        _uiState.update { it.copy(esp32IpAddress = ip) }
        wifiHubRepository.setHubIpAddress(ip)
    }
    fun setEsp32Ip(ip: String) = setEsp32IpAddress(ip)

    fun pingEsp32Hardware(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = activeHubRepository.pingHardware()
            if (result.isSuccess) {
                onResult(true, result.getOrDefault("Hardware Online & Verified!"))
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Connection Timed Out")
            }
        }
    }

    fun setVehicleType(type: VehicleType) {
        _uiState.update { it.copy(vehicleType = type) }
        viewModelScope.launch {
            val modeStr = if (type == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            activeHubRepository.updateOledDisplay(
                text = "MODE SWITCHED",
                mode = modeStr,
                speed = 0,
                score = _uiState.value.summary.score,
                glyph = _uiState.value.idleGlyph,
                vehicleMode = modeStr
            )
        }
    }

    fun toggleVehicleGuard() {
        _uiState.update {
            val newState = !it.isVehicleGuardArmed
            it.copy(isVehicleGuardArmed = newState, isTamperAlertActive = false)
        }
        viewModelScope.launch {
            val statusStr = if (_uiState.value.isVehicleGuardArmed) "GUARD ARMED" else "GUARD DISARMED"
            activeHubRepository.updateOledDisplay(
                text = statusStr,
                mode = "Security",
                speed = 0,
                score = _uiState.value.summary.score,
                glyph = "SHIELD",
                vehicleMode = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            )
        }
    }

    fun triggerTamperAlert() {
        if (_uiState.value.isVehicleGuardArmed) {
            _uiState.update { it.copy(isTamperAlertActive = true) }
            viewModelScope.launch {
                activeHubRepository.updateOledDisplay(
                    text = "! TAMPER ALERT !",
                    mode = "Vibration",
                    speed = 0,
                    score = _uiState.value.summary.score,
                    glyph = "ALERT",
                    vehicleMode = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
                )
            }
        }
    }

    fun dismissTamperAlert() {
        _uiState.update { it.copy(isTamperAlertActive = false) }
    }

    fun startTrip() {
        _uiState.update {
            it.copy(
                isTripActive = true,
                activeEvent = TripEvent.NONE,
                activeTripDistanceKm = 0.0,
                activeTripDurationSeconds = 0L,
                activeTripSpeedKmH = 0f
            )
        }
        viewModelScope.launch {
            val modeStr = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            activeHubRepository.updateOledDisplay(
                text = "TRIP ACTIVE",
                mode = "Navigation",
                speed = 0,
                score = _uiState.value.summary.score,
                glyph = "NAV",
                vehicleMode = modeStr
            )
        }
    }

    fun updateLiveTripMetrics(distanceDeltaKm: Double, speedKmH: Float) {
        if (!_uiState.value.isTripActive) return
        _uiState.update { current ->
            val totalDistance = current.activeTripDistanceKm + distanceDeltaKm
            current.copy(
                activeTripDistanceKm = totalDistance,
                activeTripSpeedKmH = speedKmH
            )
        }
        viewModelScope.launch {
            tripRepository.addSafeDistance(distanceDeltaKm)
        }
    }

    fun incrementTripDuration() {
        if (!_uiState.value.isTripActive) return
        _uiState.update { it.copy(activeTripDurationSeconds = it.activeTripDurationSeconds + 1) }
    }

    fun endTrip(onFinished: (distanceKm: Double, durationMins: Int, finalScore: Int) -> Unit) {
        val distance = _uiState.value.activeTripDistanceKm
        val durationMins = (_uiState.value.activeTripDurationSeconds / 60).toInt().coerceAtLeast(1)
        val finalScore = _uiState.value.summary.score

        _uiState.update {
            it.copy(
                isTripActive = false,
                activeEvent = TripEvent.NONE,
                lastTripDistanceKm = distance,
                lastTripDurationMins = durationMins,
                lastTripScore = finalScore
            )
        }

        viewModelScope.launch {
            val modeStr = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            hubRepository.updateOledDisplay(
                text = "TRIP COMPLETED",
                mode = "Score: $finalScore",
                speed = 0,
                score = finalScore,
                glyph = _uiState.value.idleGlyph,
                vehicleMode = modeStr
            )
        }

        onFinished(distance, durationMins, finalScore)
    }

    fun triggerEvent(event: TripEvent) {
        _uiState.update { it.copy(activeEvent = event) }
        viewModelScope.launch {
            val vehicleStr = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            when (event) {
                TripEvent.OVERSPEED -> {
                    tripRepository.recordViolation(8, "Speed limit exceeded")
                    activeHubRepository.updateOledDisplay("OVERSPEED!", "Slow Down", 68, _uiState.value.summary.score - 8, "SPEED", vehicleStr)
                }
                TripEvent.DISTRACTION -> {
                    tripRepository.recordViolation(4, "Mobile device usage")
                    activeHubRepository.updateOledDisplay("DISTRACTION", "Focus Road", 35, _uiState.value.summary.score - 4, "ALERT", vehicleStr)
                }
                TripEvent.HELMET_RISK -> {
                    tripRepository.recordViolation(17, "No safety helmet detected")
                    activeHubRepository.updateOledDisplay("NO HELMET!", "Fasten Strap", 20, _uiState.value.summary.score - 17, "HELMET", vehicleStr)
                }
                TripEvent.SEATBELT_RISK -> {
                    tripRepository.recordViolation(14, "Seatbelt unbuckled")
                    activeHubRepository.updateOledDisplay("NO SEATBELT!", "Buckle Up", 30, _uiState.value.summary.score - 14, "BELT", vehicleStr)
                }
                TripEvent.FALL -> {
                    tripRepository.recordViolation(15, "Two-wheeler fall")
                    activeHubRepository.updateOledDisplay("FALL DETECTED", "S.O.S Active", 0, _uiState.value.summary.score - 15, "SOS", vehicleStr)
                }
                TripEvent.CRASH_IMPACT -> {
                    tripRepository.recordViolation(32, "High-G collision impact")
                    activeHubRepository.updateOledDisplay("CRASH DETECTED", "High-G Impact", 0, _uiState.value.summary.score - 32, "SOS", vehicleStr)
                }
                TripEvent.NONE -> {
                    activeHubRepository.updateOledDisplay("SAFE RIDE", "Normal Mode", 25, _uiState.value.summary.score, "SAFE", vehicleStr)
                }
            }
        }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkMode = if (it.isDarkMode == null) true else if (it.isDarkMode) false else null) }
    }
    
    fun setIdleGlyph(glyph: String) {
        _uiState.update { it.copy(idleGlyph = glyph) }
        viewModelScope.launch {
            val vehicleStr = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            activeHubRepository.updateOledDisplay("GLYPH UPDATED", glyph, 0, _uiState.value.summary.score, glyph, vehicleStr)
        }
    }
    
    fun toggleCharging() {
        _uiState.update { it.copy(isCharging = !it.isCharging) }
    }
    
    fun setHapticSync(enabled: Boolean) {
        _uiState.update { it.copy(hapticSync = enabled) }
    }
    
    fun setNightGlowMode(enabled: Boolean) {
        _uiState.update { it.copy(isNightGlowMode = enabled) }
    }
    
    fun updateHapticMapping(event: String, pattern: String) {
        _uiState.update { 
            val newMappings = it.hapticMappings.toMutableMap()
            newMappings[event] = pattern
            it.copy(hapticMappings = newMappings)
        }
    }

    fun deductCoins(amount: Int): Boolean {
        if (_uiState.value.driveCoins >= amount) {
            _uiState.update { it.copy(driveCoins = it.driveCoins - amount) }
            return true
        }
        return false
    }

    fun addCoins(amount: Int) {
        _uiState.update { it.copy(driveCoins = it.driveCoins + amount) }
    }

    fun resolveEvent() {
        _uiState.update { it.copy(activeEvent = TripEvent.NONE) }
        viewModelScope.launch {
            activeHubRepository.resetHardwareSos()
            val vehicleStr = if (_uiState.value.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR"
            activeHubRepository.updateOledDisplay("SAFE RIDE", "All Clear", 20, _uiState.value.summary.score, "SAFE", vehicleStr)
        }
    }
}
