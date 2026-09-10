cat << 'INNER_EOF' > app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppState
import com.example.viewmodel.TripEvent
import com.example.ui.theme.OLEDBlack
import com.example.ui.theme.OLEDWhite
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun ActiveTripScreen(
    state: AppState,
    onSimulateEvent: (TripEvent) -> Unit,
    onEndTrip: () -> Unit
) {
    val collegeLocation = LatLng(37.4221, -122.0841) // Default coords (Googleplex)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(collegeLocation, 15f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Active Session", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Interactive Google Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true),
                properties = MapProperties(isMyLocationEnabled = false) // Requires location permissions to be true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Monochrome OLED Preview Component
        Card(
            colors = CardDefaults.cardColors(containerColor = OLEDBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "OLED PREVIEW (128x64)", style = MaterialTheme.typography.labelSmall, color = OLEDWhite.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "← TURN LEFT", style = MaterialTheme.typography.bodyLarge, color = OLEDWhite)
                Text(text = "120 m", style = MaterialTheme.typography.titleLarge, color = OLEDWhite)
                Text(text = "38 km/h • Score ${state.summary.score}", style = MaterialTheme.typography.bodyMedium, color = OLEDWhite)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("ESP32-CAM Stream", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Awaiting Hardware connection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Simulate Events", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton("Overspeed", { onSimulateEvent(TripEvent.OVERSPEED) }, Modifier.weight(1f))
            DemoButton("Distract", { onSimulateEvent(TripEvent.DISTRACTION) }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton("Helmet", { onSimulateEvent(TripEvent.HELMET_RISK) }, Modifier.weight(1f))
            DemoButton("Fall", { onSimulateEvent(TripEvent.FALL) }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onEndTrip,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("End Session", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}
INNER_EOF
