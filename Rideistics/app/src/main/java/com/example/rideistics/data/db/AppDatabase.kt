package com.example.rideistics.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RideData::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rideDao(): RideDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rideistics_database"
                )
                    // In a real app, you'd implement proper migrations.
                    // For development, destructive migration is okay.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}