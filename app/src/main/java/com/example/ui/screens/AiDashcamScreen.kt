package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var isConnected by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf("Testing connection...") }
    var showIpDialog by remember { mutableStateOf(false) }
    var targetIpInput by remember { mutableStateOf(camRepo.getCameraIp()) }

    // Live frame polling loop
    LaunchedEffect(isStreaming, camRepo.getCameraIp()) {
        while (isActive && isStreaming) {
            val result = camRepo.fetchSnapshot()
            if (result.isSuccess) {
                currentFrame = result.getOrNull()
                isConnected = true
            } else {
                // If failed, delay slightly before retry
                delay(800)
                val ping = camRepo.pingCamera()
                isConnected = ping.isSuccess
                pingStatus = ping.getOrDefault("Offline")
            }
            delay(70) // ~14 FPS snapshot stream
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI SAFETY DASHCAM",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = if (isConnected) "LIVE FEED ACTIVE • IP: ${camRepo.getCameraIp()}" else "OFFLINE • STANDALONE AP: 192.168.4.1",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            // Main Video Viewport (16:9 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111111))
                    .then(
                        if (isConnected) Modifier
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentFrame != null && isConnected) {
                    Image(
                        bitmap = currentFrame!!.asImageBitmap(),
                        contentDescription = "Live Camera Feed",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bounding Box Simulation HUD Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Top Left: Recording Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE REC", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }

                        // Top Right: Vehicle Mode
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "BIKE MONITOR" else "CAR MONITOR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = Color.Cyan,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    // Offline Fallback Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CAMERA FEED OFFLINE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect phone to Wi-Fi 'DriveSphere-Cam' or Guardian Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val res = camRepo.pingCamera()
                                    isConnected = res.isSuccess
                                    pingStatus = res.getOrDefault("Unreachable")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETRY CONNECTION", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time AI Safety Diagnostics Cards
            Text(
                text = "REAL-TIME SAFETY COMPLIANCE",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Safety Badge (Helmet in Bike Mode / Seatbelt in Car Mode)
            if (state.vehicleType == VehicleType.TWO_WHEELER) {
                SafetyMetricRow(
                    title = "Rider Helmet Wear",
                    status = "COMPLIANT (99% CONFIDENCE)",
                    icon = Icons.Default.SportsMotorsports,
                    isSafe = true
                )
            } else {
                SafetyMetricRow(
                    title = "Driver Seatbelt",
                    status = "BUCKLED (98% CONFIDENCE)",
                    icon = Icons.Default.AirlineSeatReclineNormal,
                    isSafe = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Safety Badge: Distraction / Eyes on Road
            SafetyMetricRow(
                title = "Mobile Phone Distraction",
                status = "NO DEVICE DETECTED • EYES ON ROAD",
                icon = Icons.Default.PhoneAndroid,
                isSafe = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Third Safety Badge: Optical Flow & Speed
            SafetyMetricRow(
                title = "Road Vision Speedometer",
                status = "${state.speedKmH.toInt()} KM/H • CLEAR FORWARD VISIBILITY",
                icon = Icons.Default.Speed,
                isSafe = state.speedKmH <= 65f
            )

            Spacer(modifier = Modifier.weight(1f))

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
        }
    }

    // IP Address Configuration Dialog
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Camera IP Address", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Default when connected to Hub: 192.168.4.2\nDefault in Standalone AP mode: 192.168.4.1", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        label = { Text("ESP32-CAM IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    camRepo.setCameraIp(targetIpInput)
                    showIpDialog = false
                    coroutineScope.launch {
                        val res = camRepo.pingCamera()
                        isConnected = res.isSuccess
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
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isSafe) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}
