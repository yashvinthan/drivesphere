import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

target = """    val isCharging: Boolean = false,
    val hapticSync: Boolean = true,
    val driveCoins: Int = 1250
)"""

replacement = """    val isCharging: Boolean = false,
    val hapticSync: Boolean = true,
    val driveCoins: Int = 1250,
    val isNightGlowMode: Boolean = false,
    val hapticMappings: Map<String, String> = mapOf(
        "Navigation Alert" to "Short Pulse",
        "Hazard Detected" to "Double Pulse",
        "Battery Low" to "Continuous"
    )
)"""

content = content.replace(target, replacement)

target2 = """    fun toggleHubStatus() {
        _uiState.update { it.copy(hubStatus = if (it.hubStatus == "Disconnected") "Connected" else "Disconnected") }
    }"""

replacement2 = """    fun toggleHubStatus() {
        _uiState.update { it.copy(hubStatus = if (it.hubStatus == "Disconnected") "Connected" else "Disconnected") }
    }
    
    fun setNightGlowMode(enabled: Boolean) {
        _uiState.update { it.copy(isNightGlowMode = enabled) }
    }
    
    fun updateHapticMapping(event: String, pattern: String) {
        _uiState.update { 
            val newMappings = it.hapticMappings.toMutableMap()
            newMappings[event] = pattern
            it.copy(hapticMappings = newMappings)
        }
    }"""

content = content.replace(target2, replacement2)

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)
