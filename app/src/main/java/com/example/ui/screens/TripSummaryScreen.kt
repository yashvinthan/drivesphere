package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun TripSummaryScreen(
    distanceKm: Double = 4.2,
    durationMins: Int = 14,
    finalScore: Int = 92,
    onComplete: () -> Unit
) {
    val displayDistance = String.format(Locale.getDefault(), "%.1f km", distanceKm.coerceAtLeast(0.1))
    val displayDuration = "${durationMins.coerceAtLeast(1)} mins"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Trip Session Summary",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Telemetry logged & saved to secure database",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Map Route Visualization (Dark)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(180.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val surfaceColor = MaterialTheme.colorScheme.surface
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val path = Path().apply {
                        moveTo(width * 0.1f, height * 0.8f)
                        cubicTo(width * 0.3f, height * 0.9f, width * 0.4f, height * 0.2f, width * 0.6f, height * 0.5f)
                        cubicTo(width * 0.7f, height * 0.7f, width * 0.8f, height * 0.1f, width * 0.9f, height * 0.2f)
                    }
                    
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(
                            width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
                        )
                    )
                    
                    drawCircle(color = secondaryColor, radius = 16f, center = Offset(width * 0.1f, height * 0.8f))
                    drawCircle(color = surfaceColor, radius = 8f, center = Offset(width * 0.1f, height * 0.8f))
                    
                    drawCircle(color = primaryColor, radius = 16f, center = Offset(width * 0.9f, height * 0.2f))
                    drawCircle(color = surfaceColor, radius = 8f, center = Offset(width * 0.9f, height * 0.2f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCard(title = "TOTAL DISTANCE", value = displayDistance, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(14.dp))
            SummaryCard(title = "DURATION", value = displayDuration, modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("TRAFFIC BEHAVIOUR SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$finalScore/100", 
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), 
                    color = if (finalScore >= 80) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                Spacer(modifier = Modifier.height(14.dp))
                
                Text("Telemetry Score Breakdown:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Heading & Smoothness", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+5", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Distance Progress Reward", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+3", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                }
                if (finalScore < 90) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recorded Driving Infractions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-${100 - finalScore}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(text = "DONE (RETURN TO DASHBOARD)", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
