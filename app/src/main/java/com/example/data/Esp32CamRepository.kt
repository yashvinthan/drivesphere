package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class Esp32CamRepository(
    private var baseIp: String = "192.168.4.1"
) {
    private fun getClient(forHost: String = ""): OkHttpClient {
        val currentNet = NetworkBinder.getWifiNetwork()
        val isHotspotHost = forHost.startsWith("192.168.43.")

        val builder = OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .writeTimeout(1500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)

        if (currentNet != null && !isHotspotHost) {
            builder.socketFactory(currentNet.socketFactory)
        }

        return builder.build()
    }

    private val _isCamConnected = MutableStateFlow(false)
    val isCamConnected: Flow<Boolean> = _isCamConnected.asStateFlow()

    private val _isHubConnected = MutableStateFlow(false)
    val isHubConnected: Flow<Boolean> = _isHubConnected.asStateFlow()

    private val _isFlashActive = MutableStateFlow(false)
    val isFlashActive: Flow<Boolean> = _isFlashActive.asStateFlow()

    private val _cameraStatusText = MutableStateFlow("Searching for AI Camera...")
    val cameraStatusText: Flow<String> = _cameraStatusText.asStateFlow()

    fun setCameraIp(ip: String) {
        baseIp = ip.trim()
    }

    fun getCameraIp(): String = baseIp

    fun getStreamUrl(): String = "http://$baseIp/stream"

    /**
     * Dynamically assemble candidate IPs across:
     * 1. Hub AP network (192.168.4.x)
     * 2. Hotspot / Tethering network (192.168.43.x)
     * 3. Fallback AP subnet (192.168.5.x)
     * 4. Active local interface subnets (192.168.1.x, etc.)
     */
    fun getCandidateIps(): List<String> {
        val set = linkedSetOf<String>()
        // 1. Current active target
        set.add(baseIp)

        // 2. Guardian Hub vehicle network candidates (192.168.4.1 is All-In-One Hub + Cam)
        set.add("192.168.4.1")
        set.add("192.168.4.2")
        set.add("192.168.4.3")
        set.add("192.168.4.4")
        set.add("192.168.5.1")
        set.add("192.168.5.2")

        // 3. Android Mobile Hotspot subnet candidates (192.168.43.x)
        for (i in 2..25) {
            set.add("192.168.43.$i")
        }

        // 4. Probe dynamically discovered local subnets from network interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: ""
                            val parts = host.split(".")
                            if (parts.size == 4) {
                                val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
                                if (prefix != "192.168.43." && prefix != "192.168.4.") {
                                    set.add("${prefix}1")
                                    for (j in 2..15) {
                                        set.add("$prefix$j")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return set.toList()
    }

    suspend fun pingCamera(): Result<String> = withContext(Dispatchers.IO) {
        // 1. Try probing current baseIp first
        val directRes = probeCameraAtIp(baseIp)
        if (directRes != null) {
            _isCamConnected.value = true
            _cameraStatusText.value = "ESP32-CAM Linked on $baseIp"
            return@withContext Result.success("ESP32-CAM Linked on $baseIp")
        }

        // 2. Check if Guardian Hub is reachable at 192.168.4.1
        val (hubOk, _) = NetworkBinder.pingHost("192.168.4.1", 80, 600)
        if (hubOk) {
            _isHubConnected.value = true
        }

        // 3. Fast scan high-priority vehicle & hotspot candidates concurrently
        val priorityCandidates = listOf(
            "192.168.4.1", "192.168.4.2", "192.168.4.3", "192.168.4.4",
            "192.168.43.2", "192.168.43.3", "192.168.43.4", "192.168.43.5",
            "192.168.5.1"
        ).filter { it != baseIp }

        for (candidate in priorityCandidates) {
            val res = probeCameraAtIp(candidate)
            if (res != null) {
                baseIp = candidate
                _isCamConnected.value = true
                _cameraStatusText.value = "Discovered Camera on $candidate"
                return@withContext Result.success("Discovered Camera on $candidate")
            }
        }

        _isCamConnected.value = false
        if (_isHubConnected.value) {
            _cameraStatusText.value = "DriveSphere Connected (192.168.4.1) • Fetching Camera Feed..."
        } else {
            _cameraStatusText.value = "Camera Offline (Connect to 'DriveSphere-Hub')"
        }
        Result.failure(Exception("Could not find active camera stream"))
    }

    /**
     * Comprehensive multi-subnet scan across all candidate IPs.
     * Tests ports 80 and 81 for live JPEG frames or MJPEG streams.
     */
    suspend fun scanAndDiscoverCamera(): Result<String> = withContext(Dispatchers.IO) {
        val candidates = getCandidateIps()
        _cameraStatusText.value = "Scanning ${candidates.size} candidate network addresses..."

        // Run concurrent probes in batches of 10
        val batches = candidates.chunked(10)
        for (batch in batches) {
            val foundIp = coroutineScope {
                val deferreds = batch.map { ip ->
                    async(Dispatchers.IO) {
                        val frame = probeCameraAtIp(ip)
                        if (frame != null) ip else null
                    }
                }
                deferreds.awaitAll().filterNotNull().firstOrNull()
            }

            if (foundIp != null) {
                baseIp = foundIp
                _isCamConnected.value = true
                _cameraStatusText.value = "Auto-Discovered Camera on $foundIp!"
                return@withContext Result.success("Linked ESP32-CAM on $foundIp")
            }
        }

        _isCamConnected.value = false
        Result.failure(Exception("No camera discovered across ${candidates.size} addresses"))
    }

    /**
     * Tests an IP address to see if it hosts an active camera.
     * Checks:
     * 1. Port 80 /capture (standard JPEG snapshot)
     * 2. Port 81 /stream (standard Arduino CameraWebServer MJPEG)
     * 3. Port 80 /stream (DriveSphere native MJPEG)
     * 4. Port 80 /status (DriveSphere Cam JSON or CameraWebServer JSON)
     */
    private fun probeCameraAtIp(ip: String): Bitmap? {
        val (p80Ok, _) = NetworkBinder.pingHost(ip, 80, 500)
        val (p81Ok, _) = NetworkBinder.pingHost(ip, 81, 500)
        if (!p80Ok && !p81Ok) return null

        val client = getClient(ip)

        // 1. Try port 80 /capture
        if (p80Ok) {
            val bmp = tryFetchSnapshot(client, ip)
            if (bmp != null) return bmp
        }

        // 2. Try port 81 /stream (standard CameraWebServer port)
        if (p81Ok) {
            val bmp81 = tryExtractFrameFromStream(client, "http://$ip:81/stream")
            if (bmp81 != null) return bmp81
        }

        // 3. Try port 80 /stream
        if (p80Ok) {
            val bmp80 = tryExtractFrameFromStream(client, "http://$ip/stream")
            if (bmp80 != null) return bmp80
        }

        // 4. Try port 80 /status JSON inspection
        if (p80Ok) {
            try {
                val req = Request.Builder().url("http://$ip/status").get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val json = JSONObject(body)
                    val isHub = json.has("speed") || json.optString("device").contains("Hub", true) || json.optString("device").contains("AllInOne", true)
                    val isCam = json.has("framesize") || json.optBoolean("cameraReady", false) || json.optString("device").contains("Cam", true) || json.optString("device").contains("AllInOne", true)
                    if (isHub) {
                        _isHubConnected.value = true
                    }
                    if (isCam) {
                        // It is a camera! Try capture again or wait for stream
                        return tryFetchSnapshot(client, ip)
                    }
                } else {
                    resp.close()
                }
            } catch (_: Exception) {}
        }

        return null
    }

    suspend fun fetchSnapshot(): Result<Bitmap> = withContext(Dispatchers.IO) {
        val client = getClient(baseIp)

        // 1. First attempt: Direct /capture on active baseIp
        val attempt1 = tryFetchSnapshot(client, baseIp)
        if (attempt1 != null) {
            _isCamConnected.value = true
            return@withContext Result.success(attempt1)
        }

        // 2. Second attempt: Extract JPEG frame from live stream (port 80 or 81)
        val streamAttempt = tryExtractFrameFromStream(client, "http://$baseIp/stream")
            ?: tryExtractFrameFromStream(client, "http://$baseIp:81/stream")
        if (streamAttempt != null) {
            _isCamConnected.value = true
            return@withContext Result.success(streamAttempt)
        }

        // 3. If baseIp failed and baseIp is 192.168.4.1 (the Hub), immediately try 192.168.4.2 (vehicle cam)
        if (baseIp == "192.168.4.1") {
            val camAttempt = probeCameraAtIp("192.168.4.2")
            if (camAttempt != null) {
                baseIp = "192.168.4.2"
                _isCamConnected.value = true
                return@withContext Result.success(camAttempt)
            }
        }

        // 4. Quick probe across primary hotspot & vehicle IPs
        // Include the camera's standalone AP. The firmware deliberately uses
        // 192.168.5.1 to avoid colliding with the Guardian Hub's 192.168.4.x AP.
        val fastCandidates = listOf(
            "192.168.4.2",
            "192.168.5.1",
            "192.168.43.2",
            "192.168.43.3",
            "192.168.43.4",
            "192.168.4.3"
        )
        for (cand in fastCandidates) {
            if (cand == baseIp) continue
            val candBmp = probeCameraAtIp(cand)
            if (candBmp != null) {
                baseIp = cand
                _isCamConnected.value = true
                return@withContext Result.success(candBmp)
            }
        }

        _isCamConnected.value = false
        Result.failure(Exception("Failed to fetch camera frame from $baseIp"))
    }

    private fun tryFetchSnapshot(client: OkHttpClient, ip: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url("http://$ip/capture")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes != null && bytes.size > 100) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else null
            } else {
                response.close()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Grabs a single JPEG frame from an active MJPEG stream (port 80 or 81).
     * Extracts bytes between JPEG SOI (0xFF, 0xD8) and EOI (0xFF, 0xD9).
     */
    private fun tryExtractFrameFromStream(client: OkHttpClient, streamUrl: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(streamUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body ?: return null
            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(4096)
            val output = ByteArrayOutputStream()
            var foundStart = false
            var prevByte = 0
            val maxBytes = 256 * 1024

            while (output.size() < maxBytes) {
                val n = inputStream.read(buffer)
                if (n == -1) break
                for (i in 0 until n) {
                    val b = buffer[i].toInt() and 0xFF
                    if (!foundStart) {
                        if (prevByte == 0xFF && b == 0xD8) {
                            foundStart = true
                            output.write(0xFF)
                            output.write(0xD8)
                        }
                    } else {
                        output.write(b)
                        if (prevByte == 0xFF && b == 0xD9) {
                            response.close()
                            val frameBytes = output.toByteArray()
                            return BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                        }
                    }
                    prevByte = b
                }
            }
            response.close()
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun toggleFlash(enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = getClient(baseIp)
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
