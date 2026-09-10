import re

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

# Need to replace the inline GlyphMatrixDirection and GLYPH constants with nothing, since they are in GlyphMatrix.kt now.
# They start from val GLYPH_TURN_LEFT to the end of the file.

target = """val GLYPH_TURN_LEFT = listOf("""
if target in content:
    index = content.find(target)
    content = content[:index]

# We need to change the IDLE_FACE call to use the state
# Actually we haven't updated AppState yet, let's just make it use state.idleGlyph
target_idle = """                            // Glyph Matrix - Idle Mode (Face)
                            GlyphMatrixDirection(
                                direction = "IDLE_FACE","""
replacement_idle = """                            // Glyph Matrix - Idle Mode (Customizable)
                            GlyphMatrixDirection(
                                direction = state.idleGlyph,"""
                                
if target_idle in content:
    content = content.replace(target_idle, replacement_idle)

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)

