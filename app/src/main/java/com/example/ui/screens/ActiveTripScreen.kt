package com.example.ui.screens

import android.content.Context
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.viewmodel.AppState
import com.example.viewmodel.TripEvent
import com.example.viewmodel.VehicleType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

enum class TravelMode {
    CAR,
    BIKE
}

data class DestinationSuggestion(
    val title: String,
    val subtitle: String,
    val iconType: String,
    val query: String
)

data class RouteData(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<GeoPoint>,
    val maneuverType: String,
    val nextStreet: String,
    val destName: String,
    val isFastest: Boolean,
    val stepInstruction: String
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    state: AppState,
    onTriggerSensorEvent: (TripEvent) -> Unit,
    onUpdateMetrics: (distanceDeltaKm: Double, speedKmH: Float) -> Unit,
    onIncrementDuration: () -> Unit,
    onEndTrip: () -> Unit,
    onAOD: () -> Unit = {},
    onOpenDashcam: () -> Unit = {},
    onUpdateNavigation: (maneuver: String, distance: String, eta: String, street: String) -> Unit = { _, _, _, _ -> },
    onEndNavigation: () -> Unit = {}
) {
    val context = LocalContext.current
    
    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    val hasLocationPermission = locationPermissions.allPermissionsGranted
    
    var currentSpeed by remember { mutableFloatStateOf(0f) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var destinationQuery by remember { mutableStateOf("") }
    var isRouting by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(false) }

    // Navigation & Routing States
    var selectedTravelMode by remember { 
        mutableStateOf(if (state.vehicleType == VehicleType.TWO_WHEELER) TravelMode.BIKE else TravelMode.CAR) 
    }
    var calculatedRoutes by remember { mutableStateOf<List<RouteData>>(emptyList()) }
    var selectedRouteIndex by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var dynamicSuggestions by remember { mutableStateOf<List<DestinationSuggestion>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Live Turn-by-Turn Telemetry
    var activeNavManeuver by remember { mutableStateOf("STRAIGHT") }
    var activeNavDistance by remember { mutableStateOf("0 m") }
    var activeNavEta by remember { mutableStateOf("Active") }
    var activeNavStreet by remember { mutableStateOf("Trip Active") }
    var activeNavInstruction by remember { mutableStateOf("Follow highlighted route") }
    
    val coroutineScope = rememberCoroutineScope()

    // Smart Popular Destinations Suggestions
    val defaultSuggestions = remember {
        listOf(
            DestinationSuggestion("Chennai International Airport (MAA)", "GST Road, Meenambakkam", "airport", "Chennai International Airport"),
            DestinationSuggestion("Puratchi Thalaivar Dr. M.G.R Central Station", "Kannappar Thidal, Periyamet", "metro", "Chennai Central Railway Station"),
            DestinationSuggestion("Marina Beach Promenade", "Kamarajar Salai, Triplicane", "beach", "Marina Beach Chennai"),
            DestinationSuggestion("Tidel Park & Cyber IT City", "Rajiv Gandhi IT Expressway, Taramani", "tech", "Tidel Park Chennai"),
            DestinationSuggestion("Apollo Speciality Hospital 24x7", "Greams Road, Thousand Lights", "hospital", "Apollo Hospital Greams Road"),
            DestinationSuggestion("Indian Oil Supercharger & Fuel Station", "Anna Salai, Mount Road", "fuel", "Indian Oil Mount Road Chennai")
        )
    }

    // Sync navigation to OLED only when route guidance is actually engaged
    LaunchedEffect(isNavigating, activeNavManeuver, activeNavDistance, activeNavEta, activeNavStreet) {
        if (isNavigating) {
            onUpdateNavigation(activeNavManeuver, activeNavDistance, activeNavEta, activeNavStreet)
        }
    }

    // Dynamic search autocomplete via Nominatim
    LaunchedEffect(destinationQuery) {
        if (destinationQuery.length >= 2) {
            searchJob?.cancel()
            searchJob = launch(Dispatchers.IO) {
                delay(350) // debounce
                try {
                    val client = okhttp3.OkHttpClient()
                    val url = "https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(destinationQuery, "UTF-8")}&format=json&limit=5&addressdetails=1"
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "DriveSphereApp/2.0")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val arr = JSONArray(body)
                        val results = mutableListOf<DestinationSuggestion>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val disp = obj.optString("display_name", "")
                            val parts = disp.split(",")
                            val title = parts.getOrNull(0)?.trim() ?: destinationQuery
                            val subtitle = parts.drop(1).take(2).joinToString(",").trim()
                            results.add(
                                DestinationSuggestion(
                                    title = title,
                                    subtitle = if (subtitle.isNotBlank()) subtitle else "Selected Destination",
                                    iconType = "place",
                                    query = title
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            dynamicSuggestions = results
                        }
                    }
                } catch (_: Exception) { }
            }
        } else {
            dynamicSuggestions = emptyList()
        }
    }

    // Real-Time Trip Duration Stopwatch
    LaunchedEffect(state.isTripActive) {
        while (state.isTripActive) {
            delay(1000)
            onIncrementDuration()
        }
    }

    // Live GPS Updates for Distance Accumulation & Speedometer
    DisposableEffect(hasLocationPermission) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = LocationListener { location ->
            currentSpeed = location.speed * 3.6f
            val currentPt = GeoPoint(location.latitude, location.longitude)
            userLocation = currentPt

            if (isNavigating) {
                mapViewRef?.let { map ->
                    map.controller.animateTo(currentPt)
                }
            }

            previousLocation?.let { prev ->
                val distanceMeters = location.distanceTo(prev)
                if (distanceMeters in 2.0..500.0) {
                    val deltaKm = distanceMeters / 1000.0
                    onUpdateMetrics(deltaKm, currentSpeed)
                }
            }
            previousLocation = location
        }
        
        if (hasLocationPermission) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null) {
                    userLocation = GeoPoint(lastKnown.latitude, lastKnown.longitude)
                    previousLocation = lastKnown
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
        
        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // Function to calculate and render routes with alternatives and travel mode factor
    fun executeRouting(dest: String) {
        val startLoc = userLocation ?: GeoPoint(13.0827, 80.2707)
        mapViewRef?.let { map ->
            isRouting = true
            showSuggestions = false
            coroutineScope.launch {
                findAndDrawFastestRoute(
                    start = startLoc,
                    destinationName = dest,
                    mapView = map,
                    travelMode = selectedTravelMode
                ) { routes, chosenRoute ->
                    calculatedRoutes = routes
                    selectedRouteIndex = chosenRoute.index
                    activeNavManeuver = chosenRoute.maneuverType
                    activeNavDistance = formatDistance(chosenRoute.distanceMeters)
                    activeNavEta = formatEta(chosenRoute.durationSeconds, selectedTravelMode)
                    activeNavStreet = chosenRoute.nextStreet
                    activeNavInstruction = chosenRoute.stepInstruction
                    
                    onUpdateNavigation(activeNavManeuver, activeNavDistance, activeNavEta, activeNavStreet)
                }
                isRouting = false
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (isNavigating) 100.dp else 170.dp,
        sheetContainerColor = Color(0xFF141414),
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Sleek Google Maps Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color(0xFF555555), CircleShape)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isNavigating) "Live Trip Guidance" else "Active Trip Cockpit",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTravelMode == TravelMode.BIKE) "Two-Wheeler Navigation • Fast Agility" else "Four-Wheeler Navigation • Standard Flow",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Hardware Status Pill
                    Box(
                        modifier = Modifier
                            .background(
                                if (state.isEsp32Connected) Color(0xFF00E676).copy(alpha = 0.15f)
                                else Color(0xFF222222),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (state.isEsp32Connected) "ESP32 ALL-IN-ONE LINKED" else "GPS TELEMETRY ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isEsp32Connected) Color(0xFF00E676) else Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-Time Trip Metrics Row (Distance, Time, Score)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                String.format(Locale.getDefault(), "%.2f KM", state.activeTripDistanceKm),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TIME", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            val mins = state.activeTripDurationSeconds / 60
                            val secs = state.activeTripDurationSeconds % 60
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                "${state.summary.score}/100",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (state.summary.score >= 80) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Glyph Matrix OLED HUD & Live Turn-by-Turn Navigation Sync
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().height(145.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphMatrixDirection(
                            direction = activeNavManeuver,
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                        )
                        
                        Spacer(modifier = Modifier.width(18.dp))
                        
                        Column(verticalArrangement = Arrangement.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("OLED HUD SYNCED", color = Color(0xFF00E676), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(activeNavEta, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeNavDistance, 
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ), 
                                color = Color.White
                            )
                            Text(
                                text = activeNavStreet, 
                                style = MaterialTheme.typography.bodySmall, 
                                color = Color.LightGray,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                
                // Hardware Camera Monitor Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
                        .clickable { onOpenDashcam() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("ESP32-CAM AI FEED (TAP TO EXPAND)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "LIVE MJPEG / SNAPSHOT • 192.168.4.1", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Safety Sensor Test Controls
                Text(
                    text = if (state.vehicleType == VehicleType.TWO_WHEELER) "SAFETY SENSORS TEST BENCH (BIKE)" else "SAFETY SENSORS TEST BENCH (CAR)",
                    style = MaterialTheme.typography.titleSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorActionButton("Speed Sensor", { onTriggerSensorEvent(TripEvent.OVERSPEED) }, Modifier.weight(1f))
                    SensorActionButton("Phone Distraction", { onTriggerSensorEvent(TripEvent.DISTRACTION) }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.vehicleType == VehicleType.TWO_WHEELER) {
                        SensorActionButton("Helmet Sensor", { onTriggerSensorEvent(TripEvent.HELMET_RISK) }, Modifier.weight(1f))
                        SensorActionButton("Fall SOS Sensor", { onTriggerSensorEvent(TripEvent.FALL) }, Modifier.weight(1f))
                    } else {
                        SensorActionButton("Seatbelt Sensor", { onTriggerSensorEvent(TripEvent.SEATBELT_RISK) }, Modifier.weight(1f))
                        SensorActionButton("Crash Sensor", { onTriggerSensorEvent(TripEvent.CRASH_IMPACT) }, Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onEndTrip,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("END SESSION & SAVE TRIP", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Interactive OpenStreetMap
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(13.0827, 80.2707))
                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.onResume()
                    if (hasLocationPermission) {
                        val hasOverlay = mapView.overlays.any { it is MyLocationNewOverlay }
                        if (!hasOverlay) {
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                            locationOverlay.enableMyLocation()
                            locationOverlay.enableFollowLocation()
                            mapView.overlays.add(locationOverlay)
                            mapView.invalidate()
                        }
                    }
                }
            )

            // -------------------------------------------------------------
            // TOP FLOATING CONTROLS: GOOGLE MAPS STYLE FLOATING SEARCH BAR & PILLS
            // -------------------------------------------------------------
            if (!isNavigating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    // Google Maps Floating Search Capsule
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                        color = Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search, 
                                contentDescription = "Search", 
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(22.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(10.dp))

                            OutlinedTextField(
                                value = destinationQuery,
                                onValueChange = { 
                                    destinationQuery = it
                                    showSuggestions = it.isNotBlank()
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { 
                                    Text("Where to? Search destination...", color = Color(0xFF888888), fontSize = 14.sp) 
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )

                            if (destinationQuery.isNotBlank()) {
                                IconButton(onClick = { 
                                    destinationQuery = ""
                                    showSuggestions = false
                                    calculatedRoutes = emptyList()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray)
                                }
                            }

                            if (isRouting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp), 
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00E676)
                                )
                            } else {
                                Surface(
                                    onClick = { 
                                        if (destinationQuery.isNotBlank()) {
                                            executeRouting(destinationQuery)
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color(0xFF00E676),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Directions, 
                                            contentDescription = "Directions", 
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Google Maps Style Horizontal Category & Transport Mode Chips
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            GoogleMapsFilterChip(
                                icon = Icons.Default.TwoWheeler,
                                label = "Bike Mode",
                                isSelected = selectedTravelMode == TravelMode.BIKE,
                                onClick = {
                                    selectedTravelMode = TravelMode.BIKE
                                    if (destinationQuery.isNotBlank()) executeRouting(destinationQuery)
                                }
                            )
                        }
                        item {
                            GoogleMapsFilterChip(
                                icon = Icons.Default.DirectionsCar,
                                label = "Car Mode",
                                isSelected = selectedTravelMode == TravelMode.CAR,
                                onClick = {
                                    selectedTravelMode = TravelMode.CAR
                                    if (destinationQuery.isNotBlank()) executeRouting(destinationQuery)
                                }
                            )
                        }
                        item {
                            GoogleMapsFilterChip(
                                icon = Icons.Default.LocalGasStation,
                                label = "Fuel",
                                isSelected = false,
                                onClick = {
                                    destinationQuery = "Fuel Station"
                                    executeRouting("Fuel Station")
                                }
                            )
                        }
                        item {
                            GoogleMapsFilterChip(
                                icon = Icons.Default.LocalHospital,
                                label = "Hospital",
                                isSelected = false,
                                onClick = {
                                    destinationQuery = "Hospital"
                                    executeRouting("Hospital")
                                }
                            )
                        }
                        item {
                            GoogleMapsFilterChip(
                                icon = Icons.Default.LocalParking,
                                label = "Parking",
                                isSelected = false,
                                onClick = {
                                    destinationQuery = "Parking"
                                    executeRouting("Parking")
                                }
                            )
                        }
                    }

                    // Suggestions Dropdown Panel - ONLY visible when user is actively typing a destination
                    AnimatedVisibility(
                        visible = showSuggestions && destinationQuery.isNotBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(max = 280.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            elevation = CardDefaults.cardElevation(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp)
                            ) {
                                val suggestionsToShow = if (dynamicSuggestions.isNotEmpty()) {
                                    dynamicSuggestions
                                } else {
                                    defaultSuggestions.filter { 
                                        it.title.contains(destinationQuery, ignoreCase = true) ||
                                        it.subtitle.contains(destinationQuery, ignoreCase = true)
                                    }
                                }

                                Text(
                                    text = "SUGGESTED DESTINATIONS",
                                    color = Color(0xFF00E676),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )

                                suggestionsToShow.forEach { suggestion ->
                                    SuggestionRow(suggestion = suggestion) {
                                        destinationQuery = suggestion.query
                                        showSuggestions = false
                                        executeRouting(suggestion.query)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TURN-BY-TURN GUIDANCE TOP HUD BANNER (WHEN NAVIGATING)
            // -------------------------------------------------------------
            if (isNavigating) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF051B10)),
                    border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // High-Visibility Maneuver Icon
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF00E676), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (activeNavManeuver) {
                                        "TURN_LEFT" -> Icons.Default.TurnLeft
                                        "TURN_RIGHT" -> Icons.Default.TurnRight
                                        "UTURN" -> Icons.Default.Refresh
                                        "DESTINATION" -> Icons.Default.Place
                                        else -> Icons.Default.Straight
                                    },
                                    contentDescription = activeNavManeuver,
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeNavDistance,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = activeNavInstruction,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFB9F6CA),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Toward ${activeNavStreet}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = {
                                    isNavigating = false
                                    onEndNavigation()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Navigation", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color(0xFF0D3B23))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (selectedTravelMode == TravelMode.BIKE) "🏍️ BIKE FASTEST" else "🚗 CAR FASTEST",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF00E676)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "ETA: $activeNavEta",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }

                            Text(
                                "ESP32 OLED LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // BOTTOM ROUTE PREVIEW CARD (BEFORE PRESSING START NAVIGATION)
            // -------------------------------------------------------------
            if (calculatedRoutes.isNotEmpty() && !isNavigating) {
                val activeRoute = calculatedRoutes.getOrElse(selectedRouteIndex) { calculatedRoutes.first() }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                    border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                    elevation = CardDefaults.cardElevation(14.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF00E676), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "⚡ FASTEST ROUTE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                            color = Color.Black
                                        )
                                    }
                                    if (calculatedRoutes.size > 1) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${calculatedRoutes.size} alternative routes found",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatEta(activeRoute.durationSeconds, selectedTravelMode),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF00E676)
                                )
                                Text(
                                    text = "${formatDistance(activeRoute.distanceMeters)} • ${activeRoute.destName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }

                            // Start Navigation Button
                            Button(
                                onClick = {
                                    isNavigating = true
                                    mapViewRef?.let { map ->
                                        userLocation?.let { loc ->
                                            map.controller.setZoom(18.0)
                                            map.controller.animateTo(loc)
                                        }
                                    }
                                    onUpdateNavigation(activeNavManeuver, activeNavDistance, activeNavEta, activeNavStreet)
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "START", 
                                        color = Color.Black, 
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Google Maps Floating Action Controls (Positioned cleanly bottom-right above the sheet)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp, 
                        bottom = if (isNavigating) 115.dp else if (calculatedRoutes.isNotEmpty()) 180.dp else 185.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Map Layers Toggle FAB
                Surface(
                    onClick = { 
                        isSatelliteMode = !isSatelliteMode
                        mapViewRef?.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                        mapViewRef?.invalidate()
                    },
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers, 
                            contentDescription = "Toggle Map View",
                            tint = if (isSatelliteMode) Color(0xFF00E676) else Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Re-center My Location FAB
                Surface(
                    onClick = { 
                        userLocation?.let { loc ->
                            mapViewRef?.controller?.animateTo(loc)
                            mapViewRef?.controller?.setZoom(17.0)
                        }
                    },
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation, 
                            contentDescription = "My Location",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Google Maps Floating Speedometer Capsule
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141414),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentSpeed.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFF00E676)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KPH",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleMapsFilterChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF1E1E1E),
        border = BorderStroke(
            1.dp, 
            if (isSelected) Color(0xFF00E676) else Color(0xFF333333)
        ),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = label,
                tint = if (isSelected) Color(0xFF00E676) else Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) Color(0xFF00E676) else Color.White
            )
        }
    }
}

@Composable
fun SuggestionRow(
    suggestion: DestinationSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF282828), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (suggestion.iconType) {
                    "airport" -> Icons.Default.Flight
                    "metro" -> Icons.Default.Train
                    "hospital" -> Icons.Default.LocalHospital
                    "fuel" -> Icons.Default.LocalGasStation
                    "beach" -> Icons.Default.Place
                    else -> Icons.Default.Place
                },
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SensorActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF111111),
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

// Distance & ETA formatters
fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${meters.toInt()} m"
    }
}

fun formatEta(baseSeconds: Double, mode: TravelMode): String {
    val durationSeconds = if (mode == TravelMode.BIKE) baseSeconds * 0.78 else baseSeconds
    val mins = (durationSeconds / 60).toInt().coerceAtLeast(1)
    return if (mins >= 60) {
        val hours = mins / 60
        val remMins = mins % 60
        "${hours}h ${remMins}m"
    } else {
        "$mins min"
    }
}

// Multi-route evaluation and rendering
suspend fun findAndDrawFastestRoute(
    start: GeoPoint, 
    destinationName: String, 
    mapView: MapView,
    travelMode: TravelMode,
    onRouteReady: (routes: List<RouteData>, fastest: RouteData) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        if (destinationName.isBlank()) return@withContext
        val client = okhttp3.OkHttpClient()
        
        // 1. Geocode Destination via Nominatim
        val searchUrl = "https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(destinationName, "UTF-8")}&format=json&limit=1"
        val searchRequest = okhttp3.Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "DriveSphereApp/2.0")
            .build()
            
        val searchResponse = client.newCall(searchRequest).execute()
        val searchJsonStr = searchResponse.body?.string()
        
        if (!searchJsonStr.isNullOrEmpty()) {
            val jsonArray = JSONArray(searchJsonStr)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val destPoint = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
                val displayName = obj.optString("display_name", destinationName).split(",")[0].trim()
                
                // 2. Fetch Routes with alternatives=true via OSRM
                val routeUrl = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${destPoint.longitude},${destPoint.latitude}?overview=full&geometries=geojson&steps=true&alternatives=true"
                val routeRequest = okhttp3.Request.Builder().url(routeUrl).build()
                val routeResponse = client.newCall(routeRequest).execute()
                val routeJsonStr = routeResponse.body?.string()
                
                if (!routeJsonStr.isNullOrEmpty()) {
                    val json = JSONObject(routeJsonStr)
                    val routesArray = json.optJSONArray("routes")
                    if (routesArray != null && routesArray.length() > 0) {
                        val parsedRoutes = mutableListOf<RouteData>()
                        
                        var fastestIndex = 0
                        var minDuration = Double.MAX_VALUE

                        for (i in 0 until routesArray.length()) {
                            val routeObj = routesArray.getJSONObject(i)
                            val totalDistM = routeObj.optDouble("distance", 0.0)
                            val totalDurSec = routeObj.optDouble("duration", 0.0)

                            if (totalDurSec < minDuration) {
                                minDuration = totalDurSec
                                fastestIndex = i
                            }

                            var maneuverType = "STRAIGHT"
                            var nextStreet = displayName
                            var stepInstruction = "Head toward $displayName"

                            // Parse steps
                            val legs = routeObj.optJSONArray("legs")
                            if (legs != null && legs.length() > 0) {
                                val steps = legs.getJSONObject(0).optJSONArray("steps")
                                if (steps != null && steps.length() > 1) {
                                    val nextStep = steps.getJSONObject(1)
                                    val mObj = nextStep.optJSONObject("maneuver")
                                    val modifier = mObj?.optString("modifier", "") ?: ""
                                    val stepName = nextStep.optString("name", "")
                                    if (stepName.isNotBlank()) nextStreet = stepName
                                    
                                    maneuverType = when {
                                        modifier.contains("left") -> "TURN_LEFT"
                                        modifier.contains("right") -> "TURN_RIGHT"
                                        modifier.contains("uturn") -> "UTURN"
                                        modifier.contains("arrive") -> "DESTINATION"
                                        else -> "STRAIGHT"
                                    }

                                    stepInstruction = when (maneuverType) {
                                        "TURN_LEFT" -> "Turn left onto $nextStreet"
                                        "TURN_RIGHT" -> "Turn right onto $nextStreet"
                                        "UTURN" -> "Make a U-Turn on $nextStreet"
                                        "DESTINATION" -> "Arriving at $displayName"
                                        else -> "Continue straight on $nextStreet"
                                    }
                                }
                            }

                            val geometry = routeObj.getJSONObject("geometry")
                            val coords = geometry.getJSONArray("coordinates")
                            val geoPoints = ArrayList<GeoPoint>()
                            for (c in 0 until coords.length()) {
                                val point = coords.getJSONArray(c)
                                geoPoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                            }

                            parsedRoutes.add(
                                RouteData(
                                    index = i,
                                    distanceMeters = totalDistM,
                                    durationSeconds = totalDurSec,
                                    points = geoPoints,
                                    maneuverType = maneuverType,
                                    nextStreet = nextStreet,
                                    destName = displayName,
                                    isFastest = false,
                                    stepInstruction = stepInstruction
                                )
                            )
                        }

                        val finalRoutes = parsedRoutes.mapIndexed { idx, r ->
                            r.copy(isFastest = (idx == fastestIndex))
                        }
                        val fastestRoute = finalRoutes[fastestIndex]

                        withContext(Dispatchers.Main) {
                            onRouteReady(finalRoutes, fastestRoute)

                            // Draw polylines: Draw alternative routes first, then fastest on top
                            mapView.overlays.removeAll { it is Polyline }
                            
                            finalRoutes.forEachIndexed { index, route ->
                                if (index != fastestIndex) {
                                    val altPolyline = Polyline(mapView).apply {
                                        setPoints(route.points)
                                        outlinePaint.color = android.graphics.Color.parseColor("#455A64")
                                        outlinePaint.strokeWidth = 8f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                        outlinePaint.strokeJoin = Paint.Join.ROUND
                                    }
                                    mapView.overlays.add(altPolyline)
                                }
                            }

                            val fastestPolyline = Polyline(mapView).apply {
                                setPoints(fastestRoute.points)
                                outlinePaint.color = android.graphics.Color.parseColor("#00E676")
                                outlinePaint.strokeWidth = 14f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                            }
                            mapView.overlays.add(fastestPolyline)

                            // Bounding Box to fit all points smoothly
                            try {
                                if (fastestRoute.points.isNotEmpty()) {
                                    val bbox = BoundingBox.fromGeoPoints(fastestRoute.points)
                                    mapView.zoomToBoundingBox(bbox, true, 120)
                                }
                            } catch (_: Exception) {
                                mapView.controller.animateTo(destPoint)
                            }
                            mapView.invalidate()
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

