package com.example.rideistics.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_data")
data class RideData(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "trip_ID")
    val tripID: Long = 0,

    @ColumnInfo(name = "trip_Date")
    val tripDate: String, // Format: "YYYY-MM-DD"

    @ColumnInfo(name = "trip_Start_Time")
    val tripStartTime: String, // Format: "HH:MM:SS"

    @ColumnInfo(name = "trip_End_Time")
    val tripEndTime: String, // Format: "HH:MM:SS"

    @ColumnInfo(name = "trip_Duration_Seconds") // Storing duration in seconds for easier calculations
    val tripDurationSeconds: Long,

    @ColumnInfo(name = "trip_Distance")
    val tripDistance: Float,

    @ColumnInfo(name = "trip_Avg_Speed_Kmh")
    val tripAvgSpeedKmh: Float,

    @ColumnInfo(name = "trip_Max_Speed_Kmh")
    val tripMaxSpeedKmh: Float,

    @ColumnInfo(name = "trip_Fuel_Consumed_Liters")
    val tripFuelConsumedLiters: Float,

    @ColumnInfo(name = "trip_Set_Mileage_KmL") // The base mileage set by the user for this trip
    val tripSetMileageKmL: Float,

    @ColumnInfo(name = "trip_Real_Mileage_KmL") // The base mileage set by the user for this trip
    val tripRealMileageKmL: Float

//    Too much work...
//    @ColumnInfo(name = "trip_Start_Latitude") // Format: "latitude,longitude"
//    val tripStartLatitude: Double,
//
//    @ColumnInfo(name = "trip_Start_Longitude") // Format: "latitude,longitude"
//    val tripStartLongitude: Double,
//
//    @ColumnInfo(name = "trip_End_Latitude") // Format: "latitude,longitude"
//    val tipEndLatitude: Double,
//
//    @ColumnInfo(name = "trip_End_Longitude") // Format: "latitude,longitude"
//    val tripEndLongitude: Double
)
