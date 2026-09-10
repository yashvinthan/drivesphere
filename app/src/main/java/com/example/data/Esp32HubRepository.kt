package com.example.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Esp32HubRepository(
    private var baseIp: String = "192.168.4.1"
) : HubRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _status = MutableStateFlow("Connecting to Guardian Hub...")
    private val _connected = MutableStateFlow(false)
    private val _hardwareSos = MutableStateFlow(false)

    private val _telemetry = MutableStateFlow(HubTelemetry())

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    init {
        startHardwarePolling()
    }

    override fun getHubStatus(): Flow<String> = _status.asStateFlow()
    override fun isHardwareConnected(): Flow<Boolean> = _connected.asStateFlow()
    override fun isHardwareSosTriggered(): Flow<Boolean> = _hardwareSos.asStateFlow()
    override fun getHubTelemetry(): Flow<HubTelemetry> = _telemetry.asStateFlow()

    override fun setHubIpAddress(ip: String) {
        baseIp = ip.trim()
        startHardwarePolling()
    }

    override suspend fun pingHardware(): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("http://$baseIp/status")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            if (response.isSuccessful) {
                _connected.value = true
                _status.value = "Hardware Linked (${latency}ms)"
                response.close()
                Result.success("ESP32 Online! Latency: ${latency}ms")
            } else {
                _connected.value = false
                _status.value = "ESP32 Error (HTTP ${response.code})"
                response.close()
                Result.failure(Exception("ESP32 returned HTTP error code ${response.code}"))
            }
        } catch (e: Exception) {
            _connected.value = false
            _status.value = "Standalone (Phone Sensors Active)"
            Result.failure(Exception("Cannot reach ESP32 at $baseIp. Verify phone is connected to 'DriveSphere-Hub' Wi-Fi."))
        }
    }

    private fun startHardwarePolling() {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("http://$baseIp/status")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            _connected.value = true
                            _status.value = "ESP32 Guardian Hub (Online)"
                            
                            val sos = json.optBoolean("sosTriggered", false)
                            val tamper = json.optBoolean("tamperDetected", false)
                            val crash = json.optBoolean("crashDetected", false)
                            val guardArmed = json.optBoolean("guardArmed", false)
                            val spd = json.optInt("speed", 0)
                            val scr = json.optInt("score", 100)
                            val vMode = json.optString("vehicleMode", "BIKE")
                            val gl = json.optString("glyph", "IDLE")

                            if (sos) {
                                _hardwareSos.value = true
                            }

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
                        }
                    } else {
                        _connected.value = false
                        _status.value = "Standalone Mode (Phone Sensors Active)"
                    }
                    response.close()
                } catch (e: Exception) {
                    _connected.value = false
                    _status.value = "Standalone Mode (Phone Sensors Active)"
                }
                delay(1000)
            }
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
            val json = JSONObject().apply {
                put("line1", text)
                put("line2", mode)
                put("speed", speed)
                put("score", score)
                put("glyph", glyph)
                put("vehicleMode", vehicleMode)
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("http://$baseIp/display")
                .post(body)
                .build()

            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // Standalone mode gracefully suppresses network exceptions
        }
    }

    override suspend fun resetHardwareSos() = withContext(Dispatchers.IO) {
        try {
            _hardwareSos.value = false
            val emptyBody = "".toRequestBody(null)
            val request = Request.Builder()
                .url("http://$baseIp/reset_sos")
                .post(emptyBody)
                .build()

            client.newCall(request).execute().close()
        } catch (e: Exception) {
            _hardwareSos.value = false
        }
    }

    override fun getCameraStreamUrl(): String = "http://$baseIp:81/stream"
}
