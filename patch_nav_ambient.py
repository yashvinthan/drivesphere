import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

target = """                        HomeDashboard(
                            state = state,
                            onToggleCharging = { viewModel.toggleCharging() },
                            onToggleHubStatus = { viewModel.toggleHubStatus() },
                            onStartTrip = {"""
                            
replacement = """                        HomeDashboard(
                            state = state,
                            onToggleCharging = { viewModel.toggleCharging() },
                            onToggleHubStatus = { viewModel.toggleHubStatus() },
                            onSetNightGlow = { viewModel.setNightGlowMode(it) },
                            onStartTrip = {"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
