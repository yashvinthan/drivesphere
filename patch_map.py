with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

# 1. Add layers icon import
content = content.replace("import androidx.compose.material.icons.filled.Search", "import androidx.compose.material.icons.filled.Search\nimport androidx.compose.material.icons.filled.Layers")

# 2. Add mapType state
state_code = """    var isRouting by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()"""
content = content.replace("    var isRouting by remember { mutableStateOf(false) }\n    val coroutineScope = rememberCoroutineScope()", state_code)

# 3. Add toggle button
toggle_ui = """        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active Session", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            
            IconButton(
                onClick = { 
                    isSatelliteMode = !isSatelliteMode
                    mapViewRef?.setTileSource(if (isSatelliteMode) org.osmdroid.tileprovider.tilesource.TileSourceFactory.USGS_SAT else org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    mapViewRef?.invalidate()
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Toggle Map View",
                    tint = if (isSatelliteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))"""

content = content.replace("""        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Active Session", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))""", toggle_ui)

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)

