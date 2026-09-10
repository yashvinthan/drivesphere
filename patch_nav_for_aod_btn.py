import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

target = """                    composable("navigate") {
                        ActiveTripScreen(
                            state = state,
                            onSimulateEvent = { viewModel.triggerEvent(it) },
                            onEndTrip = {
                                viewModel.endTrip()
                                navController.navigate("summary")
                            }
                        )
                    }"""

replacement = """                    composable("navigate") {
                        ActiveTripScreen(
                            state = state,
                            onSimulateEvent = { viewModel.triggerEvent(it) },
                            onEndTrip = {
                                viewModel.endTrip()
                                navController.navigate("summary")
                            },
                            onAOD = { navController.navigate("aod") }
                        )
                    }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
