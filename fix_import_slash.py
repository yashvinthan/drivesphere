with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'r') as f:
    content = f.read()

content = content.replace("background\\nimport", "background\nimport")

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'w') as f:
    f.write(content)
