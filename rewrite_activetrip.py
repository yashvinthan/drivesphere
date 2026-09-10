content = """package com.example.ui.screens

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.compose.foundation.background
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
import com.example.ui.theme.OLEDBlack
import com.example.ui.theme.OLEDWhite
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    state: AppState,
    onSimulateEvent: (TripEvent) -> Unit,
    onEndTrip: () -> Unit
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
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var destinationQuery by remember { mutableStateOf("") }
    var isRouting by remember { mutableStateOf(false) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Location Updates for Speedometer and Starting Point
    DisposableEffect(hasLocationPermission) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = LocationListener { location ->
            currentSpeed = location.speed * 3.6f
            userLocation = GeoPoint(location.latitude, location.longitude)
        }
        
        if (hasLocationPermission) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null) {
                    userLocation = GeoPoint(lastKnown.latitude, lastKnown.longitude)
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
        sheetPeekHeight = 160.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Active Session",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Monochrome OLED Preview Component
                Card(
                    colors = CardDefaults.cardColors(containerColor = OLEDBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "OLED PREVIEW (128x64)", style = MaterialTheme.typography.labelSmall, color = OLEDWhite.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "← TURN LEFT", style = MaterialTheme.typography.bodyLarge, color = OLEDWhite)
                        Text(text = "120 m", style = MaterialTheme.typography.titleLarge, color = OLEDWhite)
                        Text(text = "${currentSpeed.toInt()} km/h • Score ${state.summary.score}", style = MaterialTheme.typography.bodyMedium, color = OLEDWhite)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ESP32-CAM Stream", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Awaiting Hardware connection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Simulate Events", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoButton("Overspeed", { onSimulateEvent(TripEvent.OVERSPEED) }, Modifier.weight(1f))
                    DemoButton("Distract", { onSimulateEvent(TripEvent.DISTRACTION) }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoButton("Helmet", { onSimulateEvent(TripEvent.HELMET_RISK) }, Modifier.weight(1f))
                    DemoButton("Fall", { onSimulateEvent(TripEvent.FALL) }, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onEndTrip,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End Session", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleMedium)
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
                    placeholder = { Text("Search here", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                            text = "km/h",
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
fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                            geoPoints.add(GeoPoint(point.getDouble(1), point.getDouble(0))) // GeoJSON is [lon, lat]
                        }
                        
                        // 3. Draw Polyline on Main Thread
                        withContext(Dispatchers.Main) {
                            // Remove existing polylines
                            mapView.overlays.removeAll { it is Polyline }
                            val polyline = Polyline()
                            polyline.setPoints(geoPoints)
                            polyline.outlinePaint.color = android.graphics.Color.parseColor("#00E676") // Match theme generic success green
                            polyline.outlinePaint.strokeWidth = 12f
                            mapView.overlays.add(polyline)
                            
                            // Adjust map to show destination
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
"""
with open('app/src/main/java/com/example/ui/screens/ActiveTripScreen.kt', 'w') as f:
    f.write(content)
