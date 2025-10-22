

// Model

package com.example.rideistics.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application // Keep for AndroidViewModel if not changing factory method fully
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel // Changed from AndroidViewModel for factory
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
//import androidx.preference.forEach
//import androidx.preference.isEmpty
import com.example.rideistics.data.repository.RideRepository
import com.example.rideistics.data.db.RideData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// Changed constructor to accept Repository
class HomeViewModel(private val repository: RideRepository) : ViewModel() {

    // Application context can be obtained from the repository if needed, or passed if essential
    // For FusedLocationProviderClient, we'll need application context.
    // One way is to pass it to init or have the factory provide it if HomeViewModel still extends AndroidViewModel.
    // For simplicity, let's assume we can get it or the factory handles this.
    // Let's revert to AndroidViewModel and have the factory handle Application context
    // This requires HomeViewModelFactory to be an ViewModelProvider.AndroidViewModelFactory
    // For now, let's keep it ViewModel and assume context is passed/available if needed for FusedLocation.
    // Actually, FusedLocationProviderClient needs context. Let's make the factory handle it.
    // The factory will need the Application context.

    private val _applicationContext = MutableLiveData<Application>() // Hacky, better if Factory handles AndroidViewModel

    fun initialize(application: Application) {
        _applicationContext.value = application
        if (!::fusedLocationClient.isInitialized && _applicationContext.value != null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(_applicationContext.value!!)
            setupLocationCallback()
        }
    }


    private val _timerDisplay = MutableLiveData<String>().apply { value = "00:00:00" }
    val timerDisplay: LiveData<String> = _timerDisplay

    private val _isTimerRunning = MutableLiveData<Boolean>().apply { value = false }
    val isTimerRunning: LiveData<Boolean> = _isTimerRunning

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var tripStartTimeMillis: Long = 0L
    private var seconds = 0
    private var timerJob: Job? = null

    private val _currentSpeedMps = MutableLiveData(0.0f)
    @SuppressLint("DefaultLocale")
    val currentSpeedKmh: LiveData<String> = _currentSpeedMps.map { speedMps ->
        String.format("Speed: %.1f km/h", speedMps * 3.6f)
    }
    private var currentSpeedKmhInternal: Float = 0.0f
    private val _maxSpeedKmh = MutableLiveData(0.0f)

    private val _totalDistanceMeters = MutableLiveData(0.0f)
    @SuppressLint("DefaultLocale")
    val totalDistanceKm: LiveData<String> = _totalDistanceMeters.map { distanceM ->
        String.format("Distance: %.2f km", distanceM / 1000.0f)
    }

    private val _customMileage = MutableLiveData<String?>()
    val customMileage: LiveData<String?> = _customMileage
    private var baseMileageKmL: Float? = null

    private val _adjustedMileageKmL = MutableLiveData<Float?>()

    // For calculating average real mileage
    private var sumOfAdjustedMileages: Double = 0.0
    private var countOfMileageReadings: Int = 0

    private val _totalFuelConsumedLiters = MutableLiveData(0.0f)
    @SuppressLint("DefaultLocale")
    val totalFuelConsumedLitersDisplay: LiveData<String> = _totalFuelConsumedLiters.map {
        String.format("Fuel Consumed: %.2f L", it)
    }
    private var distanceAccumulatorForFuelCalcMeters = 0.0f

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var previousLocation: Location? = null
    private var startLocation: Location? = null // For trip_Start_Location
    private var endLocation: Location? = null   // For trip_End_Location
    private var locationUpdatesActive = false
    private var startTrackingPendingPermission = false

