with open('app/src/main/java/com/example/AppNavigation.kt', 'r') as f:
    content = f.read()

content = content.replace('label = { Text("Home") }', 'label = { Text("HOME") }')
content = content.replace('label = { Text("Navigate") }', 'label = { Text("NAVIGATE") }')
content = content.replace('label = { Text("Safety") }', 'label = { Text("SAFETY") }')
content = content.replace('label = { Text("Rewards") }', 'label = { Text("REWARDS") }')
content = content.replace('label = { Text("Hub") }', 'label = { Text("HUB") }')

with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)

