with open('app/src/main/java/com/example/ui/screens/WelcomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('"Welcome to DriveSphere"', '"WELCOME TO DRIVESPHERE"')
content = content.replace('"A safer-trip companion for college riders. Earn rewards and build safer habits."', '"A SAFER-TRIP COMPANION FOR COLLEGE RIDERS. EARN REWARDS AND BUILD SAFER HABITS."')
content = content.replace('"Continue"', '"CONTINUE"')
content = content.replace('RoundedCornerShape(16.dp)', 'RoundedCornerShape(24.dp)')

with open('app/src/main/java/com/example/ui/screens/WelcomeScreen.kt', 'w') as f:
    f.write(content)
