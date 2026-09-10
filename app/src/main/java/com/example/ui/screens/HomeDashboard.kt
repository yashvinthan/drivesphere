package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppState
import com.example.viewmodel.HardwareTransport
import com.example.viewmodel.VehicleType
import kotlinx.coroutines.delay

@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onNavigateToChallan: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToDashcam: () -> Unit = {},
    onToggleVehicleType: (VehicleType) -> Unit = {},
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {},
    onToggleHubStatus: () -> Unit = {},
    onSetNightGlow: (Boolean) -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }
    val ambientLux by rememberAmbientLightLevel()
    
    LaunchedEffect(ambientLux) {
        onSetNightGlow(ambientLux < 20f)
    }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val scrollState = rememberScrollState()
    val bgColor = if (state.isNightGlowMode) Color.Black else MaterialTheme.colorScheme.background
    val primaryTextColor = if (state.isNightGlowMode) Color.White else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DRIVESPHERE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = if (state.isNightGlowMode) androidx.compose.ui.graphics.Shadow(color = Color.White, blurRadius = 8f) else null
                ),
                color = primaryTextColor
            )

            // Vehicle Type Switcher Chip (Two-Wheeler vs Four-Wheeler)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clickable {
                    val nextType = if (state.vehicleType == VehicleType.TWO_WHEELER) VehicleType.FOUR_WHEELER else VehicleType.TWO_WHEELER
                    onToggleVehicleType(nextType)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.vehicleType == VehicleType.TWO_WHEELER) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                        contentDescription = "Switch Vehicle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.vehicleType == VehicleType.TWO_WHEELER) "BIKE" else "CAR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        // Hardware Status Pill (Interactive link to Guardian Hub)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.clickable { onNavigateToHub() },
                color = Color(0xFF141414),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (state.isEsp32Connected) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF282828))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.isEsp32Connected) Color(0xFF00E676) else Color(0xFF29B6F6))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusLabel = if (state.isEsp32Connected) {
                        if (state.hardwareTransport == HardwareTransport.BLUETOOTH) "ESP32 BT LINKED" else "ESP32 WI-FI LINKED"
                    } else {
                        "HUB DISCONNECTED • TAP TO LINK"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (state.isEsp32Connected) Color(0xFF00E676) else Color(0xFF888888)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Hub",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (state.isVehicleGuardArmed) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00E676).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("GUARD ARMED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                }
            }
        }

        // Greeting Header
        Column {
            Text("TODAY'S DRIVE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "HI, ${state.userName.uppercase()}", 
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), 
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Weather & Battery Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlyphWidgetCard(
                modifier = Modifier.weight(1f),
                title = "WEATHER",
                value = "24°C CLEAR",
                glyph = "SUN",
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.weight(1f).clickable { onToggleCharging() },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (state.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isCharging) {
                            AnimatedChargingGlyph(modifier = Modifier.fillMaxSize())
                        } else {
                            GlyphMatrixDirection(direction = "BATTERY_FULL", modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("BATTERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (state.isCharging) "CHARGING" else "85% EST", style = MaterialTheme.typography.titleMedium, color = if (state.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SOS Button
        Button(
            onClick = onSOS,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Spacer(modifier = Modifier.width(12.dp))
            Text("SOS EMERGENCY DISPATCH", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Traffic Behaviour Score Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TRAFFIC BEHAVIOUR SCORE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.summary.score / 100f },
                        modifier = Modifier.size(150.dp),
                        color = if (state.summary.score >= 85) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 10.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.summary.score}", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold))
                        Text(if (state.summary.score >= 85) "EXCELLENT" else "GOOD", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                
                Spacer(modifier = Modifier.height(18.dp))

                // Mode-Specific Compliance Status Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "HELMET COMPLIANCE" else "SEATBELT COMPLIANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "94% (ISI VERIFIED)" else "98% (BUCKLED)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "FALL DETECTION" else "COLLISION SENSOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick Action Hub Cards
        Text("COMMAND SHORTCUTS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(14.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Directions, 
                label = "TRIP", 
                color = MaterialTheme.colorScheme.primary, 
                onClick = onStartTrip
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Videocam, 
                label = "AI CAM", 
                color = Color(0xFF00E5FF), 
                onClick = onNavigateToDashcam
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ReceiptLong, 
                label = "CHALLAN", 
                color = Color(0xFFFFB300), 
                onClick = onNavigateToChallan
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Security, 
                label = "GUARD", 
                color = Color(0xFF00E676), 
                onClick = onNavigateToSecurity
            )
            CategoryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.SettingsInputAntenna, 
                label = "ESP32", 
                color = MaterialTheme.colorScheme.secondary, 
                onClick = onNavigateToHub
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Weekly Distance Chart
        Text("WEEKLY PERFORMANCE & SPEED", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(14.dp))
        WeeklyGraphCard()
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Route,
                title = "SAFE DISTANCE",
                value = "124.8 km",
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Eco,
                title = "REWARD COINS",
                value = "${state.driveCoins} DC",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun GlyphWidgetCard(modifier: Modifier = Modifier, title: String, value: String, glyph: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                GlyphMatrixDirection(direction = glyph, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun CategoryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector, 
    label: String, 
    color: Color, 
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Composable
fun AnimatedChargingGlyph(modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(500)
            step = (step + 1) % 4
        }
    }
    val glyph = when(step) {
        0 -> "BATTERY_EMPTY"
        1 -> "BATTERY_HALF"
        2 -> "BATTERY_FULL"
        else -> "BATTERY_FULL"
    }
    GlyphMatrixDirection(direction = glyph, modifier = modifier)
}

@Composable
fun WeeklyGraphCard() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E0E)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dataPoints = listOf(20f, 40f, 35f, 60f, 50f, 80f, 65f)
                val width = size.width
                val height = size.height
                
                for (i in 0..4) {
                    val y = height - (i * height / 4f)
                    drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.5f)
                }
                
                val stepX = width / (dataPoints.size - 1)
                val path = Path()
                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / 100f * height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = secondaryColor, radius = 6f, center = Offset(x, y))
                }
                
                drawPath(path = path, color = primaryColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        border = BorderStroke(1.dp, Color(0xFF2B2B2B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
    }
}
