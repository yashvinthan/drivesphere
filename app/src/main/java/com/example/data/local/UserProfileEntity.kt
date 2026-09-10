package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val vehicleType: String = "BIKE", // "BIKE" or "CAR"
    val vehicleRegNumber: String = "DL-01-AB-1234",
    val secondaryContactName: String = "College Security / Warden",
    val secondaryContactPhone: String = "+91 98765 43210",
    val campusRollNumber: String = "CS-2024-89"
)
