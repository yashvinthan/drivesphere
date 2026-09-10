package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Esp32CamRepository(
    private var baseIp: String = "192.168.4.2"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val _isCamConnected = MutableStateFlow(false)
    val isCamConnected: Flow<Boolean> = _isCamConnected.asStateFlow()

    private val _isFlashActive = MutableStateFlow(false)
    val isFlashActive: Flow<Boolean> = _isFlashActive.asStateFlow()

    private val _cameraStatusText = MutableStateFlow("Searching for AI Camera...")
    val cameraStatusText: Flow<String> = _cameraStatusText.asStateFlow()

    fun setCameraIp(ip: String) {
        baseIp = ip.trim()
    }

    fun getCameraIp(): String = baseIp

    fun getStreamUrl(): String = "http://$baseIp/stream"

    suspend fun pingCamera(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("http://$baseIp/status")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: "{}"
                response.close()
                val json = JSONObject(bodyStr)
                val ready = json.optBoolean("cameraReady", true)
                _isCamConnected.value = ready
                _isFlashActive.value = json.optBoolean("flashActive", false)
                _cameraStatusText.value = "AI Cam Online (${latency}ms)"
                Result.success("ESP32-CAM Linked (${latency}ms)")
            } else {
                response.close()
                _isCamConnected.value = false
                _cameraStatusText.value = "Camera Server Error ${response.code}"
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            _isCamConnected.value = false
            _cameraStatusText.value = "Camera Offline (${e.localizedMessage ?: "Timeout"})"
            Result.failure(e)
        }
    }

    suspend fun fetchSnapshot(): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$baseIp/capture")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        _isCamConnected.value = true
                        return@withContext Result.success(bitmap)
                    }
                }
                Result.failure(Exception("Failed to decode JPEG"))
            } else {
                response.close()
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFlash(enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$baseIp/flash?state=${if (enable) "1" else "0"}")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.close()
                _isFlashActive.value = enable
                Result.success(enable)
            } else {
                response.close()
                Result.failure(Exception("Flash command failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
