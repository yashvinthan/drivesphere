import re

files = [
    'app/src/main/java/com/example/ui/screens/HomeDashboard.kt',
    'app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt'
]

for file_name in files:
    with open(file_name, 'r') as f:
        content = f.read()

    # Fix bad inline calls
    content = content.replace('.androidx.compose.foundation.border', '.border')
    content = content.replace('border = androidx.compose.foundation.BorderStroke', 'border = androidx.compose.foundation.BorderStroke')
    
    # We might need to add imports for border and BorderStroke at the top if they are missing
    if "import androidx.compose.foundation.border" not in content:
        content = content.replace('import androidx.compose.foundation.background', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.BorderStroke')

    with open(file_name, 'w') as f:
        f.write(content)

