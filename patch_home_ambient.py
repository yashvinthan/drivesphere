import re

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'r') as f:
    content = f.read()

imports = """
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
"""
if "import androidx.compose.ui.draw.shadow" not in content:
    content = content.replace("import androidx.compose.ui.draw.clip", "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.shadow")

sig_target = """fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {},
    onToggleHubStatus: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100) // slight delay for pulsing effect
        isVisible = true
    }"""

sig_replacement = """fun HomeDashboard(
    state: AppState,
    onStartTrip: () -> Unit,
    onNavigateToHub: () -> Unit,
    onToggleTheme: () -> Unit,
    onSOS: () -> Unit,
    onToggleCharging: () -> Unit = {},
    onToggleHubStatus: () -> Unit = {},
    onSetNightGlow: (Boolean) -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(false) }
    val ambientLux by rememberAmbientLightLevel()
    
    LaunchedEffect(ambientLux) {
        // Toggle night glow mode if ambient light is low (lux < 20)
        onSetNightGlow(ambientLux < 20f)
    }

    LaunchedEffect(Unit) {
        delay(100) // slight delay for pulsing effect
        isVisible = true
    }"""

content = content.replace(sig_target, sig_replacement)

# We want to change the primary color on the dashboard to glow if in night mode.
# Glow can be represented by pure white text with a shadow. Or simply mapping `primary` to pure white if night mode.
# Let's adjust the dashboard background and colors inside HomeDashboard.

bg_target = """    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {"""

bg_replacement = """    val scrollState = rememberScrollState()
    // High contrast glow colors
    val bgColor = if (state.isNightGlowMode) Color.Black else MaterialTheme.colorScheme.background
    val primaryTextColor = if (state.isNightGlowMode) Color.White else MaterialTheme.colorScheme.primary
    val glowModifier = if (state.isNightGlowMode) Modifier.shadow(16.dp, ambientColor = Color.White, spotColor = Color.White) else Modifier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {"""

content = content.replace(bg_target, bg_replacement)

# Update DRIVESPHERE text to use primaryTextColor and glow
title_target = """                Text(
                    "DRIVESPHERE",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )"""

title_replacement = """                Text(
                    "DRIVESPHERE",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = if (state.isNightGlowMode) androidx.compose.ui.graphics.Shadow(color = Color.White, blurRadius = 8f) else null
                    ),
                    color = primaryTextColor
                )"""

content = content.replace(title_target, title_replacement)

with open('app/src/main/java/com/example/ui/screens/HomeDashboard.kt', 'w') as f:
    f.write(content)
