import re

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'r') as f:
    content = f.read()

# Update ActiveTripScreen signature to include onAOD
sig_target = """@Composable
fun ActiveTripScreen(
    state: AppState,
    onSimulateEvent: (TripEvent) -> Unit,
    onEndTrip: () -> Unit
) {"""

sig_replacement = """@Composable
fun ActiveTripScreen(
    state: AppState,
    onSimulateEvent: (TripEvent) -> Unit,
    onEndTrip: () -> Unit,
    onAOD: () -> Unit = {}
) {"""

content = content.replace(sig_target, sig_replacement)

# Add AOD button to the Active Trip bottom sheet
btn_target = """                        Button(
                            onClick = onEndTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("END TRIP", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelLarge)
                        }"""
                        
btn_replacement = """                        Button(
                            onClick = onEndTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("END TRIP", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelLarge)
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Button(
                            onClick = onAOD,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("AOD MODE", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        }"""
                        
content = content.replace(btn_target, btn_replacement)

with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)
