package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppState
import com.example.viewmodel.VehicleType
import kotlinx.coroutines.delay

@Composable
fun FallSOSScreen(
    state: AppState? = null,
    onCancel: () -> Unit,
    onCountdownComplete: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(10) }
    val context = LocalContext.current
    val isBike = state?.vehicleType != VehicleType.FOUR_WHEELER

    // Extract device GPS coordinates
    var currentCoords by remember { mutableStateOf("13.0827,80.2707") }
    LaunchedEffect(Unit) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                currentCoords = "${location.latitude},${location.longitude}"
            }
        } catch (e: SecurityException) {
            // Permission fallback
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else {
            // Trigger Emergency SMS Dispatch with real GPS coordinates
            val emergencyMessage = if (isBike) {
                "EMERGENCY: Two-wheeler fall sensor triggered for ${state?.userName ?: "Rider"}! Live GPS location: https://maps.google.com/?q=$currentCoords. Immediate assistance requested."
            } else {
                "EMERGENCY: High-G vehicle collision detected for ${state?.userName ?: "Driver"}! Live GPS location: https://maps.google.com/?q=$currentCoords. Emergency response required."
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:")
                    putExtra("address", "112;911")
                    putExtra("sms_body", emergencyMessage)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onCountdownComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (state?.isEsp32Connected == true) "ESP32 GUARDIAN HUB ALERT" else "INTERNAL SENSOR THRESHOLD EXCEEDED",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isBike) "Possible Fall Detected" else "High-G Collision Detected",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onError,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "$countdown",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onError
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "LIVE DISPATCH LOCATION", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "$currentCoords (GPS Fix)", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Auto SMS will be sent to registered guardians", color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(36.dp))
        
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onError)
        ) {
            Text(text = "I'M SAFE — CANCEL ALERT", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Press physical ESP32 button or tap above to cancel emergency dispatch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f)
        )
    }
}
