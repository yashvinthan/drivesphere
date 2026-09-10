package com.example

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
import com.example.viewmodel.VehicleType

@Composable
fun DriveSphereApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    val dbViewModel: DatabaseViewModel = viewModel()
    val userProfile by dbViewModel.userProfile.collectAsState()
    val pastTrips by dbViewModel.pastTrips.collectAsState()
    val allChallans by dbViewModel.allChallans.collectAsState()
    val allVouchers by dbViewModel.allVouchers.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = state.isDarkMode ?: systemDark

    MyApplicationTheme(darkTheme = isDark) {
        // High-Priority Interrupt Flow for Safety & Accident Events
        if (state.activeEvent != TripEvent.NONE) {
            if (state.activeEvent == TripEvent.FALL || state.activeEvent == TripEvent.CRASH_IMPACT) {
                FallSOSScreen(
                    state = state,
                    onCancel = { viewModel.resolveEvent() },
                    onCountdownComplete = { 
                        navController.navigate("guardian_alert")
                        viewModel.resolveEvent() 
                    }
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
                                label = { Text("HOME") },
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
                                label = { Text("NAVIGATE") },
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
                                label = { Text("SAFETY") },
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
                                label = { Text("REWARDS") },
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
                                icon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = "Hub", modifier = Modifier.scale(scaleHub)) },
                                label = { Text("HUB") },
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
                    startDestination = "onboarding",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("onboarding") {
                        OnboardingScreen(onFinish = {
                            navController.navigate("welcome") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        })
                    }
                    composable("welcome") {
                        WelcomeScreen(onBeginJourney = {
                            navController.navigate("home") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        })
                    }
                    composable("home") {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        HomeDashboard(
                            state = state,
                            onToggleVehicleType = { viewModel.setVehicleType(it) },
                            onNavigateToChallan = { navController.navigate("echallan") },
                            onNavigateToSecurity = { navController.navigate("security") },
                            onNavigateToDashcam = { navController.navigate("dashcam") },
                            onToggleCharging = { viewModel.toggleCharging() },
                            onToggleHubStatus = { viewModel.toggleHubStatus() },
                            onSetNightGlow = { viewModel.setNightGlowMode(it) },
                            onStartTrip = {
                                viewModel.startTrip()
                                navController.navigate("navigate") {
                                    popUpTo("home")
                                }
                            },
                            onNavigateToHub = { navController.navigate("hub") },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onSOS = {
                                val isBike = state.vehicleType == VehicleType.TWO_WHEELER
                                val emergencyMessage = if (isBike) {
                                    "EMERGENCY: Rider ${state.userName} pressed DriveSphere SOS! Live GPS location: https://maps.google.com/?q=13.0827,80.2707"
                                } else {
                                    "EMERGENCY: Driver ${state.userName} triggered in-vehicle SOS! Live GPS location: https://maps.google.com/?q=13.0827,80.2707"
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("sms:")
                                    putExtra("address", "112;911")
                                    putExtra("sms_body", emergencyMessage)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    composable("navigate") {
                        ActiveTripScreen(
                            state = state,
                            onTriggerSensorEvent = { viewModel.triggerEvent(it) },
                            onUpdateMetrics = { deltaKm, speed -> viewModel.updateLiveTripMetrics(deltaKm, speed) },
                            onIncrementDuration = { viewModel.incrementTripDuration() },
                            onEndTrip = {
                                viewModel.endTrip { dist, dur, score ->
                                    dbViewModel.saveCompletedTrip(dist, dur, score)
                                    navController.navigate("summary")
                                }
                            },
                            onAOD = { navController.navigate("aod") },
                            onOpenDashcam = { navController.navigate("dashcam") }
                        )
                    }
                    composable("echallan") {
                        EChallanComplianceScreen(
                            vehicleRegNumber = userProfile?.vehicleRegNumber ?: state.vehicleRegNumber,
                            challans = allChallans,
                            onPayChallan = { challanId ->
                                dbViewModel.payChallan(challanId) {
                                    viewModel.addCoins(50)
                                }
                            },
                            onDisputeChallan = { challanId ->
                                dbViewModel.disputeChallan(challanId)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("security") {
                        VehicleSecurityScreen(
                            state = state,
                            onToggleGuard = { viewModel.toggleVehicleGuard() },
                            onTriggerTamperSensor = { viewModel.triggerTamperAlert() },
                            onDismissTamper = { viewModel.dismissTamperAlert() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("dashcam") {
                        AiDashcamScreen(
                            state = state,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("safety") {
                        PrivacyProfileScreen(
                            profile = userProfile,
                            onEditProfile = { navController.navigate("profile_edit") },
                            onViewHistory = { navController.navigate("trip_history") },
                            onViewChallans = { navController.navigate("echallan") },
                            onViewSecurity = { navController.navigate("security") },
                            onViewHaptics = { navController.navigate("haptic_settings") }
                        )
                    }
                    composable("haptic_settings") {
                        HapticSettingsScreen(
                            mappings = state.hapticMappings,
                            onUpdateMapping = { event, pattern -> viewModel.updateHapticMapping(event, pattern) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("profile_edit") {
                        UserProfileScreen(
                            profile = userProfile,
                            onSave = { name, cName, cPhone, vType, vReg, sName, sPhone, roll ->
                                dbViewModel.saveProfile(name, cName, cPhone, vType, vReg, sName, sPhone, roll)
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
                        RewardsScreen(
                            state = state,
                            vouchers = allVouchers,
                            onRedeemVoucher = { voucher ->
                                if (viewModel.deductCoins(voucher.costCoins)) {
                                    dbViewModel.redeemVoucher(voucher.id)
                                }
                            }
                        )
                    }
                    composable("hub") {
                        GuardianHubScreen(
                            state = state,
                            onSetGlyph = { viewModel.setIdleGlyph(it) },
                            onUpdateIp = { viewModel.setEsp32IpAddress(it) },
                            onSetTransport = { viewModel.setHardwareTransport(it) },
                            onConnectBluetooth = { addr, cb -> viewModel.connectBluetoothDevice(addr, cb) },
                            getPairedBtDevices = { viewModel.getPairedBluetoothDevices() },
                            onPingHardware = { callback -> viewModel.pingEsp32Hardware(callback) }
                        )
                    }
                    composable("summary") {
                        TripSummaryScreen(
                            distanceKm = state.lastTripDistanceKm,
                            durationMins = state.lastTripDurationMins,
                            finalScore = state.lastTripScore,
                            onComplete = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("guardian_alert") {
                        GuardianAlertPreviewScreen(
                            riderName = userProfile?.name ?: state.userName,
                            onReturn = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("aod") {
                        AlwaysOnDisplayScreen(
                            state = state,
                            onWake = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
