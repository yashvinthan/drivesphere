import re

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'r') as f:
    content = f.read()

# Add imports for animations
if "import androidx.compose.animation.core.rememberInfiniteTransition" not in content:
    content = content.replace("import androidx.compose.animation.core.tween", "import androidx.compose.animation.core.*\nimport androidx.compose.animation.core.tween")

# Add the widget composables at the end of the file
new_widgets = """
@Composable
fun DriveCoinsWidget(coins: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DRIVECOINS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = "Coins", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%04d", coins), 
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), 
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
fun GuardianHubStatusWidget(status: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val isConnected = status.equals("Connected", ignoreCase = true)
    
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 0.3f else 0.0f,
        targetValue = if (isConnected) 1.0f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier.clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GUARDIAN HUB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha) else Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.uppercase(), 
                    style = MaterialTheme.typography.titleMedium, 
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
"""

if "fun DriveCoinsWidget(" not in content:
    content = content + "\n" + new_widgets

# Insert into the layout, just after the Greeting and before the Weather/Battery widgets
# Let's find:
#         PulsingEnter(visible = isVisible, delayMillis = 50) {
#             Column {
#                 Text("TODAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
#                 Text("HI, ${state.userName.uppercase()}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
#             }
#         }
#         
#         Spacer(modifier = Modifier.height(24.dp))

target_insert = """        PulsingEnter(visible = isVisible, delayMillis = 50) {
            Column {
                Text("TODAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("HI, ${state.userName.uppercase()}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))"""

replacement_insert = """        PulsingEnter(visible = isVisible, delayMillis = 50) {
            Column {
                Text("TODAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("HI, ${state.userName.uppercase()}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PulsingEnter(visible = isVisible, delayMillis = 75) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DriveCoinsWidget(
                    coins = state.driveCoins,
                    modifier = Modifier.weight(1f)
                )
                GuardianHubStatusWidget(
                    status = state.hubStatus,
                    onToggle = onNavigateToHub, // Or toggle here, let's use a new callback onToggleHubStatus
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))"""

if "DriveCoinsWidget(" not in content:
    content = content.replace(target_insert, replacement_insert)

# Need to update HomeDashboard signature and usages to support onToggleHubStatus
sig_target = """fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {}
) {"""

sig_replacement = """fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {},
    onToggleHubStatus: () -> Unit = {}
) {"""

content = content.replace(sig_target, sig_replacement)

# Use onToggleHubStatus
content = content.replace("onToggle = onNavigateToHub, // Or toggle here, let's use a new callback onToggleHubStatus", "onToggle = onToggleHubStatus,")

# Also, there's a button in the Traffic Behaviour Score card:
# Text("HUB STATUS: ${state.hubStatus.uppercase()}"
# Let's change that to just navigate to hub, maybe "OPEN GUARDIAN HUB" instead since we have a dedicated widget for status now.
content = content.replace('Text("HUB STATUS: ${state.hubStatus.uppercase()}",', 'Text("OPEN GUARDIAN HUB",')


with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'w') as f:
    f.write(content)
