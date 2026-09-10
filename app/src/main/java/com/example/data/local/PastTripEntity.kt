package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "past_trips")
data class PastTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val distanceKm: Double,
    val score: Int
)
