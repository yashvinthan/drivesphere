package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlwaysOnDisplayScreen(state: AppState, onWake: () -> Unit) {
    var showColon by remember { mutableStateOf(true) }
    var currentHour by remember { mutableStateOf("12") }
    var currentMinute by remember { mutableStateOf("00") }

    LaunchedEffect(Unit) {
        val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
        val minuteFormat = SimpleDateFormat("mm", Locale.getDefault())
        while (true) {
            val now = Date()
            currentHour = hourFormat.format(now)
            currentMinute = minuteFormat.format(now)
            showColon = !showColon
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onWake() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "OLED ALWAYS-ON DISPLAY",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Glyph
            GlyphMatrixDirection(
                direction = state.idleGlyph,
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Real Live Time Clock
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentHour, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(if (showColon) ":" else " ", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text(currentMinute, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Telemetry Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SPEED", style = MaterialTheme.typography.labelMedium, color = Color.DarkGray)
                    Text("${state.activeTripSpeedKmH.toInt()} KM/H", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DISTANCE", style = MaterialTheme.typography.labelMedium, color = Color.DarkGray)
                    Text(String.format(Locale.getDefault(), "%.1f KM", state.activeTripDistanceKm), style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            Text("TAP SCREEN TO WAKE", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
        }
    }
}
