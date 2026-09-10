package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSphereDao {
    // --- User Profile ---
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)

    // --- Past Trips ---
    @Query("SELECT * FROM past_trips ORDER BY id DESC")
    fun getAllPastTrips(): Flow<List<PastTripEntity>>

    @Insert
    suspend fun insertPastTrips(trips: List<PastTripEntity>)
    
    @Query("SELECT COUNT(*) FROM past_trips")
    suspend fun getPastTripsCount(): Int

    // --- e-Challan Compliance ---
    @Query("SELECT * FROM e_challans ORDER BY id DESC")
    fun getAllChallans(): Flow<List<ChallanEntity>>

    @Query("SELECT * FROM e_challans WHERE status = 'PENDING'")
    fun getPendingChallans(): Flow<List<ChallanEntity>>

    @Query("SELECT COUNT(*) FROM e_challans")
    suspend fun getChallansCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallans(challans: List<ChallanEntity>)

    @Query("UPDATE e_challans SET status = 'PAID' WHERE id = :challanId")
    suspend fun markChallanPaid(challanId: Int)

    @Query("UPDATE e_challans SET status = 'DISPUTED' WHERE id = :challanId")
    suspend fun markChallanDisputed(challanId: Int)

    // --- Reward Vouchers ---
    @Query("SELECT * FROM rewards_vouchers")
    fun getAllVouchers(): Flow<List<VoucherEntity>>

    @Query("SELECT COUNT(*) FROM rewards_vouchers")
    suspend fun getVouchersCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVouchers(vouchers: List<VoucherEntity>)

    @Query("UPDATE rewards_vouchers SET isRedeemed = 1 WHERE id = :voucherId")
    suspend fun markVoucherRedeemed(voucherId: Int)
}
