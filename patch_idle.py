import re

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

target = """                        // Glyph Matrix
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
                        }"""

replacement = """                        if (isRouting) {
                            // Glyph Matrix - Navigation Mode
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
                                    text = "NAVIGATING", 
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ), 
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            // Glyph Matrix - Idle Mode (Face)
                            GlyphMatrixDirection(
                                direction = "IDLE_FACE",
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(32.dp))
                            
                            // Text info
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "128x64 MONOCHROME OLED", 
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ), 
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ready for input", 
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ), 
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("TARGET NOT FOUND!")

# Add IDLE_FACE glyph and update when condition
glyph_target = """val GLYPH_STRAIGHT = listOf(
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
)"""

glyph_replacement = glyph_target + """

val GLYPH_IDLE_FACE = listOf(
    "              ",
    "              ",
    "  ***    ***  ",
    " *****  ***** ",
    " *****  ***** ",
    "  ***    ***  ",
    "              ",
    "              ",
    "   *      *   ",
    "    ******    ",
    "              ",
    "              "
)"""

content = content.replace(glyph_target, glyph_replacement)

when_target = """            "TURN_LEFT" -> GLYPH_TURN_LEFT
            "TURN_RIGHT" -> GLYPH_TURN_RIGHT
            "STRAIGHT" -> GLYPH_STRAIGHT
            else -> GLYPH_STRAIGHT"""

when_replacement = """            "TURN_LEFT" -> GLYPH_TURN_LEFT
            "TURN_RIGHT" -> GLYPH_TURN_RIGHT
            "STRAIGHT" -> GLYPH_STRAIGHT
            "IDLE_FACE" -> GLYPH_IDLE_FACE
            else -> GLYPH_STRAIGHT"""

content = content.replace(when_target, when_replacement)

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)

