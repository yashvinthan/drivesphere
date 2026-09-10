import re

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'r') as f:
    content = f.read()

target = """@Composable
fun GuardianHubScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Guardian Hub", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Device Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Wi-Fi Status", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Connected", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Camera Stream", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ready", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("OLED Display Preview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = OLEDBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(120.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("128x64 MONOCHROME OLED\\nReady for input", color = OLEDWhite, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}"""

replacement = """@Composable
fun GuardianHubScreen(state: com.example.viewmodel.AppState, onSetGlyph: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("GUARDIAN HUB", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("DEVICE CONNECTION", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("WI-FI STATUS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text("CONNECTED", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CAMERA STREAM", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text("READY", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("AVAILABLE GLYPHS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        val glyphs = listOf(
            Pair("MAC", "mac"),
            Pair("ARCO", "arco"),
            Pair("PC", "pc"),
            Pair("CUP", "cup"),
            Pair("ROCKET", "rocket"),
            Pair("IDLE_FACE", "face")
        )
        
        glyphs.forEach { (glyphId, glyphName) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (state.idleGlyph == glyphId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onSetGlyph(glyphId) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        GlyphMatrixDirection(direction = glyphId, modifier = Modifier.fillMaxSize())
                    }
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    Column {
                        Text(glyphName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (state.idleGlyph == glyphId) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("SELECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Text("NOT SELECTED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("TARGET NOT FOUND! Fallback to regex or raw replace.")

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'w') as f:
    f.write(content)

