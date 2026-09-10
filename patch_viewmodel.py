import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""    val activeHazard: String? = "🚧 Heavy rain expected in 2 miles. Reduce speed.",
    val idleGlyph: String = "IDLE_FACE"
)""",
"""    val activeHazard: String? = "🚧 Heavy rain expected in 2 miles. Reduce speed.",
    val idleGlyph: String = "IDLE_FACE",
    val isCharging: Boolean = false,
    val hapticSync: Boolean = true
)""")

content = content.replace(
"""    fun setIdleGlyph(glyph: String) {
        _uiState.update { it.copy(idleGlyph = glyph) }
    }""",
"""    fun setIdleGlyph(glyph: String) {
        _uiState.update { it.copy(idleGlyph = glyph) }
    }
    
    fun toggleCharging() {
        _uiState.update { it.copy(isCharging = !it.isCharging) }
    }
    
    fun setHapticSync(enabled: Boolean) {
        _uiState.update { it.copy(hapticSync = enabled) }
    }""")

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)
