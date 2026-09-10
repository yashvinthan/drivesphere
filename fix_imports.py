with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'r') as f:
    content = f.read()

if "import androidx.compose.foundation.clickable" not in content:
    content = content.replace("import androidx.compose.foundation.background", "import androidx.compose.foundation.background\\nimport androidx.compose.foundation.clickable")

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'w') as f:
    f.write(content)
