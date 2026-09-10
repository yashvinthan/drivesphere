import re

with open('app/src/main/java/com/example/ui/screens/HapticSettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.compose.material.icons.filled.Check", "Icons.Default.Check")

if "import androidx.compose.material.icons.filled.Check" not in content:
    content = content.replace("import androidx.compose.material.icons.automirrored.filled.ArrowBack", "import androidx.compose.material.icons.automirrored.filled.ArrowBack\nimport androidx.compose.material.icons.filled.Check")

with open('app/src/main/java/com/example/ui/screens/HapticSettingsScreen.kt', 'w') as f:
    f.write(content)
