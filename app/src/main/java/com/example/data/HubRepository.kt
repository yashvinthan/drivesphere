package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay

data class HubTelemetry(
    val isConnected: Boolean = false,
    val isSosTriggered: Boolean = false,
    val isTamperDetected: Boolean = false,
    val isCrashDetected: Boolean = false,
    val isGuardArmed: Boolean = false,
    val speedKmH: Int = 0,
    val score: Int = 100,
    val vehicleMode: String = "BIKE",
    val glyph: String = "IDLE",
    // NEO-M8N GPS
    val gpsFix: Boolean = false,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsSpeedKmH: Float = 0f,
    val gpsSats: Int = 0,
    val gpsAltM: Float = 0f,
    // MPU-6050 IMU
    val leanAngleDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val accelG: Float = 1.0f,
    val mpuAvailable: Boolean = false
)

interface HubRepository {
    fun getHubStatus(): Flow<String>
    fun isHardwareConnected(): Flow<Boolean>
    fun isHardwareSosTriggered(): Flow<Boolean>
    fun getHubTelemetry(): Flow<HubTelemetry> = flowOf(HubTelemetry())
    suspend fun updateOledDisplay(
        text: String, 
        mode: String, 
        speed: Int = 0, 
        score: Int = 100, 
        glyph: String = "IDLE",
        vehicleMode: String = "BIKE"
    )
    suspend fun resetHardwareSos()
    fun setHubIpAddress(ip: String)
    suspend fun pingHardware(): Result<String>
    fun getCameraStreamUrl(): String
}

class StandaloneHubRepository : HubRepository {
    private val _status = MutableStateFlow("Standalone Mode (Phone Sensors Active)")
    private val _connected = MutableStateFlow(false)
    private val _sos = MutableStateFlow(false)
    private val _telemetry = MutableStateFlow(HubTelemetry())

    override fun getHubStatus(): Flow<String> = _status.asStateFlow()
    override fun isHardwareConnected(): Flow<Boolean> = _connected.asStateFlow()
    override fun isHardwareSosTriggered(): Flow<Boolean> = _sos.asStateFlow()
    override fun getHubTelemetry(): Flow<HubTelemetry> = _telemetry.asStateFlow()
    
    override suspend fun updateOledDisplay(
        text: String, 
        mode: String, 
        speed: Int, 
        score: Int, 
        glyph: String,
        vehicleMode: String
    ) {
        // Standalone smartphone mode - no physical I2C display connected
    }

    override suspend fun resetHardwareSos() {
        _sos.value = false
    }

    override fun setHubIpAddress(ip: String) {
        // Standalone mode maintains internal sensors
    }

    override suspend fun pingHardware(): Result<String> {
        return Result.failure(Exception("No external ESP32 configured. Operating via smartphone internal gyroscope & GPS."))
    }
    
    override fun getCameraStreamUrl(): String = "http://192.168.4.1:81/stream"
}

