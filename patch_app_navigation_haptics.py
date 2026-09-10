import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

target = """                    composable("safety") {
                        PrivacyProfileScreen(
                            profile = userProfile,
                            onEditProfile = { navController.navigate("profile_edit") },
                            onViewHistory = { navController.navigate("trip_history") }
                        )
                    }"""

replacement = """                    composable("safety") {
                        PrivacyProfileScreen(
                            profile = userProfile,
                            onEditProfile = { navController.navigate("profile_edit") },
                            onViewHistory = { navController.navigate("trip_history") },
                            onViewHaptics = { navController.navigate("haptic_settings") }
                        )
                    }
                    composable("haptic_settings") {
                        com.example.ui.screens.HapticSettingsScreen(
                            mappings = state.hapticMappings,
                            onUpdateMapping = { event, pattern -> viewModel.updateHapticMapping(event, pattern) },
                            onBack = { navController.popBackStack() }
                        )
                    }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
