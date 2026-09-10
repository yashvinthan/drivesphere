import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

target = """                    composable("guardian_alert") {
                        GuardianAlertPreviewScreen(
                            onReturn = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }"""
                    
replacement = """                    composable("guardian_alert") {
                        GuardianAlertPreviewScreen(
                            onReturn = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
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
