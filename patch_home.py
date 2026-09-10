import re

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'r') as f:
    content = f.read()

# Replace HomeDashboard signature to add onToggleCharging
sig_target = """@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit
) {"""

sig_replacement = """@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {}
) {"""

content = content.replace(sig_target, sig_replacement)

# Create AnimatedChargingGlyph
animated_glyph_comp = """
@Composable
fun AnimatedChargingGlyph(modifier: Modifier = Modifier) {
    var step by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(500)
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
fun BatteryTrendWidget() {
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("BATTERY CONSUMPTION TREND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dataPoints = listOf(100f, 95f, 92f, 85f, 70f, 65f, 60f, 50f)
                val width = size.width
                val height = size.height
                
                val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)
                val path = Path()
                
                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    // scale so 50-100 fills the height
                    val normalizedY = ((value - 40f) / 60f).coerceIn(0f, 1f)
                    val y = height - (normalizedY * height)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    drawCircle(color = Color.White, radius = 6f, center = Offset(x, y))
                }
                
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
"""

if "fun AnimatedChargingGlyph(" not in content:
    content = content.replace("@Composable\nfun WeeklyGraphCard()", animated_glyph_comp + "\n@Composable\nfun WeeklyGraphCard()")


# Replace the weather and battery row to handle charging
widget_row_target = """                GlyphWidgetCard(
                    modifier = Modifier.weight(1f),
                    title = "BATTERY",
                    value = "85% EST",
                    glyph = "BATTERY_FULL",
                    color = MaterialTheme.colorScheme.secondary
                )"""
                
widget_row_replacement = """                Card(
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
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("BATTERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (state.isCharging) "CHARGING" else "85% EST", style = MaterialTheme.typography.titleMedium, color = if (state.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }"""
                
content = content.replace(widget_row_target, widget_row_replacement)

# Add BatteryTrendWidget to the UI before Quick Actions
trend_target = """        PulsingEnter(visible = isVisible, delayMillis = 400) {
            Column {"""
            
trend_replacement = """        PulsingEnter(visible = isVisible, delayMillis = 375) {
            BatteryTrendWidget()
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 400) {
            Column {"""
            
content = content.replace(trend_target, trend_replacement)

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'w') as f:
    f.write(content)

