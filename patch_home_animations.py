import re

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'r') as f:
    content = f.read()

# Make sure we import animations and LaunchedEffect
imports_to_add = """
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
"""

if "import androidx.compose.animation.AnimatedVisibility" not in content:
    content = content.replace("import androidx.compose.foundation.Canvas", imports_to_add.strip() + "\nimport androidx.compose.foundation.Canvas")

# Add the new components
new_components = """
@Composable
fun PulsingEnter(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = delayMillis)) +
                scaleIn(initialScale = 0.95f, animationSpec = tween(durationMillis = 600, delayMillis = delayMillis)),
        exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300))
    ) {
        content()
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
                GlyphMatrixDirection(direction = glyph, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
"""
if "fun PulsingEnter(" not in content:
    content = content + "\n" + new_components

# Now we need to modify the HomeDashboard function to use `isVisible` and wrap components.
# Let's write a replacement for the `HomeDashboard` function

target_func = """@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit
) {"""

# We'll just replace the entire function body with our new animated logic
# Read the file up to HomeDashboard and after the end of HomeDashboard (before WeeklyGraphCard)

part1 = content.split("@Composable\nfun HomeDashboard(")[0]
part2 = content.split("@Composable\nfun WeeklyGraphCard()")[1]

new_home_dashboard = """@Composable
fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100) // slight delay for pulsing effect
        isVisible = true
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        PulsingEnter(visible = isVisible, delayMillis = 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DRIVESPHERE",
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
        }

        PulsingEnter(visible = isVisible, delayMillis = 50) {
            Column {
                Text("TODAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("HI, ${state.userName.uppercase()}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 100) {
            // Weather and Battery Widgets Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlyphWidgetCard(
                    modifier = Modifier.weight(1f),
                    title = "WEATHER",
                    value = "24°C CLEAR",
                    glyph = "SUN",
                    color = MaterialTheme.colorScheme.primary
                )
                GlyphWidgetCard(
                    modifier = Modifier.weight(1f),
                    title = "BATTERY",
                    value = "85% EST",
                    glyph = "BATTERY_FULL",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PulsingEnter(visible = isVisible, delayMillis = 150) {
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.activeHazard != null) {
            PulsingEnter(visible = isVisible, delayMillis = 200) {
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
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.activeHazard.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        PulsingEnter(visible = isVisible, delayMillis = 250) {
            // Main Score Card
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
                            modifier = Modifier.size(160.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 12.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.summary.score}", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold))
                            Text("GOOD", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = onNavigateToHub,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("HUB STATUS: ${state.hubStatus.uppercase()}", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 300) {
            Column {
                Text("WEEKLY DISTANCE & SPEED", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                // Custom Canvas Chart integration
                WeeklyGraphCard()
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 350) {
            // Weekly Stats Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Route,
                    title = "TOTAL DISTANCE",
                    value = "124 km",
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Eco,
                    title = "AVG SPEED",
                    value = "38 km/h",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 400) {
            Column {
                Text("QUICK ACTIONS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CategoryCard(icon = Icons.Default.DirectionsCar, label = "NAVIGATE", color = MaterialTheme.colorScheme.primary, onClick = onStartTrip)
                    CategoryCard(icon = Icons.Default.Security, label = "SAFETY", color = MaterialTheme.colorScheme.secondary, onClick = {})
                    CategoryCard(icon = Icons.Default.Star, label = "REWARDS", color = MaterialTheme.colorScheme.tertiary, onClick = {})
                    CategoryCard(icon = Icons.Default.History, label = "HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = {})
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 450) {
            Column {
                Text("RECENT ACTIVITY", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                RecentTripItem("CAMPUS ROUTE", "14.2 km • Score: 92", MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                RecentTripItem("LIBRARY DRIVE", "5.4 km • Score: 100", MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
                RecentTripItem("DOWNTOWN RUN", "8.1 km • Score: 85", MaterialTheme.colorScheme.tertiary)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
@Composable
fun WeeklyGraphCard()"""

final_content = part1 + new_home_dashboard + part2

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'w') as f:
    f.write(final_content)
