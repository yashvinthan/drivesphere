package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AppState
import com.example.viewmodel.TripEvent

@Composable
fun RiskAlertScreen(
    state: AppState,
    onResolve: () -> Unit
) {
    val (title, scoreChange, explanation, coaching, cta) = when (state.activeEvent) {
        TripEvent.OVERSPEED -> listOf(
            "Overspeed Event Detected", 
            "92 → 84", 
            "-8 points: high-speed duration in restricted zone", 
            "Slow down safely. Consistent safe riding helps your score recover and prevents e-Challan penalties.", 
            "I'm back to a safe speed"
        )
        TripEvent.DISTRACTION -> listOf(
            "Phone Distraction Detected", 
            "92 → 88", 
            "-4 points: mobile phone usage while moving", 
            "Keep your eyes strictly on the road. Mount phone securely on handlebar/dashboard.", 
            "I'm focused on the road"
        )
        TripEvent.HELMET_RISK -> listOf(
            "No Helmet Detected (Bike)", 
            "92 → 75", 
            "-17 points: critical two-wheeler safety violation", 
            "Always wear an ISI/DOT certified helmet to prevent fatal head trauma.", 
            "I've secured my helmet"
        )
        TripEvent.SEATBELT_RISK -> listOf(
            "Seatbelt Unfastened (Car)",
            "92 → 78",
            "-14 points: four-wheeler passenger safety non-compliance",
            "Buckle your three-point seatbelt before moving. Protects cabin occupants during sudden braking.",
            "I've buckled my seatbelt"
        )
        TripEvent.CRASH_IMPACT -> listOf(
            "High-G Impact Detected",
            "92 → 60",
            "-32 points: collision sensor threshold exceeded",
            "Vehicle stability alert active. Ensure all occupants are safe and inspect vehicle.",
            "I'm safe — acknowledge"
        )
        else -> listOf(
            "Traffic Risk Event", 
            "92 → 90", 
            "-2 points: minor infraction", 
            "Drive safely and maintain steady headway.", 
            "Resolved"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SAFETY ALERT (AI MONITOR)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Score impact: $scoreChange",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(28.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = coaching,
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onResolve,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text(text = cta, color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
