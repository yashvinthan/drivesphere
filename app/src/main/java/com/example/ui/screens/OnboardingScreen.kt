package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "TRAFFIC BEHAVIOUR SCORE",
        description = "Ride smarter, not harder. Track your riding habits in real-time and get AI-powered insights to improve your safety score.",
        icon = Icons.Default.Speed
    ),
    OnboardingPage(
        title = "GUARDIAN HUB CONNECTIVITY",
        description = "Seamlessly connect with your vehicle's ESP32-CAM and sensors for instant video streams and live hardware telemetry.",
        icon = Icons.Default.SettingsInputAntenna
    ),
    OnboardingPage(
        title = "SOS & HAZARD ALERTS",
        description = "We've got your back. Automatic fall detection, real-time hazard warnings, and one-tap emergency SOS ensure you're never alone.",
        icon = Icons.Default.Emergency
    ),
    OnboardingPage(
        title = "DRIVECOINS",
        description = "Safety pays off. Earn DriveCoins for every safe trip and redeem your rewards for exclusive campus perks.",
        icon = Icons.Default.Toll
    )
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header / Skip Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (currentPage < onboardingPages.size - 1) {
                TextButton(onClick = onFinish) {
                    Text("SKIP", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp)) // Maintain height
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Content
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300))
                } else {
                    slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300))
                }
            },
            label = "onboarding_animation"
        ) { pageIndex ->
            val page = onboardingPages[pageIndex]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Indicators & Next Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(onboardingPages.size) { index ->
                    val isSelected = index == currentPage
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isSelected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Next / Get Started Button
            Button(
                onClick = {
                    if (currentPage < onboardingPages.size - 1) {
                        currentPage++
                    } else {
                        onFinish()
                    }
                },
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (currentPage == onboardingPages.size - 1) "GET STARTED" else "NEXT",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
