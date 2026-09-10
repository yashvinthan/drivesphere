with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

# Replace startDestination
content = content.replace('startDestination = "welcome",', 'startDestination = "onboarding",')

# Add composable("onboarding") before composable("welcome")
import re
new_composable = """
                    composable("onboarding") {
                        OnboardingScreen(onFinish = {
                            navController.navigate("welcome") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        })
                    }
                    composable("welcome")
"""
content = re.sub(r'composable\("welcome"\)', new_composable.strip(), content, count=1)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
