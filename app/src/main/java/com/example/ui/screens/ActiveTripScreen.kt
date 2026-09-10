package com.example.ui.screens

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.viewmodel.AppState
import com.example.viewmodel.TripEvent
import com.example.viewmodel.VehicleType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    state: AppState,
    onTriggerSensorEvent: (TripEvent) -> Unit,
    onUpdateMetrics: (distanceDeltaKm: Double, speedKmH: Float) -> Unit,
    onIncrementDuration: () -> Unit,
    onEndTrip: () -> Unit,
    onAOD: () -> Unit = {},
    onOpenDashcam: () -> Unit = {}
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
    val coroutineScope = rememberCoroutineScope()

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
            userLocation = GeoPoint(location.latitude, location.longitude)

            previousLocation?.let { prev ->
                val distanceMeters = location.distanceTo(prev)
                // Filter GPS drift: distance must be between 2m and 500m per second
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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 180.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ACTIVE TRIP TELEMETRY",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.vehicleType == VehicleType.TWO_WHEELER) "TWO-WHEELER COCKPIT (BIKE)" else "FOUR-WHEELER COCKPIT (CAR)",
                            style = MaterialTheme.typography.labelSmall,
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
                            if (state.isEsp32Connected) "ESP32 LINKED" else "GPS TELEMETRY ACTIVE",
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
                
                // Glyph Matrix OLED HUD
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRouting) {
                            GlyphMatrixDirection(
                                direction = "TURN_LEFT",
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(24.dp))
                            
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "TURN LEFT", 
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                                        fontWeight = FontWeight.Bold
                                    ), 
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "120 m", 
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                                        fontWeight = FontWeight.Bold
                                    ), 
                                    color = Color.White
                                )
                            }
                        } else {
                            GlyphMatrixDirection(
                                direction = state.idleGlyph,
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(24.dp))
                            
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "OLED HUD LINK", 
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ), 
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (state.isEsp32Connected) "Broadcasting to 192.168.4.1" else "Using Smartphone Gyroscope", 
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ), 
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
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
                            "LIVE MJPEG / SNAPSHOT • 192.168.4.2", 
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

            // Top Floating Search Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                OutlinedTextField(
                    value = destinationQuery,
                    onValueChange = { destinationQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("SEARCH DESTINATION", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (isRouting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = {
                                    val startLoc = userLocation ?: GeoPoint(13.0827, 80.2707)
                                    mapViewRef?.let { map ->
                                        isRouting = true
                                        coroutineScope.launch {
                                            fetchAndDrawRoute(startLoc, destinationQuery, map)
                                            isRouting = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = "Route", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            // Right-side Floating Action Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Map Layers Toggle
                FloatingActionButton(
                    onClick = { 
                        isSatelliteMode = !isSatelliteMode
                        mapViewRef?.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                        mapViewRef?.invalidate()
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = if (isSatelliteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = "Toggle Map View")
                }

                // Speedometer Widget
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${currentSpeed.toInt()}",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "KM/H",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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

suspend fun fetchAndDrawRoute(start: GeoPoint, destinationName: String, mapView: MapView) = withContext(Dispatchers.IO) {
    try {
        if (destinationName.isBlank()) return@withContext
        val client = okhttp3.OkHttpClient()
        
        // 1. Geocode Destination via Nominatim
        val searchUrl = "https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(destinationName, "UTF-8")}&format=json&limit=1"
        val searchRequest = okhttp3.Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "DriveSphereApp")
            .build()
            
        val searchResponse = client.newCall(searchRequest).execute()
        val searchJsonStr = searchResponse.body?.string()
        
        if (!searchJsonStr.isNullOrEmpty()) {
            val jsonArray = JSONArray(searchJsonStr)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val destPoint = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
                
                // 2. Fetch Route via OSRM
                val routeUrl = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${destPoint.longitude},${destPoint.latitude}?overview=full&geometries=geojson"
                val routeRequest = okhttp3.Request.Builder().url(routeUrl).build()
                val routeResponse = client.newCall(routeRequest).execute()
                val routeJsonStr = routeResponse.body?.string()
                
                if (!routeJsonStr.isNullOrEmpty()) {
                    val json = JSONObject(routeJsonStr)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        val geoPoints = ArrayList<GeoPoint>()
                        for (i in 0 until coords.length()) {
                            val point = coords.getJSONArray(i)
                            geoPoints.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
                        }
                        
                        withContext(Dispatchers.Main) {
                            mapView.overlays.removeAll { it is Polyline }
                            val polyline = Polyline()
                            polyline.setPoints(geoPoints)
                            polyline.outlinePaint.color = android.graphics.Color.parseColor("#00E676")
                            polyline.outlinePaint.strokeWidth = 12f
                            mapView.overlays.add(polyline)
                            mapView.controller.animateTo(destPoint)
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
