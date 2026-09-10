import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

# Add AOD route to Navigation
target = """                    composable("profile") {
                        PrivacyProfileScreen(
                            profile = null,
                            onEditProfile = {},
                            onViewHistory = {}
                        )
                    }"""
                    
replacement = """                    composable("profile") {
                        PrivacyProfileScreen(
                            profile = null,
                            onEditProfile = {},
                            onViewHistory = {}
                        )
                    }
                    composable("aod") {
                        com.example.ui.screens.AlwaysOnDisplayScreen(
                            state = state,
                            onWake = { navController.popBackStack() }
                        )
                    }"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Navigation target not found")

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
