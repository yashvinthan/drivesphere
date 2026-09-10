with open('app/src/main/java/com/example/ui/theme/Type.kt', 'r') as f:
    content = f.read()

content = content.replace("package com.example.ui.theme", "package com.example.ui.theme\n\nimport androidx.compose.ui.text.font.Font\nimport com.example.R")

with open('app/src/main/java/com/example/ui/theme/Type.kt', 'w') as f:
    f.write(content)
