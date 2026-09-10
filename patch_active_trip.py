with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('"Active Session"', '"ACTIVE SESSION"')
content = content.replace('"Search here"', '"SEARCH HERE"')
content = content.replace('"End Session"', '"END SESSION"')
content = content.replace('"Simulate Events"', '"SIMULATE EVENTS"')
content = content.replace('"ESP32-CAM Stream"', '"ESP32-CAM STREAM"')
content = content.replace('"Awaiting Hardware connection"', '"AWAITING HARDWARE CONNECTION"')
content = content.replace('"km/h"', '"KM/H"')

# Box with transparent background and white border for camera stream
content = content.replace(
"""                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                )""",
"""                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                        .androidx.compose.foundation.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                )""")

content = content.replace(
"""fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    )""",
"""fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    )""")

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)

