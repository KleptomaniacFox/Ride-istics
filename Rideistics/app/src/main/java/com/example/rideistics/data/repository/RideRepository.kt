package com.example.rideistics.data.repository

import androidx.lifecycle.LiveData
import com.example.rideistics.data.db.RideDao
import com.example.rideistics.data.db.RideData

class RideRepository(private val rideDao: RideDao) {

    suspend fun insertRide(rideData: RideData): Long {
        return rideDao.insertRide(rideData)
    }

    fun getAllRidesLiveData(): LiveData<List<RideData>> {
        return rideDao.getAllRidesLiveData()
    }

    suspend fun getAllRidesList(): List<RideData> {
        return rideDao.getAllRidesList()
    }

    suspend fun getRideById(id: Long): RideData? {
        return rideDao.getRideById(id)
    }

    suspend fun clearAllRides() {
        rideDao.clearAllRides()
    }

    suspend fun getTotalRidesForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Int {
        return rideDao.getTotalRidesForPeriod(startDate, endDate, isToday)
    }

    suspend fun getTotalFuelSpentForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Double? {
        return rideDao.getTotalFuelSpentForPeriod(startDate, endDate, isToday)
    }

    suspend fun getTotalDistanceForPeriod(startDate: String?, endDate: String?, isToday: Boolean): Double? {
        return rideDao.getTotalDistanceForPeriod(startDate, endDate, isToday)
    }

    suspend fun calculateAverageFuelEfficiency(startDate: String?, endDate: String?, isToday: Boolean): Double? {
        return rideDao.calculateAverageFuelEfficiency(startDate, endDate, isToday)
    }

    suspend fun getRidesListForPeriod(startDate: String?, endDate: String?, isToday: Boolean): List<RideData> {
        return rideDao.getRidesListForPeriod(startDate, endDate, isToday)

    }

    suspend fun getAllRidesForPrediction(): List<RideData> {
        return rideDao.getAllRidesSortedByDate()
    }

}