    // Date and Time Formatters
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())


    // init {
    // Moved to initialize() because FusedLocationProviderClient needs context from Application
    // if (_applicationContext.value != null) {
    // fusedLocationClient = LocationServices.getFusedLocationProviderClient(_applicationContext.value!!)
    // setupLocationCallback()
    //  }
    // }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { newLocation ->
                    if (_isTimerRunning.value == false && previousLocation != null) {
                        return
                    }

                    endLocation = newLocation // Continuously update end location while tracking

                    val speedMps = if (newLocation.hasSpeed() && newLocation.speedAccuracyMetersPerSecond > 0 && newLocation.speed > 0.05) {
                        newLocation.speed
                    } else {
                        var calculatedSpeedMps = 0.0f
                        previousLocation?.let { prevLoc ->
                            val timeDifferenceSeconds = (newLocation.time - prevLoc.time) / 1000.0f
                            if (timeDifferenceSeconds > 0.5f) { // Min time diff to avoid erratic speeds
                                val distanceSegment = prevLoc.distanceTo(newLocation)
                                calculatedSpeedMps = distanceSegment / timeDifferenceSeconds
                            }
                        }
                        if (calculatedSpeedMps < 0.1 && (!newLocation.hasSpeed() || newLocation.speed < 0.1)) 0.0f else max(0f, calculatedSpeedMps)
                    }

                    if (_isTimerRunning.value == true) {
                        _currentSpeedMps.value = speedMps
                        currentSpeedKmhInternal = speedMps * 3.6f

                        if (currentSpeedKmhInternal > (_maxSpeedKmh.value ?: 0.0f)) {
                            _maxSpeedKmh.value = currentSpeedKmhInternal
                        }

                        baseMileageKmL?.let { base ->
                            val currentAdjustedMileage = calculateAdjustedMileage(currentSpeedKmhInternal, base)
                            _adjustedMileageKmL.value = currentAdjustedMileage
                            // Accumulate for average real mileage
                            sumOfAdjustedMileages += currentAdjustedMileage
                            countOfMileageReadings++
                        }

                        previousLocation?.let { prevLoc ->
                            if (newLocation.accuracy < 30.0f) {
                                val distanceSegmentMeters = prevLoc.distanceTo(newLocation)
                                _totalDistanceMeters.value = (_totalDistanceMeters.value ?: 0.0f) + distanceSegmentMeters

                                if (baseMileageKmL != null && (_adjustedMileageKmL.value ?: 0f) > 0f) {
                                    distanceAccumulatorForFuelCalcMeters += distanceSegmentMeters
                                    if (distanceAccumulatorForFuelCalcMeters >= 5.0f) {
                                        val adjustedMileage = _adjustedMileageKmL.value!!
                                        val fuelForThisChunkLiters = (distanceAccumulatorForFuelCalcMeters / 1000.0f) / adjustedMileage
                                        _totalFuelConsumedLiters.value = (_totalFuelConsumedLiters.value ?: 0.0f) + fuelForThisChunkLiters
                                        distanceAccumulatorForFuelCalcMeters %= 5.0f
                                    }
                                }
                            }
                        }
                    }
                    if (startLocation == null && _isTimerRunning.value == true) { // Capture start location once tracking starts
                        startLocation = newLocation
                    }
                    previousLocation = newLocation
                }
            }
        }
    }

    private fun calculateAdjustedMileage(currentSpeedKmh: Float, baseMileage: Float): Float {
        val percentageDrop = when {
            currentSpeedKmh <= 55 -> 0.0f
            currentSpeedKmh <= 65 -> 0.05f
            currentSpeedKmh <= 75 -> 0.175f
            currentSpeedKmh <= 97 -> 0.225f
            currentSpeedKmh <= 121 -> 0.275f
            currentSpeedKmh <= 137 -> 0.275f
            else -> 0.335f
        }
        val adjusted = baseMileage * (1 - percentageDrop)
        return if (adjusted > 0) adjusted else 0.01f
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onPlayPauseClicked() {
        if (_applicationContext.value == null) {
            _toastMessage.value = "Error: Location services not initialized."
            return
        }
        if (_isTimerRunning.value == true) {
            pauseTracking()
        } else {
            if (baseMileageKmL == null || baseMileageKmL!! <= 0f) {
                _toastMessage.value = "Please set a valid mileage first 🙏"
                return
            }
            // MODIFIED CONDITION: Check if 'seconds' is 0 to determine a new trip
            if (seconds == 0) { // This implies a fresh start for tracking logic
                startTrackingPendingPermission = true // Might be used for permission handling flow
                // Reset accumulators for average real mileage here for a completely new trip start
                sumOfAdjustedMileages = 0.0
                countOfMileageReadings = 0
            }
            startTracking()
        }
    }

    fun onRecordAndResetClicked() {
        if (_applicationContext.value == null) {
            _toastMessage.value = "Error: App not fully initialized."
            return
        }
        val tripEndTimeMillis = System.currentTimeMillis()
        saveTripData(tripEndTimeMillis) // Save data before resetting values

        // Reset state
        _isTimerRunning.value = false
        timerJob?.cancel()
        seconds = 0
        _timerDisplay.value = formatTime(seconds)

        stopLocationUpdates()
        _currentSpeedMps.value = 0.0f
        currentSpeedKmhInternal = 0.0f
        _maxSpeedKmh.value = 0.0f
        _totalDistanceMeters.value = 0.0f
        previousLocation = null
        startLocation = null
        endLocation = null
        startTrackingPendingPermission = false

        _customMileage.value = null
        baseMileageKmL = null
        _adjustedMileageKmL.value = null
        _totalFuelConsumedLiters.value = 0.0f
        distanceAccumulatorForFuelCalcMeters = 0.0f
        tripStartTimeMillis = 0L

        // Reset accumulators for average real mileage
        sumOfAdjustedMileages = 0.0
        countOfMileageReadings = 0

        // For debugging, log all rides after saving and resetting
        logAllRideData()
    }

    private fun saveTripData(tripEndTimeMillis: Long) {
        val currentTripStartTimeMillis = tripStartTimeMillis // Capture before it's reset
        if (currentTripStartTimeMillis == 0L) {
            Log.w("HomeViewModel", "Start time not recorded, cannot save trip.")
            _toastMessage.value = "Start a trip first genius 🤡."
            return
        }
        if (baseMileageKmL == null) {
            Log.w("HomeViewModel", "Base mileage not set, cannot save trip accurately.")
            _toastMessage.value = "Error: Could not save trip, mileage missing."
            return
        }

        val calculatedTripRealMileageKmL = if (countOfMileageReadings > 0) {
            (sumOfAdjustedMileages / countOfMileageReadings).toFloat()
        } else {
            0.0f // Default or handle as appropriate if no readings
        }

        val ride = RideData(
            tripDate = dateFormat.format(Date(currentTripStartTimeMillis)),
            tripStartTime = timeFormat.format(Date(currentTripStartTimeMillis)),
            tripEndTime = timeFormat.format(Date(tripEndTimeMillis)),
            tripDurationSeconds = seconds.toLong(),
            tripDistance = (_totalDistanceMeters.value?: 0.0f) / 1000.0f, // Value in KM
            tripAvgSpeedKmh = if (seconds > 0 && (_totalDistanceMeters.value ?: 0.0f) > 0) {
                ((_totalDistanceMeters.value!! / 1000.0f) / (seconds / 3600.0f))
            } else {
                0.0f
            },
            tripMaxSpeedKmh = _maxSpeedKmh.value ?: 0.0f,
            tripFuelConsumedLiters = _totalFuelConsumedLiters.value ?: 0.0f,
            tripSetMileageKmL = baseMileageKmL!!,
            tripRealMileageKmL = calculatedTripRealMileageKmL
        )

        viewModelScope.launch {
            val rowId = repository.insertRide(ride)
            if (rowId > 0) {
                Log.i("HomeViewModel", "Trip saved successfully with ID: $rowId")
            } else {
                Log.e("HomeViewModel", "Failed to save trip.")
                _toastMessage.postValue("Error saving trip data.")
            }
        }
        _toastMessage.value = "Trip recorded 😁🎉."
    }


    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun requestStartTrackingAfterPermission() {
        if (_applicationContext.value == null) {
            _toastMessage.value = "Error: App not initialized for location."
            startTrackingPendingPermission = false
            return
        }
        if (baseMileageKmL == null || baseMileageKmL!! <= 0f) {
            _toastMessage.value = "Set a valid mileage first 🙏"
            startTrackingPendingPermission = false
            return
        }
        if (startTrackingPendingPermission) {
            if (previousLocation == null) { // Ensure reset for new trip if pending permission led to this start
                sumOfAdjustedMileages = 0.0
                countOfMileageReadings = 0
            }
            startTracking()
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startTracking() {
        if (_applicationContext.value == null) {
            _toastMessage.value = "Error: App not initialized for location updates."
            if (_isTimerRunning.value != false) _isTimerRunning.value = false
            return
        }
        if (baseMileageKmL == null || baseMileageKmL!! <= 0f) {
            _toastMessage.value = "Internal error: Mileage not set before starting."
            if (_isTimerRunning.value != false) _isTimerRunning.value = false
            return
        }

        if (ActivityCompat.checkSelfPermission(_applicationContext.value!!, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(_applicationContext.value!!, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("HomeViewModel", "Start tracking called without permission.")
            _isTimerRunning.value = false
            return
        }

        startTrackingPendingPermission = false
        _isTimerRunning.value = true
//        _toastMessage.value = "Trip Started 🏍️🍃"

        if(tripStartTimeMillis == 0L){
            _toastMessage.value = "Trip Started 🏍️🍃"
        } else{
            _toastMessage.value = "Trip Resumed 🟢🟢🟢"
        }


        if (seconds == 0) { // Fresh start
            seconds = 0
            _timerDisplay.value = formatTime(seconds)
            _totalDistanceMeters.value = 0.0f
            _totalFuelConsumedLiters.value = 0.0f
            distanceAccumulatorForFuelCalcMeters = 0.0f
            _currentSpeedMps.value = 0.0f
            currentSpeedKmhInternal = 0.0f
            _maxSpeedKmh.value = 0.0f
            startLocation = null // Reset start location for the new trip
            endLocation = null
            tripStartTimeMillis = System.currentTimeMillis() // Record start time for the trip
            // Reset accumulators for average real mileage for a fresh start
            sumOfAdjustedMileages = 0.0
            countOfMileageReadings = 0
        }
        // else, it's a resume

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isTimerRunning.value == true) {
                delay(1000)
                seconds++
                _timerDisplay.postValue(formatTime(seconds))
            }
        }
        startLocationUpdates()
    }

    private fun pauseTracking() {
        _isTimerRunning.value = false
        _toastMessage.value = "Trip paused 🛑🛑🛑"
        timerJob?.cancel()
        stopLocationUpdates()
        _currentSpeedMps.postValue(0.0f)
        currentSpeedKmhInternal = 0.0f
    }


    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        if (_applicationContext.value == null || (ActivityCompat.checkSelfPermission(_applicationContext.value!!, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(_applicationContext.value!!, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        ) {
            Log.e("HomeViewModel", "Attempted to start location updates without permission or context.")
            _isTimerRunning.value = false // Stop tracking if we can't get updates
            return
        }
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            locationUpdatesActive = true
            Log.d("HomeViewModel", "Location updates started.")
        } catch (e: SecurityException) {
            Log.e("HomeViewModel", "SecurityException in startLocationUpdates: ${e.message}")
            _isTimerRunning.value = false
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesActive = false
            Log.d("HomeViewModel", "Location updates stopped.")
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }

    fun setCustomMileage(mileage: String) {
        try {
            val mileageFloat = mileage.toFloatOrNull()
            if (mileageFloat != null && mileageFloat > 0) {
                baseMileageKmL = mileageFloat
                _customMileage.value = mileage
                _toastMessage.value = "Mileage set to: $mileage km/L"
                if(_isTimerRunning.value == true && baseMileageKmL != null) { // Added null check for baseMileageKmL
                    val currentAdjustedMileage = calculateAdjustedMileage(currentSpeedKmhInternal, baseMileageKmL!!)
                    _adjustedMileageKmL.value = currentAdjustedMileage
                    // If tracking is active, this new base mileage might affect ongoing average calculation
                    // For simplicity, we assume average calculation starts fresh with tracking.
                    // If a mid-trip base mileage change should retroactively affect the average,
                    // this would need more complex logic.
                } else if (baseMileageKmL != null) { // Added null check for baseMileageKmL
                    _adjustedMileageKmL.value = calculateAdjustedMileage(0f, baseMileageKmL!!)
                }
            } else {
                baseMileageKmL = null
                _customMileage.value = mileage
                _toastMessage.value = "Invalid mileage: $mileage. Must be a positive number."
            }
        } catch (_: NumberFormatException) {
            baseMileageKmL = null
            _customMileage.value = mileage
            _toastMessage.value = "Invalid mileage format: $mileage."
        }
    }

    private fun logAllRideData() {
        viewModelScope.launch {
            val rides = repository.getAllRidesList()
            if (rides.isEmpty()) {
                Log.i("HomeViewModel_RideData", "No rides found in the database.")
                return@launch
            }
            val logOutput = StringBuilder("--- Ride Data Log ---\n")
            rides.forEach { ride ->
                logOutput.append("ID: ${ride.tripID}\n")
                logOutput.append("  Date: ${ride.tripDate}, Start: ${ride.tripStartTime}, End: ${ride.tripEndTime}\n")
                logOutput.append("  Duration: ${ride.tripDurationSeconds}s, Distance: ${"%.2f".format(ride.tripDistance)}km\n")
                logOutput.append("  Avg Speed: ${"%.1f".format(ride.tripAvgSpeedKmh)}km/h, Max Speed: ${"%.1f".format(ride.tripMaxSpeedKmh)}km/h\n")
                logOutput.append("  Fuel: ${"%.2f".format(ride.tripFuelConsumedLiters)}L, Set Mileage: ${"%.1f".format(ride.tripSetMileageKmL)}km/L\n")
                logOutput.append("  Avg Real Mileage: ${"%.1f".format(ride.tripRealMileageKmL)}km/L\n")
                logOutput.append("---------------------\n")
            }
            Log.i("HomeViewModel_RideData", logOutput.toString())
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopLocationUpdates()
    }
}
