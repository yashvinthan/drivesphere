package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseRepository(private val dao: DriveSphereDao) {
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val pastTrips: Flow<List<PastTripEntity>> = dao.getAllPastTrips()
    val allChallans: Flow<List<ChallanEntity>> = dao.getAllChallans()
    val pendingChallans: Flow<List<ChallanEntity>> = dao.getPendingChallans()
    val allVouchers: Flow<List<VoucherEntity>> = dao.getAllVouchers()

    suspend fun saveUserProfile(
        name: String, 
        emergencyContactName: String, 
        emergencyContactPhone: String,
        vehicleType: String = "BIKE",
        vehicleRegNumber: String = "DL-01-AB-1234",
        secondaryContactName: String = "Campus Security",
        secondaryContactPhone: String = "+91 98765 43210",
        campusRollNumber: String = "CS-2024-89"
    ) {
        dao.saveUserProfile(
            UserProfileEntity(
                id = 1,
                name = name, 
                emergencyContactName = emergencyContactName, 
                emergencyContactPhone = emergencyContactPhone,
                vehicleType = vehicleType,
                vehicleRegNumber = vehicleRegNumber,
                secondaryContactName = secondaryContactName,
                secondaryContactPhone = secondaryContactPhone,
                campusRollNumber = campusRollNumber
            )
        )
    }

    suspend fun recordCompletedTrip(distanceKm: Double, durationMins: Int, score: Int): PastTripEntity {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date())
        val roundedDistance = Math.round(distanceKm * 10.0) / 10.0
        val trip = PastTripEntity(
            date = formattedDate,
            distanceKm = roundedDistance.coerceAtLeast(0.1),
            score = score.coerceIn(40, 100)
        )
        dao.insertPastTrips(listOf(trip))
        return trip
    }

    suspend fun payChallan(challanId: Int) {
        dao.markChallanPaid(challanId)
    }

    suspend fun disputeChallan(challanId: Int) {
        dao.markChallanDisputed(challanId)
    }

    suspend fun redeemVoucher(voucherId: Int) {
        dao.markVoucherRedeemed(voucherId)
    }

    suspend fun saveOfficialChallans(challans: List<ChallanEntity>) {
        if (challans.isNotEmpty()) {
            dao.insertChallans(challans)
        }
    }

    suspend fun initializeDefaultDataIfNeeded() {
        // Initialize Default Past Trips if database is freshly installed
        if (dao.getPastTripsCount() == 0) {
            val defaultTrips = listOf(
                PastTripEntity(date = "08 Mar 2026", distanceKm = 14.2, score = 92),
                PastTripEntity(date = "07 Mar 2026", distanceKm = 8.5, score = 98),
                PastTripEntity(date = "05 Mar 2026", distanceKm = 22.1, score = 85),
                PastTripEntity(date = "03 Mar 2026", distanceKm = 5.4, score = 100)
            )
            dao.insertPastTrips(defaultTrips)
        }

        // Initialize Partner Reward Vouchers if empty
        if (dao.getVouchersCount() == 0) {
            val defaultVouchers = listOf(
                VoucherEntity(
                    title = "₹100 Fuel Cashback Voucher",
                    partnerName = "IndianOil / HPCL",
                    costCoins = 350,
                    category = "FUEL",
                    promoCode = "DRIVE-FUEL-100",
                    isRedeemed = false,
                    description = "Redeemable at any participating IndianOil or HPCL petrol station."
                ),
                VoucherEntity(
                    title = "Free Two-Wheeler Safety Check",
                    partnerName = "SpeedWorks Auto Care",
                    costCoins = 200,
                    category = "SERVICE",
                    promoCode = "SAFETY-CHECK-FREE",
                    isRedeemed = false,
                    description = "Comprehensive 18-point brake, tyre pressure, and chain maintenance inspection."
                ),
                VoucherEntity(
                    title = "25% Off ISI/DOT Safety Helmet",
                    partnerName = "Steelbird / Vega Helmets",
                    costCoins = 450,
                    category = "SAFETY_GEAR",
                    promoCode = "STEELBIRD-SAFE25",
                    isRedeemed = false,
                    description = "Instant 25% discount on all certified crash-tested riding helmets."
                ),
                VoucherEntity(
                    title = "Campus Café Coffee & Snack Combo",
                    partnerName = "Campus Central Café",
                    costCoins = 150,
                    category = "FOOD",
                    promoCode = "CAMPUS-COFFEE-SAFE",
                    isRedeemed = true,
                    description = "Special perk awarded for completing a 7-day safe riding streak."
                )
            )
            dao.insertVouchers(defaultVouchers)
        }
    }
}
