package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "e_challans")
data class ChallanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val challanNumber: String,
    val vehicleNumber: String,
    val violationType: String,
    val location: String,
    val amount: Int,
    val date: String,
    val status: String, // "PENDING", "PAID", "DISPUTED"
    val scoreDeduction: Int
)
