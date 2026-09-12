package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlin.math.min
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiSafetyVisionEngine
import com.example.ai.AiVisionState
import com.example.data.Esp32CamRepository
import com.example.viewmodel.AppState
import com.example.viewmodel.VehicleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDashcamScreen(
    state: AppState,
    camRepo: Esp32CamRepository = remember { Esp32CamRepository() },
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    var isStreaming by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var targetIpInput by remember { mutableStateOf(camRepo.getCameraIp()) }
    var isScanning by remember { mutableStateOf(false) }

    val isCamConnected by camRepo.isCamConnected.collectAsState(initial = false)
    val isHubConnected by camRepo.isHubConnected.collectAsState(initial = false)
    val camStatusText by camRepo.cameraStatusText.collectAsState(initial = "Searching for AI Camera...")

    val visionEngine = remember { AiSafetyVisionEngine() }
    val visionState by visionEngine.visionState.collectAsState()

    // Live frame polling loop - Real hardware frames only
    LaunchedEffect(isStreaming, camRepo.getCameraIp(), state.speedKmH, state.vehicleType) {
        var tick = 0L
        while (isActive && isStreaming) {
            val result = camRepo.fetchSnapshot()
            if (result.isSuccess && result.getOrNull() != null) {
                val frame = result.getOrNull()
                currentFrame = frame
                frame?.let {
                    visionEngine.analyzeFrame(it, state.speedKmH, state.vehicleType)
                }
            } else {
                currentFrame = null
                // Background ping to maintain connection status
                if (tick % 10 == 0L) {
                    camRepo.pingCamera()
                }
            }
            tick++
            delay(70) // Real hardware snapshot polling interval (~14 FPS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "AI RIDER MONITOR (DMS)" else "AI DRIVER MONITOR (DMS)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        )
                        val (subtitleText, subtitleColor) = when {
                            currentFrame != null ->
                                Pair("LIVE DMS FEED ACTIVE • IP: ${camRepo.getCameraIp()}", Color(0xFF4CAF50))
                            isCamConnected ->
                                Pair("ALL-IN-ONE LINKED • FETCHING FRAMES...", Color(0xFFFFA000))
                            isHubConnected ->
                                Pair("HUB CONNECTED (192.168.4.1) • PROBING CAMERA...", Color(0xFFFFA000))
                            else ->
                                Pair("CAMERA OFFLINE • CONNECT TO 'DriveSphere-Hub'", Color.Gray)
                        }

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subtitleColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val nextState = !isFlashOn
                            val res = camRepo.toggleFlash(nextState)
                            if (res.isSuccess) isFlashOn = nextState
                        }
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flashlight",
                            tint = if (isFlashOn) Color.Yellow else Color.White
                        )
                    }
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Camera Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Main Video Viewport (16:9 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                if (currentFrame != null) {
                    Image(
                        bitmap = currentFrame!!.asImageBitmap(),
                        contentDescription = "Live Hardware Camera Feed",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Tactical HUD Canvas Overlay for On-Device Driver Monitoring (DMS)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // 1. Red Alert Perimeter if Driver is Distracted or Drowsy
                        if (visionState.distraction.isLookingDownAtPhone || visionState.distraction.isDrowsy) {
                            drawRect(
                                color = Color(0x33FF1744),
                                size = size
                            )
                            drawRect(
                                color = Color(0xFFFF1744),
                                size = size,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }

                        // 2. Driver Face Tracking Tactical Reticle
                        val normFace = visionState.distraction.normalizedFaceBox
                        if (normFace != null) {
                            val fLeft = normFace.left * size.width
                            val fTop = normFace.top * size.height
                            val fRight = normFace.right * size.width
                            val fBottom = normFace.bottom * size.height
                            val fWidth = fRight - fLeft
                            val fHeight = fBottom - fTop

                            val faceColor = when {
                                visionState.distraction.isLookingDownAtPhone || visionState.distraction.isDrowsy -> Color(0xFFFF1744)
                                visionState.distraction.isLookingAwayFromRoad -> Color(0xFFFF9100)
                                else -> Color(0xFF00E5FF)
                            }

                            // Translucent face tint
                            drawRect(
                                color = faceColor.copy(alpha = 0.08f),
                                topLeft = Offset(fLeft, fTop),
                                size = Size(fWidth, fHeight)
                            )

                            // Tactical 4-Corner Reticle Brackets
                            val cLen = min(24f, min(fWidth, fHeight) * 0.25f)
                            val strokeW = 3.5f

                            // Top-Left
                            drawLine(faceColor, Offset(fLeft, fTop), Offset(fLeft + cLen, fTop), strokeWidth = strokeW)
                            drawLine(faceColor, Offset(fLeft, fTop), Offset(fLeft, fTop + cLen), strokeWidth = strokeW)

                            // Top-Right
                            drawLine(faceColor, Offset(fRight, fTop), Offset(fRight - cLen, fTop), strokeWidth = strokeW)
                            drawLine(faceColor, Offset(fRight, fTop), Offset(fRight, fTop + cLen), strokeWidth = strokeW)

                            // Bottom-Left
                            drawLine(faceColor, Offset(fLeft, fBottom), Offset(fLeft + cLen, fBottom), strokeWidth = strokeW)
                            drawLine(faceColor, Offset(fLeft, fBottom), Offset(fLeft, fBottom - cLen), strokeWidth = strokeW)

                            // Bottom-Right
                            drawLine(faceColor, Offset(fRight, fBottom), Offset(fRight - cLen, fBottom), strokeWidth = strokeW)
                            drawLine(faceColor, Offset(fRight, fBottom), Offset(fRight, fBottom - cLen), strokeWidth = strokeW)

                            // Center eye-level gaze tracking pip
                            val gazeCenter = Offset(fLeft + fWidth / 2f, fTop + fHeight * 0.40f)
                            drawCircle(color = faceColor, radius = 3.5f, center = gazeCenter)
                        }

                        // 3. Rider Helmet Dome Reticle (Two-Wheeler Mode)
                        val normHelmet = visionState.helmet.normalizedHelmetBox
                        if (state.vehicleType == VehicleType.TWO_WHEELER && normHelmet != null) {
                            val hLeft = normHelmet.left * size.width
                            val hTop = normHelmet.top * size.height
                            val hRight = normHelmet.right * size.width
                            val hBottom = normHelmet.bottom * size.height
                            val hWidth = hRight - hLeft

                            val helmetColor = if (visionState.helmet.isHelmetWorn) Color(0xFF00E676) else Color(0xFFFF5722)
                            val cLen = min(20f, hWidth * 0.25f)
                            val strokeW = 3f

                            // Top-Left dome bracket
                            drawLine(helmetColor, Offset(hLeft, hTop), Offset(hLeft + cLen, hTop), strokeWidth = strokeW)
                            drawLine(helmetColor, Offset(hLeft, hTop), Offset(hLeft, hTop + cLen), strokeWidth = strokeW)

                            // Top-Right dome bracket
                            drawLine(helmetColor, Offset(hRight, hTop), Offset(hRight - cLen, hTop), strokeWidth = strokeW)
                            drawLine(helmetColor, Offset(hRight, hTop), Offset(hRight, hTop + cLen), strokeWidth = strokeW)
                        }

                        // 4. Secondary Objects Reticles (e.g. Phone or Handheld electronics)
                        visionState.trackedObstacles.forEach { obs ->
                            val left = obs.normalizedBox.left * size.width
                            val top = obs.normalizedBox.top * size.height
                            val right = obs.normalizedBox.right * size.width
                            val bottom = obs.normalizedBox.bottom * size.height
                            val boxWidth = right - left
                            val boxHeight = bottom - top

                            val color = if (obs.label.contains("PHONE") || visionState.distraction.isPhoneObjectDetected) Color(0xFFFF1744) else Color(0xFFFFA000)
                            val cLen = min(16f, min(boxWidth, boxHeight) * 0.25f)
                            val strokeW = 2.5f

                            drawLine(color, Offset(left, top), Offset(left + cLen, top), strokeWidth = strokeW)
                            drawLine(color, Offset(left, top), Offset(left, top + cLen), strokeWidth = strokeW)
                            drawLine(color, Offset(right, top), Offset(right - cLen, top), strokeWidth = strokeW)
                            drawLine(color, Offset(right, top), Offset(right, top + cLen), strokeWidth = strokeW)
                            drawLine(color, Offset(left, bottom), Offset(left + cLen, bottom), strokeWidth = strokeW)
                            drawLine(color, Offset(left, bottom), Offset(left, bottom - cLen), strokeWidth = strokeW)
                            drawLine(color, Offset(right, bottom), Offset(right - cLen, bottom), strokeWidth = strokeW)
                            drawLine(color, Offset(right, bottom), Offset(right, bottom - cLen), strokeWidth = strokeW)
                        }
                    }

                    // Live Hardware Camera HUD Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        // Top HUD Bar: Recording Status, ML Inference Latency & Attention
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Recording & ML Frame Latency
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE DMS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                if (visionState.inferenceTimeMs > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${visionState.inferenceTimeMs}ms",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Color(0xFF00E676)
                                    )
                                }
                            }

                            // Right: Vehicle Mode & Attention Score
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (visionState.distraction.isDriverFaceVisible)
                                        "ATTN: ${visionState.distraction.attentionScore}%"
                                    else
                                        if (state.vehicleType == VehicleType.TWO_WHEELER) "RIDER DMS" else "CABIN DMS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (visionState.distraction.attentionScore >= 70) Color.Cyan else Color(0xFFFF9100)
                                )
                            }
                        }

                        // Center: High-Priority Emergency Alert Banners
                        val activeAlert: Pair<String, Color>? = when {
                            visionState.distraction.isLookingDownAtPhone ->
                                Pair("⚠️ PHONE DISTRACTION DETECTED • EYES ON ROAD", Color(0xFFFF1744))
                            visionState.distraction.isLookingAwayFromRoad ->
                                Pair("⚠️ ATTENTION: LOOK AT ROAD • GAZE DEFLECTED", Color(0xFFFF9100))
                            visionState.distraction.isDrowsy ->
                                Pair("⚠️ DROWSINESS DETECTED • REST RECOMMENDED", Color(0xFFFF1744))
                            state.vehicleType == VehicleType.TWO_WHEELER && visionState.helmet.isChecked && !visionState.helmet.isHelmetWorn ->
                                Pair("⚠️ HELMET REQUIRED • NO HELMET DETECTED", Color(0xFFFF5722))
                            else -> null
                        }

                        if (activeAlert != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color(0xEE000000), RoundedCornerShape(8.dp))
                                    .border(1.5.dp, activeAlert.second, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = activeAlert.first,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = activeAlert.second
                                )
                            }
                        }

                        // Bottom Row: Gaze Telemetry & Target IP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (visionState.distraction.isDriverFaceVisible) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                if (visionState.distraction.isLookingDownAtPhone) Color.Red else Color.Cyan,
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PITCH: ${"%.0f".format(visionState.distraction.headPitchDeg)}° | YAW: ${"%.0f".format(visionState.distraction.headYawDeg)}°",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Text(
                                text = "ESP32-CAM: ${camRepo.getCameraIp()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = Color(0xBBFFFFFF),
                                modifier = Modifier
                                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                } else {
                    // Offline / Awaiting Hardware Fallback Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = if (isHubConnected) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = if (isHubConnected) Color(0xFFFFA000) else Color.Gray,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isHubConnected) "AWAITING ESP32-CAM FEED" else "CAMERA FEED OFFLINE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHubConnected)
                                "Guardian Hub is online (192.168.4.1).\nSearching for ESP32-CAM on 192.168.4.2 or standalone AP 192.168.5.1..."
                            else
                                "Connect phone to Wi-Fi 'DriveSphere-Cam' or Guardian Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isScanning = true
                                    val res = camRepo.scanAndDiscoverCamera()
                                    if (res.isSuccess) {
                                        camRepo.fetchSnapshot()
                                    }
                                    isScanning = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScanning) "SCANNING SUBNETS..." else "AUTO-DISCOVER CAMERA", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time AI Safety Diagnostics Cards (Bound to live sensors and ML inference)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DRIVER MONITORING COMPLIANCE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                    color = Color.Gray
                )
                Text(
                    text = if (currentFrame != null) "ON-DEVICE DMS ACTIVE" else "AWAITING FEED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (currentFrame != null) Color(0xFF00E676) else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Rider Helmet Wear / Driver Seatbelt
            if (state.vehicleType == VehicleType.TWO_WHEELER) {
                SafetyMetricRow(
                    title = "Rider Helmet Compliance (AI Vision)",
                    status = if (currentFrame != null) visionState.helmet.statusText else "CAMERA FEED REQUIRED",
                    icon = Icons.Default.SportsMotorsports,
                    isSafe = if (currentFrame != null) visionState.helmet.isHelmetWorn else false
                )
            } else {
                SafetyMetricRow(
                    title = "Driver Seatbelt Compliance",
                    status = if (currentFrame != null) visionState.helmet.statusText else "CAMERA FEED REQUIRED",
                    icon = Icons.Default.AirlineSeatReclineNormal,
                    isSafe = if (currentFrame != null) visionState.helmet.isHelmetWorn else false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Mobile Phone Distraction (AI Pose & Handheld Object Detection)
            val phoneDistractionStatus = if (currentFrame != null) {
                when {
                    visionState.distraction.isLookingDownAtPhone -> "PHONE DISTRACTION ACTIVE"
                    visionState.distraction.isPhoneObjectDetected -> "PHONE IN HAND DETECTED"
                    else -> "HANDS CLEAR • ATTENTIVE"
                }
            } else {
                "CAMERA FEED REQUIRED"
            }

            SafetyMetricRow(
                title = "Mobile Phone Distraction (AI Pose)",
                status = phoneDistractionStatus,
                icon = Icons.Default.PhoneAndroid,
                isSafe = if (currentFrame != null) !visionState.distraction.isLookingDownAtPhone && !visionState.distraction.isPhoneObjectDetected else false
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Driver Gaze & Drowsiness (Eye-Blink Tracker)
            val gazeStatusText = if (currentFrame != null) {
                when {
                    visionState.distraction.isDrowsy -> "DROWSY • LOW BLINK FREQUENCY"
                    visionState.distraction.isLookingAwayFromRoad -> "LOOKING AWAY • DEFLECTED GAZE"
                    else -> "FORWARD GAZE • NOMINAL"
                }
            } else {
                "CAMERA FEED REQUIRED"
            }

            SafetyMetricRow(
                title = "Driver Gaze & Drowsiness (Eye Tracker)",
                status = gazeStatusText,
                icon = Icons.Default.Visibility,
                isSafe = if (currentFrame != null) !visionState.distraction.isDrowsy && !visionState.distraction.isLookingAwayFromRoad else false
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Attention Index & Speedometer
            SafetyMetricRow(
                title = "Driver Attention Index & Speed",
                status = "${visionState.distraction.attentionScore}% ATTENTION • ${state.speedKmH.toInt()} KM/H",
                icon = Icons.Default.Speed,
                isSafe = visionState.distraction.attentionScore >= 70
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Camera Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val res = camRepo.fetchSnapshot()
                            if (res.isSuccess) currentFrame = res.getOrNull()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SNAPSHOT", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }

                Button(
                    onClick = { isStreaming = !isStreaming },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) Color.White else Color(0xFF333333)
                    )
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isStreaming) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isStreaming) "PAUSE STREAM" else "RESUME",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isStreaming) Color.Black else Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // IP Address Configuration Dialog
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Camera IP Address", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Vehicle Hub Network: 192.168.4.2\nStandalone Camera AP: 192.168.5.1\nSSID: DriveSphere-Cam",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        label = { Text("ESP32-CAM Target IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = targetIpInput == "192.168.4.2",
                            onClick = { targetIpInput = "192.168.4.2" },
                            label = { Text("192.168.4.2 (Hub)") }
                        )
                        FilterChip(
                            selected = targetIpInput == "192.168.4.1",
                            onClick = { targetIpInput = "192.168.4.1" },
                            label = { Text("192.168.4.1 (AP)") }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = targetIpInput == "192.168.43.2",
                            onClick = { targetIpInput = "192.168.43.2" },
                            label = { Text("192.168.43.2 (Hotspot)") }
                        )
                        FilterChip(
                            selected = targetIpInput == "192.168.43.3",
                            onClick = { targetIpInput = "192.168.43.3" },
                            label = { Text("192.168.43.3") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    camRepo.setCameraIp(targetIpInput)
                    showIpDialog = false
                    coroutineScope.launch {
                        camRepo.pingCamera()
                    }
                }) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SafetyMetricRow(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSafe: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = BorderStroke(1.dp, if (isSafe) Color(0xFF1E3A1E) else Color(0xFF4A1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isSafe) Color(0x224CAF50) else Color(0x22F44336), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSafe) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSafe) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}
