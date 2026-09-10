import re

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight", "Icons.Default.KeyboardArrowRight")

if "import androidx.compose.material.icons.filled.KeyboardArrowRight" not in content:
    content = content.replace("import androidx.compose.material.icons.Icons", "import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.KeyboardArrowRight")

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'w') as f:
    f.write(content)
