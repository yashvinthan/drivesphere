with open('app/src/main/java/com/example/ui/screens/OnboardingScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('"Traffic Behaviour Score"', '"TRAFFIC BEHAVIOUR SCORE"')
content = content.replace('"Guardian Hub Connectivity"', '"GUARDIAN HUB CONNECTIVITY"')
content = content.replace('"SOS & Hazard Alerts"', '"SOS & HAZARD ALERTS"')
content = content.replace('"DriveCoins"', '"DRIVECOINS"')
content = content.replace('"Skip"', '"SKIP"')
content = content.replace('"Get Started"', '"GET STARTED"')
content = content.replace('"Next"', '"NEXT"')

content = content.replace('RoundedCornerShape(16.dp)', 'RoundedCornerShape(24.dp)')

with open('app/src/main/java/com/example/ui/screens/OnboardingScreen.kt', 'w') as f:
    f.write(content)
