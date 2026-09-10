import re

with open('app/src/main/java/com/example/ui/theme/Type.kt', 'r') as f:
    content = f.read()

# Make sure we import Font
if "import androidx.compose.ui.text.font.Font" not in content:
    content = content.replace("import androidx.compose.ui.text.font.FontFamily", "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.Font\nimport com.example.R")

content = content.replace("val NothingFontFamily = FontFamily.Monospace", "val NothingFontFamily = FontFamily(Font(R.font.dot_matrix))")

# Apply to all typographies
content = content.replace("fontFamily = FontFamily.SansSerif,", "fontFamily = NothingFontFamily,")

with open('app/src/main/java/com/example/ui/theme/Type.kt', 'w') as f:
    f.write(content)
