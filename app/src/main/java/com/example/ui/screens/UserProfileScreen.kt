package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserProfileEntity

@Composable
fun UserProfileScreen(
    profile: UserProfileEntity?,
    onSave: (name: String, contactName: String, contactPhone: String, vehicleType: String, vehicleReg: String, secName: String, secPhone: String, roll: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.name ?: "Arjun") }
    var contactName by remember(profile) { mutableStateOf(profile?.emergencyContactName ?: "Parent") }
    var contactPhone by remember(profile) { mutableStateOf(profile?.emergencyContactPhone ?: "+91 98765 43210") }
    var vehicleType by remember(profile) { mutableStateOf(profile?.vehicleType ?: "BIKE") }
    var vehicleReg by remember(profile) { mutableStateOf(profile?.vehicleRegNumber ?: "DL-01-AB-1234") }
    var secName by remember(profile) { mutableStateOf(profile?.secondaryContactName ?: "Campus Security / Warden") }
    var secPhone by remember(profile) { mutableStateOf(profile?.secondaryContactPhone ?: "+91 11 2766 7777") }
    var rollNumber by remember(profile) { mutableStateOf(profile?.campusRollNumber ?: "CS-2024-89") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Rider & Vehicle Profile", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        Text("Configures Emergency SOS & Government e-Challan RC link", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Driver / Student Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = rollNumber,
            onValueChange = { rollNumber = it },
            label = { Text("Campus ID / Roll Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = vehicleReg,
            onValueChange = { vehicleReg = it },
            label = { Text("Vehicle Registration Number (RC)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(20.dp))
        
        Text("Primary Emergency Guardian", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = contactName,
            onValueChange = { contactName = it },
            label = { Text("Primary Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = contactPhone,
            onValueChange = { contactPhone = it },
            label = { Text("Primary Phone (Receives SMS SOS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text("Secondary Contact (Hostel / Campus Warden)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = secName,
            onValueChange = { secName = it },
            label = { Text("Secondary Contact Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = secPhone,
            onValueChange = { secPhone = it },
            label = { Text("Secondary Phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = { 
                    onSave(name, contactName, contactPhone, vehicleType, vehicleReg, secName, secPhone, rollNumber)
                    onBack()
                },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Profile", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
