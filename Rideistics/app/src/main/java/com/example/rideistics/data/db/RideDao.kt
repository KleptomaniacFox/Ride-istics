package com.example.rideistics.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(rideData: RideData): Long // Returns the new rowId

    @Query("SELECT * FROM ride_data ORDER BY trip_ID DESC")
    fun getAllRidesLiveData(): LiveData<List<RideData>> // For observing changes in UI

    @Query("SELECT * FROM ride_data ORDER BY trip_ID DESC")
    suspend fun getAllRidesList(): List<RideData> // For one-time fetch, e.g., logging

    @Query("SELECT * FROM ride_data WHERE trip_ID = :id")
    suspend fun getRideById(id: Long): RideData?

    @Query("DELETE FROM ride_data")
    suspend fun clearAllRides()

    @Query("""
        SELECT COUNT(trip_id) FROM ride_data 
        WHERE 
            (:startDate IS NULL AND :endDate IS NULL AND :isToday = 0) OR
            (:isToday = 1 AND trip_Date = :startDate) OR
            (:isToday = 0 AND :startDate IS NOT NULL AND :endDate IS NOT NULL AND trip_Date BETWEEN :startDate AND :endDate)
            """)
    suspend fun getTotalRidesForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Int

    /**
     * Calculates the total fuel consumed for the given period.
     * Assumes 'trip_Fuel_Consumed_Liters' is the column name for fuel.
     */
    @Query("""
        SELECT SUM(trip_Fuel_Consumed_Liters) FROM ride_data
        WHERE
            (:startDate IS NULL AND :endDate IS NULL AND :isToday = 0) OR
            (:isToday = 1 AND trip_Date = :startDate) OR
            (:isToday = 0 AND :startDate IS NOT NULL AND :endDate IS NOT NULL AND trip_Date BETWEEN :startDate AND :endDate)
    """)
    suspend fun getTotalFuelSpentForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Double?

    /**
     * Calculates the total distance traveled for the given period.
     * Assumes 'trip_Distance_Meters' is the column name for distance.
     * Note: If trip_Distance_Meters is stored as Float/Real, SUM will return Double?
     */
    @Query("""
        SELECT SUM(trip_Distance) FROM ride_data
        WHERE
            (:startDate IS NULL AND :endDate IS NULL AND :isToday = 0) OR
            (:isToday = 1 AND trip_Date = :startDate) OR
            (:isToday = 0 AND :startDate IS NOT NULL AND :endDate IS NOT NULL AND trip_Date BETWEEN :startDate AND :endDate)
    """)
    suspend fun getTotalDistanceForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Double?

    @Query("""
        SELECT AVG(trip_Real_Mileage_KmL) FROM ride_data
        WHERE
            (:startDate IS NULL AND :endDate IS NULL AND :isToday = 0) OR
            (:isToday = 1 AND trip_Date = :startDate) OR
            (:isToday = 0 AND :startDate IS NOT NULL AND :endDate IS NOT NULL AND trip_Date BETWEEN :startDate AND :endDate)
        """)
    suspend fun calculateAverageFuelEfficiency(startDate: String?, endDate: String?, isToday: Boolean): Double?
    
    @Query("""
            SELECT * FROM ride_data
            WHERE
                (:startDate IS NULL AND :endDate IS NULL AND :isToday = 0) OR 
                (:isToday = 1 AND trip_Date = :startDate) OR
                (:isToday = 0 AND :startDate IS NOT NULL AND :endDate IS NOT NULL AND trip_Date BETWEEN :startDate AND :endDate)
    """)
    suspend fun getRidesListForPeriod(startDate: String?, endDate: String?, isToday: Boolean): List<RideData>


    @Query("SELECT * FROM ride_data ORDER BY trip_Date ASC")
    suspend fun getAllRidesSortedByDate(): List<RideData>


}