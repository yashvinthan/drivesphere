import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""    val isCharging: Boolean = false,
    val hapticSync: Boolean = true
)""",
"""    val isCharging: Boolean = false,
    val hapticSync: Boolean = true,
    val driveCoins: Int = 1250
)""")

content = content.replace(
"""    fun setHapticSync(enabled: Boolean) {
        _uiState.update { it.copy(hapticSync = enabled) }
    }""",
"""    fun setHapticSync(enabled: Boolean) {
        _uiState.update { it.copy(hapticSync = enabled) }
    }
    
    fun toggleHubStatus() {
        _uiState.update { it.copy(hubStatus = if (it.hubStatus == "Disconnected") "Connected" else "Disconnected") }
    }""")

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)
