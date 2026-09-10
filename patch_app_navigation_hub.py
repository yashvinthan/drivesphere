import re

with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""                    composable("hub") {
                        GuardianHubScreen()
                    }""",
"""                    composable("hub") {
                        GuardianHubScreen(
                            state = state,
                            onSetGlyph = { viewModel.setIdleGlyph(it) }
                        )
                    }""")

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
