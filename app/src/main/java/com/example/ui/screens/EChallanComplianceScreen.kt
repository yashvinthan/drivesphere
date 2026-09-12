package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ParivahanChallanService
import com.example.data.local.ChallanEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EChallanComplianceScreen(
    vehicleRegNumber: String,
    challans: List<ChallanEntity>,
    onPayChallan: (Int) -> Unit,
    onDisputeChallan: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(vehicleRegNumber) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Pending, 2: Paid
    var showDisputeDialog by remember { mutableStateOf<ChallanEntity?>(null) }
    var showPaymentSuccessDialog by remember { mutableStateOf(false) }

    // Real Parivahan MoRTH verification states
    var isVerifying by remember { mutableStateOf(false) }
    var parivahanStatusMsg by remember { mutableStateOf<String?>(null) }
    var isParivahanOnline by remember { mutableStateOf<Boolean?>(null) }
    var lastVerifiedTime by remember { mutableStateOf<String?>(null) }

    val filteredChallans = remember(challans, selectedTab) {
        when (selectedTab) {
            1 -> challans.filter { it.status == "PENDING" }
            2 -> challans.filter { it.status == "PAID" }
            else -> challans
        }
    }

    val pendingCount = challans.count { it.status == "PENDING" }
    val totalPendingAmount = challans.filter { it.status == "PENDING" }.sumOf { it.amount }

    fun triggerParivahanCheck() {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        isVerifying = true
        parivahanStatusMsg = "Contacting Parivahan MoRTH Portal..."

        coroutineScope.launch {
            val res = ParivahanChallanService.verifyVehicleOnParivahan(searchQuery)
            isVerifying = false
            if (res.isSuccess) {
                val data = res.getOrThrow()
                isParivahanOnline = true
                parivahanStatusMsg = data.message
                lastVerifiedTime = data.lastVerifiedTime
            } else {
                isParivahanOnline = false
                parivahanStatusMsg = res.exceptionOrNull()?.message ?: "Parivahan service unreachable"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "TRAFFIC COMPLIANCE",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "Official Parivahan MoRTH Integration",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00E5FF)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vehicle Registration & Live Parivahan Query Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ENTER INDIAN VEHICLE REGISTRATION NUMBER", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.uppercase() },
                        placeholder = { Text("e.g. DL-01-AB-1234", color = Color.DarkGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { triggerParivahanCheck() }
                        ),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = { triggerParivahanCheck() },
                        enabled = !isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Live Verify & Direct Portal Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ParivahanChallanService.openOfficialPortal(context, searchQuery)
                        },
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OPEN GOV PORTAL", color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(
                                if (pendingCount == 0) Color(0xFF00E676).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (pendingCount == 0) "CLEAN RECORD (0 FINES)" else "$pendingCount UNPAID CHALLANS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (pendingCount == 0) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Verification Feedback Banner
                parivahanStatusMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isParivahanOnline == true) Color(0xFF0A2012) else Color(0xFF201010)
                        ),
                        border = BorderStroke(1.dp, if (isParivahanOnline == true) Color(0xFF00E676) else Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isParivahanOnline == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isParivahanOnline == true) Color(0xFF00E676) else Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = Color.White)
                                lastVerifiedTime?.let { t ->
                                    Text("Verified at: $t", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                border = BorderStroke(1.dp, Color(0xFF222222)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TOTAL FINES", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹$totalPendingAmount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                border = BorderStroke(1.dp, Color(0xFF222222)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("CLEAN REWARD", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("+150 DC", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "PENDING", "CLEARED").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Button(
                    onClick = { selectedTab = index },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color.White else Color(0xFF151515),
                        contentColor = if (isSelected) Color.Black else Color.Gray
                    )
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // e-Challan Records List
        if (filteredChallans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("NO PENDING VIOLATIONS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Official MoRTH Parivahan record shows 100% compliance.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { ParivahanChallanService.openOfficialPortal(context, searchQuery) },
                        border = BorderStroke(1.dp, Color(0xFF444444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CHECK ON ECHALLAN.PARIVAHAN.GOV.IN", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredChallans) { challan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E0E)),
                        border = BorderStroke(1.dp, if (challan.status == "PENDING") MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else Color(0xFF222222)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    challan.challanNumber,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.LightGray
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            when (challan.status) {
                                                "PAID" -> Color(0xFF00E676).copy(alpha = 0.2f)
                                                "PENDING" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                                else -> Color.Gray.copy(alpha = 0.2f)
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        challan.status,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = when (challan.status) {
                                            "PAID" -> Color(0xFF00E676)
                                            "PENDING" -> MaterialTheme.colorScheme.error
                                            else -> Color.LightGray
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(challan.violationType, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(challan.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF1E1E1E))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PENALTY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("₹${challan.amount}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }

                                Column {
                                    Text("SCORE IMPACT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("-${challan.scoreDeduction} PTS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                }

                                if (challan.status == "PENDING") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showDisputeDialog = challan },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color.DarkGray),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("DISPUTE", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                        }
                                        Button(
                                            onClick = {
                                                onPayChallan(challan.id)
                                                showPaymentSuccessDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("PAY NOW", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dispute Modal Dialog
        showDisputeDialog?.let { challan ->
            AlertDialog(
                onDismissRequest = { showDisputeDialog = null },
                containerColor = Color(0xFF151515),
                title = { Text("Dispute e-Challan", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) },
                text = {
                    Column {
                        Text("Challan: ${challan.challanNumber}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Submit grievance to State Traffic Police Authority with automated GPS & Camera verification log from DriveSphere Guardian Hub.", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDisputeChallan(challan.id)
                            showDisputeDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("SUBMIT EVIDENCE", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisputeDialog = null }) {
                        Text("CANCEL", color = Color.Gray)
                    }
                }
            )
        }

        // Payment Success Dialog
        if (showPaymentSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showPaymentSuccessDialog = false },
                containerColor = Color(0xFF151515),
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(48.dp)) },
                title = { Text("Challan Settled", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White) },
                text = {
                    Text("Payment of e-Challan verified via government gateway. Your Traffic Behaviour Score has recovered +8 points, and you earned 50 DriveCoins for prompt resolution.", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                },
                confirmButton = {
                    Button(
                        onClick = { showPaymentSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("DONE", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
