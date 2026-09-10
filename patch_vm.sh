sed -i 's/val userName: String = "Alex"/val userName: String = "Alex",\n    val isDarkMode: Boolean? = null,\n    val activeHazard: String? = "🚧 Heavy rain expected in 2 miles. Reduce speed."/g' app/src/main/java/com/example/viewmodel/MainViewModel.kt

sed -i '/fun resolveEvent()/i \ \ \ \ fun toggleTheme() {\n        _uiState.update { it.copy(isDarkMode = if (it.isDarkMode == null) true else if (it.isDarkMode) false else null) }\n    }\n' app/src/main/java/com/example/viewmodel/MainViewModel.kt
