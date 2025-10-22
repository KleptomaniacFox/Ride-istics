package com.example.rideistics

import android.app.Application
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatDelegate
import com.example.rideistics.data.repository.RideRepository
import com.example.rideistics.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RideisticsApplication : Application() {
    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this) }
    val rideRepository by lazy { RideRepository(database.rideDao()) }

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Do this to clear the database on app startup
//        CoroutineScope(Dispatchers.IO).launch {
//            database.rideDao().clearAllRides()
//            // After clearing, you can proceed to insert new data if that's part of your startup.
//            // For example, if you have a method to insert fresh fake data:
//            // insertNewFakeData()
//        }
    }
}