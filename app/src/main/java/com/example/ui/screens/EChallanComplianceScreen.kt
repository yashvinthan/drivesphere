package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChallanEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EChallanComplianceScreen(
    vehicleRegNumber: String,
    challans: List<ChallanEntity>,
    onPayChallan: (Int) -> Unit,
    onDisputeChallan: (Int) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(vehicleRegNumber) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Pending, 2: Paid
    var showDisputeDialog by remember { mutableStateOf<ChallanEntity?>(null) }
    var showPaymentSuccessDialog by remember { mutableStateOf(false) }

    val filteredChallans = remember(challans, selectedTab) {
        when (selectedTab) {
            1 -> challans.filter { it.status == "PENDING" }
            2 -> challans.filter { it.status == "PAID" }
            else -> challans
        }
    }

    val pendingCount = challans.count { it.status == "PENDING" }
    val totalPendingAmount = challans.filter { it.status == "PENDING" }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
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
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "e-Challan & Enforcement Engine",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Vehicle Registration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("REGISTERED VEHICLE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        searchQuery.uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (pendingCount == 0) Color(0xFF00E676).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (pendingCount == 0) "CLEAN RECORD" else "$pendingCount UNPAID",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (pendingCount == 0) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF222222)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("TOTAL FINES", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹$totalPendingAmount", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF222222)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("CLEAN BONUS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("+150 DC", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF00E676))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "PENDING", "CLEARED").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Button(
                    onClick = { selectedTab = index },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color.White else Color(0xFF151515),
                        contentColor = if (isSelected) Color.Black else Color.Gray
                    )
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // e-Challan Records List
        if (filteredChallans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("NO VIOLATIONS FOUND", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Text("Your driving compliance is 100% verified.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredChallans) { challan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                        border = BorderStroke(1.dp, if (challan.status == "PENDING") MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else Color(0xFF222222)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    challan.challanNumber,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(challan.violationType, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(challan.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF1E1E1E))
                            Spacer(modifier = Modifier.height(12.dp))

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
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
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
