import re

with open('app/src/main/java/com/example/ui/screens/GlyphMatrix.kt', 'r') as f:
    content = f.read()

new_glyphs = """
val GLYPH_BATTERY_EMPTY = listOf(
    "              ",
    "              ",
    "  **********  ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  **********  ",
    "              ",
    "              ",
    "              "
)
"""

if "GLYPH_BATTERY_EMPTY" not in content:
    content = content.replace("val GLYPH_BATTERY_HALF", new_glyphs + "\nval GLYPH_BATTERY_HALF")
    
content = content.replace('"BATTERY_HALF" -> GLYPH_BATTERY_HALF', '"BATTERY_HALF" -> GLYPH_BATTERY_HALF\n            "BATTERY_EMPTY" -> GLYPH_BATTERY_EMPTY')

with open('app/src/main/java/com/example/ui/screens/GlyphMatrix.kt', 'w') as f:
    f.write(content)
