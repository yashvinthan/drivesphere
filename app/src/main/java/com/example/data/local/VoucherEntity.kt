package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rewards_vouchers")
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val partnerName: String,
    val costCoins: Int,
    val category: String, // "FUEL", "SERVICE", "FOOD", "SAFETY_GEAR"
    val promoCode: String,
    val isRedeemed: Boolean = false,
    val description: String = ""
)
