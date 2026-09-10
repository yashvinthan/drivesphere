package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TripSummary(
    val safeDistanceKm: Double = 0.0,
    val driveCoins: Int = 1250,
    val streakDays: Int = 7,
    val score: Int = 100
)

interface TripRepository {
    fun getSummary(): Flow<TripSummary>
    suspend fun recordViolation(penalty: Int, reason: String)
    suspend fun addSafeDistance(distanceKm: Double)
    suspend fun resetScore()
}

class LiveTripRepository : TripRepository {
    private val _summary = MutableStateFlow(TripSummary())
    
    override fun getSummary(): Flow<TripSummary> = _summary.asStateFlow()
    
    override suspend fun recordViolation(penalty: Int, reason: String) {
        _summary.update { current ->
            val updatedScore = (current.score - penalty).coerceIn(40, 100)
            current.copy(score = updatedScore)
        }
    }

    override suspend fun addSafeDistance(distanceKm: Double) {
        _summary.update { current ->
            val newDistance = current.safeDistanceKm + distanceKm
            // Gradual score recovery with safe distance (1 point every 2 km of safe driving)
            val recoveredScore = if (newDistance > 0 && current.score < 100) {
                (current.score + 1).coerceAtMost(100)
            } else {
                current.score
            }
            current.copy(safeDistanceKm = newDistance, score = recoveredScore)
        }
    }

    override suspend fun resetScore() {
        _summary.update { it.copy(score = 100, safeDistanceKm = 0.0) }
    }
}
