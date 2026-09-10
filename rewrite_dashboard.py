content = """package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppState

@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DriveSphere",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        Icons.Default.Brightness4,
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Text("Today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Hi, ${state.userName}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)

        Spacer(modifier = Modifier.height(24.dp))

        // SOS Button
        Button(
            onClick = onSOS,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Spacer(modifier = Modifier.width(12.dp))
            Text("SOS EMERGENCY", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Real-Time Hazard Alert
        if (state.activeHazard != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Hazard", tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        state.activeHazard,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Search Bar
        var searchQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            placeholder = { Text("Where to?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { 
                Box(
                    modifier = Modifier.padding(end = 8.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary).clickable { onStartTrip() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = "Go", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large hero metric card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BEHAVIOUR SCORE", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("DEMO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))

                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = state.summary.score / 100f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "scoreProgress"
                )
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 12.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.summary.score}", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold))
                        Text("Good", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onNavigateToHub,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hub Status: ${state.hubStatus}", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Weekly Distance & Speed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Custom Canvas Chart integration
        WeeklyGraphCard()

        Spacer(modifier = Modifier.height(24.dp))

        // Weekly Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Route,
                title = "Total Distance",
                value = "124 km",
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Eco,
                title = "Avg Speed",
                value = "38 km/h",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Actions Grid
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CategoryCard(icon = Icons.Default.DirectionsCar, label = "Navigate", color = MaterialTheme.colorScheme.primary, onClick = onStartTrip)
            CategoryCard(icon = Icons.Default.Security, label = "Safety", color = MaterialTheme.colorScheme.secondary, onClick = {})
            CategoryCard(icon = Icons.Default.Star, label = "Rewards", color = MaterialTheme.colorScheme.tertiary, onClick = {})
            CategoryCard(icon = Icons.Default.History, label = "History", color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = {})
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Recent Activity", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        RecentTripItem("Campus Route", "14.2 km • Score: 92", MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        RecentTripItem("Library Drive", "5.4 km • Score: 100", MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(12.dp))
        RecentTripItem("Downtown Run", "8.1 km • Score: 85", MaterialTheme.colorScheme.tertiary)
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun WeeklyGraphCard() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dataPoints = listOf(20f, 40f, 35f, 60f, 50f, 80f, 65f) // Example distance points
                
                val width = size.width
                val height = size.height
                
                // Draw grid lines
                for (i in 0..4) {
                    val y = height - (i * height / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 2f
                    )
                }
                
                val stepX = width / (dataPoints.size - 1)
                
                // Draw Line Chart
                val path = Path()
                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value / 100f * height) // Scaling 100 as max value
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    // Draw node circles
                    drawCircle(color = secondaryColor, radius = 8f, center = Offset(x, y))
                }
                
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun CategoryCard(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecentTripItem(title: String, subtitle: String, iconColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.TripOrigin, contentDescription = null, tint = iconColor)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
"""
with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'w') as f:
    f.write(content)
