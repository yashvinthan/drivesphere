import re

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

target = """                // Monochrome OLED Preview Component
                Card(
                    colors = CardDefaults.cardColors(containerColor = OLEDBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "OLED PREVIEW (128x64)", style = MaterialTheme.typography.labelSmall, color = OLEDWhite.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "← TURN LEFT", style = MaterialTheme.typography.bodyLarge, color = OLEDWhite)
                        Text(text = "120 m", style = MaterialTheme.typography.titleLarge, color = OLEDWhite)
                        Text(text = "${currentSpeed.toInt()} km/h • Score ${state.summary.score}", style = MaterialTheme.typography.bodyMedium, color = OLEDWhite)
                    }
                }"""

replacement = """                // Glyph Matrix Interface (Nothing Phone Style)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Glyph Matrix
                        GlyphMatrixDirection(
                            direction = "TURN_LEFT",
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                        )
                        
                        Spacer(modifier = Modifier.width(32.dp))
                        
                        // Text info
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "TURN LEFT", 
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                                    fontWeight = FontWeight.Bold
                                ), 
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "120 m", 
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                                    fontWeight = FontWeight.Bold
                                ), 
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "GLYPH MATRIX ACTIVE", 
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ), 
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("TARGET NOT FOUND! Please check the regex.")

helpers = """
val GLYPH_TURN_LEFT = listOf(
    "    ***       ",
    "   *****      ",
    "  *******     ",
    " *********    ",
    "    ***       ",
    "    ***       ",
    "    ***       ",
    "    ********  ",
    "         ***  ",
    "         ***  ",
    "         ***  ",
    "              "
)

val GLYPH_TURN_RIGHT = listOf(
    "       ***    ",
    "      *****   ",
    "     *******  ",
    "    ********* ",
    "       ***    ",
    "       ***    ",
    "       ***    ",
    "  ********    ",
    "  ***         ",
    "  ***         ",
    "  ***         ",
    "              "
)

val GLYPH_STRAIGHT = listOf(
    "      ***     ",
    "     *****    ",
    "    *******   ",
    "   *********  ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "              "
)

@Composable
fun GlyphMatrixDirection(direction: String, modifier: Modifier = Modifier) {
    val pattern = remember(direction) {
        when (direction) {
            "TURN_LEFT" -> GLYPH_TURN_LEFT
            "TURN_RIGHT" -> GLYPH_TURN_RIGHT
            "STRAIGHT" -> GLYPH_STRAIGHT
            else -> GLYPH_STRAIGHT
        }
    }
    
    val activePixels = remember(pattern) {
        val pixels = mutableSetOf<Pair<Int, Int>>()
        pattern.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                if (char != ' ') pixels.add(Pair(x, y))
            }
        }
        pixels
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val rows = pattern.size
        val columns = pattern.maxOfOrNull { it.length } ?: rows
        
        val padding = 4.dp.toPx()
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val pixelSize = minOf(cellWidth, cellHeight) - padding

        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val isActive = activePixels.contains(Pair(x, y))
                val color = if (isActive) Color.White else Color(0xFF151515)
                
                val offsetX = x * cellWidth + (cellWidth - pixelSize) / 2
                val offsetY = y * cellHeight + (cellHeight - pixelSize) / 2
                
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(offsetX, offsetY),
                    size = androidx.compose.ui.geometry.Size(pixelSize, pixelSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pixelSize / 4, pixelSize / 4)
                )
            }
        }
    }
}
"""

content = content + "\n" + helpers

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)
