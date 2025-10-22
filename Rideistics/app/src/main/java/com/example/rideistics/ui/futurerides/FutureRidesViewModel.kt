package com.example.rideistics.ui.futurerides

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rideistics.data.repository.RideRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.pow

// Helper class to hold the slope and intercept of our regression line
data class LinearModel(val slope: Double, val intercept: Double)

// This data class will hold the formatted prediction strings for the UI
data class PredictionData(
    val rides: String,
    val fuel: String,
    val distance: String,
    val efficiency: String
)

class FutureRidesViewModel(private val repository: RideRepository) : ViewModel() {

    // A single LiveData to hold the prediction results for the month
    private val _monthPrediction = MutableLiveData<PredictionData>()
    val monthPrediction: LiveData<PredictionData> = _monthPrediction

    // LiveData to control the visibility of the progress bar
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Calculates the ride prediction for the next 30 days.
     * This is the function the Fragment will call.
     */
    fun calculateNextMonthPrediction() {
        viewModelScope.launch {
            _isLoading.value = true
            val rides = repository.getAllRidesForPrediction()

            if (rides.size < 2) { // Need at least 2 rides to calculate a duration
                setNotEnoughDataState()
                _isLoading.value = false
                return@launch
            }

            // --- New Simple Averaging Logic ---

            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val firstDay = LocalDate.parse(rides.first().tripDate, dateFormatter)
            val lastDay = LocalDate.parse(rides.last().tripDate, dateFormatter)

            // Calculate the total duration in days. Use at least 1 day to avoid division by zero.
            val durationInDays = (ChronoUnit.DAYS.between(firstDay, lastDay) + 1).coerceAtLeast(1)

            val totalRides = rides.size.toDouble()
            val totalDistance = rides.sumOf { it.tripDistance.toDouble() }
            val totalFuel = rides.sumOf { it.tripFuelConsumedLiters.toDouble() }

            val avgRidesPerDay = totalRides / durationInDays
            val avgDistancePerDay = totalDistance / durationInDays
            val avgFuelPerDay = totalFuel / durationInDays

            val predictedRidesForPeriod = avgRidesPerDay * 30
            val predictedDistanceForPeriod = avgDistancePerDay * 30
            val predictedFuelForPeriod = avgFuelPerDay * 30

            // The best predictor for future efficiency is past overall efficiency.
            val predictedEfficiency = if (totalFuel > 0) totalDistance / totalFuel else 0.0

            val predictionResult = PredictionData(
                rides = "Total Rides: ${predictedRidesForPeriod.toInt()}",
                fuel = "Total Fuel: %.1f L".format(predictedFuelForPeriod),
                distance = "Total Distance: %.1f km".format(predictedDistanceForPeriod),
                efficiency = "Avg. Efficiency: %.1f km/L".format(predictedEfficiency)
            )

            _monthPrediction.value = predictionResult


            /*
            // --- 1. Prepare Data Points for Regression (COMMENTED OUT) ---
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val firstDay = LocalDate.parse(rides.first().tripDate, dateFormatter)
            val firstDayMillis = firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val cumulativeDistancePoints = mutableListOf<Pair<Long, Double>>()
            val cumulativeFuelPoints = mutableListOf<Pair<Long, Double>>()
            val cumulativeRidesPoints = mutableListOf<Pair<Long, Double>>()

            var currentDistance = 0.0
            var currentFuel = 0.0

            rides.forEachIndexed { index, ride ->
                val rideDate = LocalDate.parse(ride.tripDate, dateFormatter)
                val rideMillis = rideDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val daysSinceStart = TimeUnit.MILLISECONDS.toDays(rideMillis - firstDayMillis)

                currentDistance += ride.tripDistance
                currentFuel += ride.tripFuelConsumedLiters

                cumulativeDistancePoints.add(daysSinceStart to currentDistance)
                cumulativeFuelPoints.add(daysSinceStart to currentFuel)
                cumulativeRidesPoints.add(daysSinceStart to (index + 1).toDouble())
            }

            // --- 2. Build Linear Regression Models (COMMENTED OUT) ---
            val distanceModel = createLinearModel(cumulativeDistancePoints)
            val fuelModel = createLinearModel(cumulativeFuelPoints)
            val ridesModel = createLinearModel(cumulativeRidesPoints)

            // --- 3. Predict Future Values for the Next Month (COMMENTED OUT) ---
            val lastDay = cumulativeRidesPoints.last().first
            val futureDay = lastDay + 30 // Hardcoding to 30 days for "Next Month"

            // First, predict the new CUMULATIVE total
            val predictedCumulativeDistance = predict(distanceModel, futureDay.toDouble())
            val predictedCumulativeFuel = predict(fuelModel, futureDay.toDouble())
            val predictedCumulativeRides = predict(ridesModel, futureDay.toDouble())

            // Then, find the predicted value for JUST the next 30 days
            // Coerce each value to be at least 0.0. It'''s not logical to predict negative rides or fuel.
            val predictedDistanceForPeriod = (predictedCumulativeDistance - currentDistance).coerceAtLeast(0.0)
            val predictedFuelForPeriod = (predictedCumulativeFuel - currentFuel).coerceAtLeast(0.0)
            val predictedRidesForPeriod = (predictedCumulativeRides - rides.size).coerceAtLeast(0.0)


            val predictedEfficiency = if (predictedFuelForPeriod > 0) predictedDistanceForPeriod / predictedFuelForPeriod else 0.0

            // --- 4. Create a PredictionData object and update LiveData (COMMENTED OUT) ---
            val predictionResult = PredictionData(
                rides = "Total Rides: ${predictedRidesForPeriod.toInt()}",
                fuel = "Total Fuel: %.1f L".format(predictedFuelForPeriod),
                distance = "Total Distance: %.1f km".format(predictedDistanceForPeriod),
                efficiency = "Avg. Efficiency: %.1f km/L".format(predictedEfficiency)
            )

            _monthPrediction.value = predictionResult
            */

            _isLoading.value = false
        }
    }

    /*
    /**
     * Calculates the slope and intercept for a given set of data points. (COMMENTED OUT)
     */
    private fun createLinearModel(points: List<Pair<Long, Double>>): LinearModel {
        val n = points.size
        if (n == 0) return LinearModel(0.0, 0.0)

        val sumX = points.sumOf { it.first.toDouble() }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first.toDouble() * it.second }
        val sumX2 = points.sumOf { it.first.toDouble().pow(2) }

        val denominator = (n * sumX2 - sumX.pow(2))
        if (denominator == 0.0) { // Avoid division by zero
            return LinearModel(0.0, sumY / n) // Return a flat line
        }

        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        return LinearModel(slope, intercept)
    }

    /**
     * Predicts a '''y''' value for a given '''x''' using a calculated model. (COMMENTED OUT)
     * Ensures the prediction is not negative.
     */
    private fun predict(model: LinearModel, futureX: Double): Double {
        return (model.slope * futureX + model.intercept).coerceAtLeast(0.0)
    }
    */

    /**
     * Updates the LiveData with a message indicating not enough data is available.
     */
    private fun setNotEnoughDataState() {
        val notEnoughDataPrediction = PredictionData(
            rides = "Not enough data to predict",
            fuel = "",
            distance = "",
            efficiency = ""
        )
        _monthPrediction.value = notEnoughDataPrediction
    }
}
