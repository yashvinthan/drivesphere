package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PastTripEntity

@Composable
fun TripHistoryScreen(
    trips: List<PastTripEntity>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("TRIP HISTORY", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("PAST DRIVES", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(trips) { trip ->
                val durationMins = (trip.distanceKm * 2.5).toInt()
                val avgSpeed = (trip.distanceKm / (durationMins / 60.0)).toInt().coerceIn(20, 80)
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color.DarkGray),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(trip.date.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("SCORE: ${trip.score}", style = MaterialTheme.typography.labelMedium, color = if (trip.score >= 90) Color.White else Color.Gray)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                Text("${trip.distanceKm} KM", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.LightGray)
                            }
                            Column {
                                Text("DURATION", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                Text("$durationMins MIN", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.LightGray)
                            }
                            Column {
                                Text("AVG SPEED", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                Text("$avgSpeed KM/H", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
