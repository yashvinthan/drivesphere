with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "return // Block standard navigation while interrupted" in line:
        pass # delete this line entirely
    elif "if (state.activeEvent != TripEvent.NONE) {" in line:
        new_lines.append(line)
    elif "    Scaffold(" in line:
        new_lines.append("    } else {\n")
        new_lines.append(line)
    elif "composable(\"guardian_alert\")" in line:
        # Before composable guardian_alert, nothing special.
        new_lines.append(line)
    else:
        new_lines.append(line)

# Add the closing brace for the else block right after the NavHost block
# It should be around line 220. Let's find NavHost closing
import re
content = "".join(new_lines)
content = re.sub(r'(\n\s*\}\n\s*\}\n\s*\})', r'\1\n    }\n', content, count=1)

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
