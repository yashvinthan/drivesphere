package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseRepository
import com.example.data.local.PastTripEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.ChallanEntity
import com.example.data.local.VoucherEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DatabaseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).driveSphereDao()
    private val repository = DatabaseRepository(dao)

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val pastTrips: StateFlow<List<PastTripEntity>> = repository.pastTrips.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allChallans: StateFlow<List<ChallanEntity>> = repository.allChallans.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val pendingChallans: StateFlow<List<ChallanEntity>> = repository.pendingChallans.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allVouchers: StateFlow<List<VoucherEntity>> = repository.allVouchers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
        }
    }

    fun saveProfile(
        name: String, 
        emergencyContactName: String, 
        emergencyContactPhone: String,
        vehicleType: String = "BIKE",
        vehicleRegNumber: String = "DL-01-AB-1234",
        secondaryContactName: String = "Campus Security",
        secondaryContactPhone: String = "+91 98765 43210",
        campusRollNumber: String = "CS-2024-89"
    ) {
        viewModelScope.launch {
            repository.saveUserProfile(
                name = name, 
                emergencyContactName = emergencyContactName, 
                emergencyContactPhone = emergencyContactPhone,
                vehicleType = vehicleType,
                vehicleRegNumber = vehicleRegNumber,
                secondaryContactName = secondaryContactName,
                secondaryContactPhone = secondaryContactPhone,
                campusRollNumber = campusRollNumber
            )
        }
    }

    fun saveCompletedTrip(
        distanceKm: Double, 
        durationMins: Int, 
        score: Int, 
        onSaved: (PastTripEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val trip = repository.recordCompletedTrip(distanceKm, durationMins, score)
            onSaved(trip)
        }
    }

    fun payChallan(challanId: Int, onPaid: () -> Unit = {}) {
        viewModelScope.launch {
            repository.payChallan(challanId)
            onPaid()
        }
    }

    fun disputeChallan(challanId: Int) {
        viewModelScope.launch {
            repository.disputeChallan(challanId)
        }
    }

    fun redeemVoucher(voucherId: Int, onRedeemed: () -> Unit = {}) {
        viewModelScope.launch {
            repository.redeemVoucher(voucherId)
            onRedeemed()
        }
    }
}
