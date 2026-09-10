import re

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'r') as f:
    content = f.read()

sig_target = """fun PrivacyProfileScreen(
    profile: com.example.data.local.UserProfileEntity?,
    onEditProfile: () -> Unit,
    onViewHistory: () -> Unit
) {"""

sig_replacement = """fun PrivacyProfileScreen(
    profile: com.example.data.local.UserProfileEntity?,
    onEditProfile: () -> Unit,
    onViewHistory: () -> Unit,
    onViewHaptics: () -> Unit = {}
) {"""

content = content.replace(sig_target, sig_replacement)

# We want to add a row to "DEVICE PREFERENCES" to map the haptic feedback
# Currently it has:
#         Text("DEVICE PREFERENCES", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
#         Spacer(modifier = Modifier.height(12.dp))
#         
#         Card(...) {
#             Column(modifier = Modifier.padding(24.dp)) {
#                 Row(...) {
#                     Text("GLYPH HAPTIC SYNC", ...)
#                     Switch(...)
#                 }
#                 Spacer(modifier = Modifier.height(8.dp))
#                 Text("Vibrate device to match Glyph light pulses for sensory feedback.", ...)
#             }
#         }

# Let's add a ProfileRow right after the Text description inside the same card, or as a new card.
target_haptic_ui = """                Text("Vibrate device to match Glyph light pulses for sensory feedback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }"""

replacement_haptic_ui = """                Text("Vibrate device to match Glyph light pulses for sensory feedback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onViewHaptics() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HAPTIC MAPPING", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Icon(androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight, contentDescription = "Edit mappings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }"""

content = content.replace(target_haptic_ui, replacement_haptic_ui)

with open('app/src/main/java/com/example/ui/screens/SecondaryScreens.kt', 'w') as f:
    f.write(content)
