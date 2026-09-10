package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSecurityScreen(
    state: AppState,
    onToggleGuard: () -> Unit,
    onTriggerTamperSensor: () -> Unit,
    onDismissTamper: () -> Unit,
    onBack: () -> Unit
) {
    val isArmed = state.isVehicleGuardArmed
    val isAlert = state.isTamperAlertActive

    val infiniteTransition = rememberInfiniteTransition(label = "guardPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isArmed) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val guardColor by animateColorAsState(
        targetValue = when {
            isAlert -> MaterialTheme.colorScheme.error
            isArmed -> Color(0xFF00E676)
            else -> Color(0xFF444444)
        },
        label = "guardColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("VEHICLE GUARD", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("Anti-Theft & Geofence Perimeter", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Center Big Shield Toggle Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(guardColor.copy(alpha = 0.12f))
                    .border(2.dp, guardColor, CircleShape)
                    .clickable { onToggleGuard() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isAlert) Icons.Default.Warning else if (isArmed) Icons.Default.Security else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = guardColor,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isAlert) "TAMPER DETECTED" else if (isArmed) "ARMED" else "DISARMED",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = if (isArmed) "TAP TO DISARM" else "TAP TO ARM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Alert Warning Banner
        if (isAlert) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("UNAUTHORIZED MOVEMENT DETECTED", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Guardian Hub accelerometer detected physical vibration and displacement outside the 20m geofence perimeter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDismissTamper,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("DISMISS ALARM", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Parked Location & Geofence Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
            border = BorderStroke(1.dp, Color(0xFF2B2B2B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PARKED VEHICLE LOCATION", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF222222), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("20M GEOFENCE", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(state.parkedLocation, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Lock status: Central ignition disabled via Guardian Hub relay", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E1E1E))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ESP32 HUB LINK", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(if (state.isEsp32Connected) "ONLINE (AP)" else "PHONE INTERNAL SENSORS", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (state.isEsp32Connected) Color(0xFF00E676) else Color.LightGray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TAMPER SENSOR", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("ACTIVE (6-AXIS)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Security Test Controls
        Text("SENSOR TEST BENCH", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onTriggerTamperSensor,
                enabled = isArmed,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isArmed) MaterialTheme.colorScheme.error else Color(0xFF333333))
            ) {
                Icon(Icons.Default.Vibration, contentDescription = null, tint = if (isArmed) MaterialTheme.colorScheme.error else Color.Gray, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TRIGGER TAMPER SENSOR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isArmed) Color.White else Color.Gray)
            }

            Button(
                onClick = onToggleGuard,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isArmed) Color(0xFF222222) else Color.White)
            ) {
                Text(if (isArmed) "DISARM" else "ARM GUARD", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (isArmed) Color.White else Color.Black)
            }
        }
    }
}
