content = """package com.example

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.DatabaseViewModel
import com.example.viewmodel.TripEvent

@Composable
fun DriveSphereApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    val dbViewModel: DatabaseViewModel = viewModel()
    val userProfile by dbViewModel.userProfile.collectAsState()
    val pastTrips by dbViewModel.pastTrips.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = state.isDarkMode ?: systemDark

    MyApplicationTheme(darkTheme = isDark) {
        // Interrupt Flow for Safety Events
        if (state.activeEvent != TripEvent.NONE) {
            if (state.activeEvent == TripEvent.FALL) {
                FallSOSScreen(
                    onCancel = { viewModel.resolveEvent() },
                    onCountdownComplete = { navController.navigate("guardian_alert"); viewModel.resolveEvent() }
                )
            } else {
                RiskAlertScreen(
                    state = state,
                    onResolve = { viewModel.resolveEvent() }
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    if (currentRoute in listOf("home", "navigate", "safety", "rewards", "hub")) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            val scaleHome by animateFloatAsState(
                                targetValue = if (currentRoute == "home") 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scaleHome"
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.scale(scaleHome)) },
                                label = { Text("Home") },
                                selected = currentRoute == "home",
                                onClick = { navController.navigate("home") { launchSingleTop = true; popUpTo("home") } },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedIconColor = MaterialTheme.colorScheme.primary)
                            )

                            val scaleNavigate by animateFloatAsState(
                                targetValue = if (currentRoute == "navigate") 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scaleNavigate"
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Place, contentDescription = "Navigate", modifier = Modifier.scale(scaleNavigate)) },
                                label = { Text("Navigate") },
                                selected = currentRoute == "navigate",
                                onClick = { navController.navigate("navigate") { launchSingleTop = true; popUpTo("home") } },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedIconColor = MaterialTheme.colorScheme.primary)
                            )

                            val scaleSafety by animateFloatAsState(
                                targetValue = if (currentRoute == "safety") 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scaleSafety"
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Security, contentDescription = "Safety", modifier = Modifier.scale(scaleSafety)) },
                                label = { Text("Safety") },
                                selected = currentRoute == "safety",
                                onClick = { navController.navigate("safety") { launchSingleTop = true; popUpTo("home") } },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedIconColor = MaterialTheme.colorScheme.primary)
                            )

                            val scaleRewards by animateFloatAsState(
                                targetValue = if (currentRoute == "rewards") 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scaleRewards"
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Star, contentDescription = "Rewards", modifier = Modifier.scale(scaleRewards)) },
                                label = { Text("Rewards") },
                                selected = currentRoute == "rewards",
                                onClick = { navController.navigate("rewards") { launchSingleTop = true; popUpTo("home") } },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedIconColor = MaterialTheme.colorScheme.primary)
                            )

                            val scaleHub by animateFloatAsState(
                                targetValue = if (currentRoute == "hub") 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scaleHub"
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Hub", modifier = Modifier.scale(scaleHub)) },
                                label = { Text("Hub") },
                                selected = currentRoute == "hub",
                                onClick = { navController.navigate("hub") { launchSingleTop = true; popUpTo("home") } },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedIconColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "welcome",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("welcome") {
                        WelcomeScreen(onBeginDemo = {
                            navController.navigate("home") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        })
                    }
                    composable("home") {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        HomeDashboard(
                            state = state,
                            onStartTrip = {
                                viewModel.startTrip()
                                navController.navigate("navigate") {
                                    popUpTo("home")
                                }
                            },
                            onNavigateToHub = { navController.navigate("hub") },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onSOS = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("sms:")
                                    putExtra("address", "911")
                                    putExtra("sms_body", "EMERGENCY: I need help. My last known location is: 13.0827 N, 80.2707 E")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    composable("navigate") {
                        ActiveTripScreen(
                            state = state,
                            onSimulateEvent = { viewModel.triggerEvent(it) },
                            onEndTrip = {
                                viewModel.endTrip()
                                navController.navigate("summary")
                            }
                        )
                    }
                    composable("safety") {
                        PrivacyProfileScreen(
                            profile = userProfile,
                            onEditProfile = { navController.navigate("profile_edit") },
                            onViewHistory = { navController.navigate("trip_history") }
                        )
                    }
                    composable("profile_edit") {
                        UserProfileScreen(
                            profile = userProfile,
                            onSave = { name, contactName, contactPhone ->
                                dbViewModel.saveProfile(name, contactName, contactPhone)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("trip_history") {
                        TripHistoryScreen(
                            trips = pastTrips,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("rewards") {
                        RewardsScreen(state = state)
                    }
                    composable("hub") {
                        GuardianHubScreen()
                    }
                    composable("summary") {
                        TripSummaryScreen(
                            onComplete = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("guardian_alert") {
                        GuardianAlertPreviewScreen(
                            onReturn = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
"""
with open('app/src/main/java/com/example/AppNavigation.kt', 'w') as f:
    f.write(content)
