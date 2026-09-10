package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticSettingsScreen(
    mappings: Map<String, String>,
    onUpdateMapping: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val events = listOf("Navigation Alert", "Hazard Detected", "Battery Low")
    val patterns = listOf("Short Pulse", "Double Pulse", "Continuous", "Heartbeat")

    var selectedEvent by remember { mutableStateOf<String?>(null) }

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
            Text("HAPTIC MAPPING", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ASSIGN GLYPH PULSE PATTERNS TO EVENTS", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        events.forEach { event ->
            val currentPattern = mappings[event] ?: "Short Pulse"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (selectedEvent == event) Color.White else Color.DarkGray),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { selectedEvent = if (selectedEvent == event) null else event }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(event.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Text(currentPattern.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                    }

                    if (selectedEvent == event) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        patterns.forEach { pattern ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdateMapping(event, pattern)
                                        selectedEvent = null
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pattern.uppercase(), style = MaterialTheme.typography.bodyLarge, color = if (currentPattern == pattern) Color.White else Color.Gray)
                                if (currentPattern == pattern) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
