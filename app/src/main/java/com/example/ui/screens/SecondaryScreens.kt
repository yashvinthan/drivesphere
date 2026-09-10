package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.example.data.WifiConnectHelper
import com.example.data.local.VoucherEntity
import com.example.viewmodel.AppState
import com.example.viewmodel.HardwareTransport
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay

@Composable
fun RewardsScreen(
    state: AppState,
    vouchers: List<VoucherEntity> = emptyList(),
    onRedeemVoucher: (VoucherEntity) -> Unit = {}
) {
    var selectedVoucherToRedeem by remember { mutableStateOf<VoucherEntity?>(null) }
    var redeemedCodeDialog by remember { mutableStateOf<VoucherEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "REWARDS & COINS",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // DriveCoins Balance Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DRIVECOINS BALANCE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "${state.driveCoins} DC", 
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), 
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("250 DC more to reach Platinum Safe Driver Tier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Rider Badges", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AchievementBadge(icon = Icons.Default.Bedtime, title = "Night Rider", color = MaterialTheme.colorScheme.primary, delay = 0)
            AchievementBadge(icon = Icons.Default.Eco, title = "Eco-Cruiser", color = MaterialTheme.colorScheme.secondary, delay = 100)
            AchievementBadge(icon = Icons.Default.Route, title = "Zero-Challan", color = Color(0xFF00E676), delay = 200)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Partner Vouchers", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (vouchers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No available vouchers at this moment. Complete safe trips to unlock rewards.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            vouchers.forEach { voucher ->
                val canAfford = state.driveCoins >= voucher.costCoins
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                    border = BorderStroke(1.dp, if (voucher.isRedeemed) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF262626)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable {
                            if (voucher.isRedeemed) {
                                redeemedCodeDialog = voucher
                            } else if (canAfford) {
                                selectedVoucherToRedeem = voucher
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (voucher.isRedeemed) Color(0xFF00E676).copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), 
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (voucher.isRedeemed) "USED" else "DC", 
                                color = if (voucher.isRedeemed) Color(0xFF00E676) else MaterialTheme.colorScheme.tertiary, 
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voucher.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text(voucher.partnerName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        
                        if (voucher.isRedeemed) {
                            Button(
                                onClick = { redeemedCodeDialog = voucher },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("VIEW CODE", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { selectedVoucherToRedeem = voucher },
                                enabled = canAfford,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${voucher.costCoins} DC", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Redeem Dialog
    selectedVoucherToRedeem?.let { voucher ->
        AlertDialog(
            onDismissRequest = { selectedVoucherToRedeem = null },
            containerColor = Color(0xFF151515),
            title = { Text("Redeem Reward", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) },
            text = {
                Column {
                    Text(voucher.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(voucher.description, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cost: ${voucher.costCoins} DriveCoins", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRedeemVoucher(voucher)
                        val redeemed = voucher.copy(isRedeemed = true)
                        selectedVoucherToRedeem = null
                        redeemedCodeDialog = redeemed
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text("CONFIRM REDEEM", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedVoucherToRedeem = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    // View Code Modal
    redeemedCodeDialog?.let { voucher ->
        AlertDialog(
            onDismissRequest = { redeemedCodeDialog = null },
            containerColor = Color(0xFF151515),
            icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(48.dp)) },
            title = { Text("Reward Code Ready", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Present this promotional code at payment counter:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            voucher.promoCode,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = Color(0xFF00E676)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Partner: ${voucher.partnerName}", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                }
            },
            confirmButton = {
                Button(
                    onClick = { redeemedCodeDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text("CLOSE", color = Color.Black)
                }
            }
        )
    }
}

@Composable
fun AchievementBadge(icon: ImageVector, title: String, color: Color, delay: Int) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delay.toLong())
        scale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 500))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp).scale(scale.value)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFF121212), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GuardianHubScreen(
    state: AppState, 
    onSetGlyph: (String) -> Unit,
    onUpdateIp: (String) -> Unit = {},
    onSetTransport: (HardwareTransport) -> Unit = {},
    onConnectBluetooth: (String?, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    getPairedBtDevices: () -> List<Pair<String, String>> = { emptyList() },
    onPingHardware: ((Boolean, String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val wifiHelper = remember { WifiConnectHelper(context) }
    val wifiStatus by wifiHelper.connectionStatus.collectAsState()

    var ipInput by remember { mutableStateOf(state.esp32IpAddress) }
    var pingMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberMultiplePermissionsState(
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        )
    } else {
        rememberMultiplePermissionsState(
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    LaunchedEffect(state.hardwareTransport, bluetoothPermissions.allPermissionsGranted) {
        if (state.hardwareTransport == HardwareTransport.BLUETOOTH) {
            if (bluetoothPermissions.allPermissionsGranted) {
                pairedDevices = getPairedBtDevices()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("GUARDIAN HUB", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Text("ESP32 IoT Telemetry • Wi-Fi & Bluetooth Controller", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))

        // Transport Mode Selector: Wi-Fi vs Bluetooth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSetTransport(HardwareTransport.WIFI) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.hardwareTransport == HardwareTransport.WIFI) Color.White else Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (state.hardwareTransport == HardwareTransport.WIFI) Color.Black else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "WI-FI REST",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state.hardwareTransport == HardwareTransport.WIFI) Color.Black else Color.Gray
                )
            }

            Button(
                onClick = { onSetTransport(HardwareTransport.BLUETOOTH) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.hardwareTransport == HardwareTransport.BLUETOOTH) Color.White else Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (state.hardwareTransport == HardwareTransport.BLUETOOTH) Color.Black else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "BLUETOOTH SPP",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state.hardwareTransport == HardwareTransport.BLUETOOTH) Color.Black else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        
        // Physical ESP32 Hardware Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
            border = BorderStroke(1.dp, if (state.isEsp32Connected) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFF333333)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (state.hardwareTransport == HardwareTransport.WIFI) "WI-FI LINK" else "BLUETOOTH LINK",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                if (state.isEsp32Connected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF29B6F6).copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (state.isEsp32Connected) "ESP32 LINKED" else "STANDBY / PHONE SENSORS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isEsp32Connected) Color(0xFF00E676) else Color(0xFF29B6F6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ACTIVE TRANSPORT", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Text(if (state.hardwareTransport == HardwareTransport.WIFI) "Wi-Fi (DriveSphere-Hub)" else "Bluetooth SPP (DriveSphere-Hub)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("OLED DISPLAY (I2C 0)", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Text("SDA: GPIO 15 | SCL: GPIO 14", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MPU-6050 IMU (I2C 1)", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Text("SDA: GPIO 13 | SCL: GPIO 2", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("NEO-M8N GPS (UART2)", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Text("TX -> GPIO 16 (U2RXD)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SOS PUSH BUTTON", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    Text("GPIO 12 (INPUT_PULLUP)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E1E1E))
                Spacer(modifier = Modifier.height(16.dp))

                // Transport-Specific In-App Connect Actions
                if (state.hardwareTransport == HardwareTransport.WIFI) {
                    // Wi-Fi Connect In-App Section
                    Text("IN-APP WI-FI PAIRING", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Link directly to SSID 'DriveSphere-Hub' (pass: drivesphere123)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                wifiHelper.connectToHubWifi(
                                    onConnected = {
                                        onUpdateIp("192.168.4.1")
                                        onPingHardware { success, msg -> pingMessage = Pair(success, msg) }
                                    },
                                    onError = { err -> pingMessage = Pair(false, err) }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONNECT IN-APP", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        OutlinedButton(
                            onClick = { wifiHelper.launchWifiSettingsPanel() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("WI-FI PANEL", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom IP Input & Reconnect / Diagnostic Ping Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            label = { Text("ESP32 IP Address") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { 
                                onUpdateIp(ipInput)
                                isPinging = true
                                onPingHardware { success, message ->
                                    isPinging = false
                                    pingMessage = Pair(success, message)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isPinging) "..." else "PING", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Bluetooth Connect In-App Section
                    Text("IN-APP BLUETOOTH PAIRING", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pair with 'DriveSphere-Hub' over Bluetooth Serial (SPP)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Runtime Permission Banner
                    if (!bluetoothPermissions.allPermissionsGranted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1E05)),
                            border = BorderStroke(1.dp, Color(0xFFFFB300)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Bluetooth Permission Required", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                    Text("Grant permissions to scan & connect to ESP32.", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                }
                                Button(
                                    onClick = { bluetoothPermissions.launchMultiplePermissionRequest() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("GRANT", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!bluetoothPermissions.allPermissionsGranted) {
                                    bluetoothPermissions.launchMultiplePermissionRequest()
                                } else {
                                    isPinging = true
                                    onConnectBluetooth(null) { success, message ->
                                        isPinging = false
                                        pingMessage = Pair(success, message)
                                        pairedDevices = getPairedBtDevices()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPinging) "LINKING..." else "LINK BT IN-APP", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("BT SETTINGS", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Hardware Troubleshooting Checklist Box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                        border = BorderStroke(1.dp, Color(0xFF282828)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ESP32 HARDWARE BT CHECKLIST:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("1. Disconnect GPIO 0 from GND (used only for flashing).", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            Text("2. Press the physical RST button on the ESP32 to boot sketch.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            Text("3. Tap 'BT SETTINGS' above -> Pair with 'DriveSphere-Hub'.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                            Text("4. Tap 'DriveSphere-Hub' in the list below to connect.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Paired Bluetooth Devices:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                        TextButton(
                            onClick = { 
                                if (bluetoothPermissions.allPermissionsGranted) {
                                    pairedDevices = getPairedBtDevices() 
                                } else {
                                    bluetoothPermissions.launchMultiplePermissionRequest()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("REFRESH", color = Color(0xFF00E676), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (pairedDevices.isEmpty()) {
                        Text(
                            "No paired devices found yet. Pair 'DriveSphere-Hub' under phone Bluetooth Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    } else {
                        pairedDevices.forEach { (devName, devAddr) ->
                            val isTarget = devName.contains("DriveSphere", ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        isPinging = true
                                        onConnectBluetooth(devAddr) { success, message ->
                                            isPinging = false
                                            pingMessage = Pair(success, message)
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTarget) Color(0xFF1E2E1E) else Color(0xFF191919),
                                border = BorderStroke(1.dp, if (isTarget) Color(0xFF00E676) else Color(0xFF333333))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth, 
                                        contentDescription = null, 
                                        tint = if (isTarget) Color(0xFF00E676) else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(devName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                        Text(devAddr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                    if (isTarget) {
                                        Text("CONNECT", color = Color(0xFF00E676), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }

                // Ping Result Diagnostic Feedback
                pingMessage?.let { (success, message) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (success) Color(0xFF00E676).copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, if (success) Color(0xFF00E676) else MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Hardware Telemetry: NEO-M8N GPS & MPU-6050 6-DOF IMU
        Text("LIVE SENSOR TELEMETRY", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        // NEO-M8N GPS Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, if (state.hubGpsFix) Color(0xFF00E676) else Color(0xFF333333)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (state.hubGpsFix) Color(0xFF00E676) else Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NEO-M8N GPS MODULE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (state.hubGpsFix) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (state.hubGpsFix) Color(0xFF00E676) else Color(0xFFFFB300))
                    ) {
                        Text(
                            if (state.hubGpsFix) "FIX ACQUIRED (${state.hubGpsSats} SATS)" else "SEARCHING SATS...",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (state.hubGpsFix) Color(0xFF00E676) else Color(0xFFFFB300)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("COORDINATES", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            if (state.hubGpsLatitude != null && state.hubGpsLongitude != null)
                                String.format(java.util.Locale.US, "%.5f° N, %.5f° E", state.hubGpsLatitude, state.hubGpsLongitude)
                            else "Acquiring Sky Lock...",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("GROUND SPEED", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            "${state.hubGpsSpeedKmH.toInt()} KM/H",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ALTITUDE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("${state.hubGpsAltM.toInt()} m above sea level", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MPU-6050 6-DOF IMU Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, if (state.isMpuAvailable) Color(0xFF29B6F6) else Color(0xFF333333)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("MPU-6050 6-DOF GYRO / ACCEL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (Math.abs(state.hubLeanAngleDeg) > 55f) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color(0xFF29B6F6).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (Math.abs(state.hubLeanAngleDeg) > 55f) MaterialTheme.colorScheme.error else Color(0xFF29B6F6))
                    ) {
                        Text(
                            if (Math.abs(state.hubLeanAngleDeg) > 55f) "FALL DETECTED!" else "IMU ACTIVE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (Math.abs(state.hubLeanAngleDeg) > 55f) MaterialTheme.colorScheme.error else Color(0xFF29B6F6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("LEAN ANGLE (ROLL)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            String.format(java.util.Locale.US, "%.1f°", state.hubLeanAngleDeg),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (Math.abs(state.hubLeanAngleDeg) > 45f) Color(0xFFFF5252) else Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PITCH ANGLE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            String.format(java.util.Locale.US, "%.1f°", state.hubPitchDeg),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL G-FORCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            String.format(java.util.Locale.US, "%.2f G", state.hubAccelG),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.hubAccelG > 3.0f) Color(0xFFFF5252) else Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("FALL / CRASH TRIGGER", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("Lean > 55° or G > 3.5g (Auto-SOS)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // 128x64 OLED Live Broadcast Preview
        Text("128x64 OLED DISPLAY PREVIEW", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(2.dp, Color(0xFF333333)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("[${state.vehicleType.name}]", style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.White)
                    Text(if (state.isEsp32Connected) "AP:ONLINE" else "AP:OFFLINE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.White)
                    Text("98%", style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.activeTripSpeedKmH.toInt()} KM/H", 
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), 
                        color = Color.White
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SCORE: ${state.summary.score}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.White)
                        Text(state.idleGlyph, style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("TELEMETRY SYNC: ACTIVE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        
        Text("SELECT OLED GLYPH PATTERN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(14.dp))
        
        val glyphs = listOf(
            Pair("MAC", "Macintosh Retro"),
            Pair("ARCO", "Arco Arc"),
            Pair("PC", "Desktop Terminal"),
            Pair("CUP", "Café Fuel"),
            Pair("ROCKET", "Rocket Speed"),
            Pair("IDLE_FACE", "Guardian Face")
        )
        
        glyphs.forEach { (glyphId, glyphName) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (state.idleGlyph == glyphId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onSetGlyph(glyphId) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        GlyphMatrixDirection(direction = glyphId, modifier = Modifier.fillMaxSize())
                    }
                    
                    Spacer(modifier = Modifier.width(20.dp))
                    
                    Column {
                        Text(glyphName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (state.idleGlyph == glyphId) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("ACTIVE HUD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Text("TAP TO ACTIVATE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyProfileScreen(
    profile: com.example.data.local.UserProfileEntity?,
    onEditProfile: () -> Unit,
    onViewHistory: () -> Unit,
    onViewChallans: () -> Unit = {},
    onViewSecurity: () -> Unit = {},
    onViewHaptics: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("SAFETY & SETTINGS", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(28.dp))
        
        Text("VEHICLE & COMPLIANCE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileRow(title = "e-Challan & Traffic Compliance", value = "Check & Pay", onClick = onViewChallans)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                ProfileRow(title = "Vehicle Anti-Theft Guard", value = "Arm / Geofence", onClick = onViewSecurity)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                ProfileRow(title = "Trip History & Telemetry", value = "View Records", onClick = onViewHistory)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("EMERGENCY GUARDIANS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileRow(title = "Rider Profile", value = profile?.name ?: "Arjun", onClick = onEditProfile)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                ProfileRow(title = "Primary Guardian", value = profile?.emergencyContactName ?: "Parent (+91 98765...)", onClick = onEditProfile)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                ProfileRow(title = "Secondary (Campus)", value = profile?.secondaryContactName ?: "Warden / Security", onClick = onEditProfile)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("HARDWARE PREFERENCES", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GLYPH HAPTIC SYNC", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = true, onCheckedChange = {})
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Vibrate phone to match physical ESP32 OLED alert pulses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onViewHaptics() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HAPTIC MAPPING", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Edit mappings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun ProfileRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}
