cat << 'INNER_EOF' > patch_nav.py
import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

replacement = """
            composable("home") {
                val context = androidx.compose.ui.platform.LocalContext.current
                HomeDashboard(
                    state = state,
                    onStartTrip = {
                        viewModel.startTrip()
                        navController.navigate("navigate") {
                            popUpTo("home")
                        }
                    },
                    onNavigateToHub = { navController.navigate("hub") },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSOS = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("sms:")
                            putExtra("address", "911")
                            putExtra("sms_body", "EMERGENCY: I need help. My last known location is: 13.0827 N, 80.2707 E")
                        }
                        context.startActivity(intent)
                    }
                )
            }
"""

content = re.sub(r'composable\("home"\)\s*\{.*?\n\s*\)', replacement.strip(), content, flags=re.DOTALL)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
INNER_EOF
python3 patch_nav.py
