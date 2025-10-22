package com.example.rideistics.ui.dashboard

import android.icu.util.Calendar
import android.util.Log
import androidx.core.text.color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rideistics.data.db.RideData
import com.example.rideistics.data.repository.RideRepository
import com.example.rideistics.utils.Event
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BubbleData
import com.github.mikephil.charting.data.BubbleDataSet
import com.github.mikephil.charting.data.BubbleEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Add a TAG for logging
private const val TAG = "DashboardViewModel_Debug"

class DashboardViewModel(private val repository: RideRepository) : ViewModel() {

    private val _periodSelector = MutableLiveData<List<PeriodSelector>>()
    val periodSelector: LiveData<List<PeriodSelector>> = _periodSelector

    private val _selectedPeriod = MutableLiveData<PeriodSelector>()

    private val _totalRidesText = MutableLiveData<String>()
    val totalRidesText: LiveData<String> = _totalRidesText

    private val _totalFuelSpentText = MutableLiveData<String>()
    val totalFuelSpentText: LiveData<String> = _totalFuelSpentText

    private val _totalDistanceText = MutableLiveData<String>()
    val totalDistanceText: LiveData<String> = _totalDistanceText

    private val _avgFuelEfficiencyText = MutableLiveData<String>()
    val avgFuelEfficiencyText: LiveData<String> = _avgFuelEfficiencyText

    private val _showDateRangePickerEvent = MutableLiveData<Event<Unit>>()
    val showDateRangePickerEvent: LiveData<Event<Unit>> = _showDateRangePickerEvent

    // LiveData for Chart Data
    private val _barChartDataLiveData = MutableLiveData<BarData?>()
    val barChartDataLiveData: LiveData<BarData?> = _barChartDataLiveData

    private val _lineChartDataLiveData = MutableLiveData<LineData?>()
    val lineChartDataLiveData: LiveData<LineData?> = _lineChartDataLiveData

    private val _pieChartDataLiveData = MutableLiveData<PieData?>()
    val pieChartDataLiveData: LiveData<PieData?> = _pieChartDataLiveData

    // Event for navigating to FutureRidesFragment
    private val _navigateToFutureRidesEvent = MutableLiveData<Event<Unit>>()
    val navigateToFutureRidesEvent: LiveData<Event<Unit>> = _navigateToFutureRidesEvent

    private var customStartDateMillis: Long? = null
    private var customEndDateMillis: Long? = null

    init {
        Log.d(TAG, "ViewModel initialized")
        loadPeriodSelector()
//        insertFakeRideDataForTesting()
    }

    private fun loadPeriodSelector() {
        Log.d(TAG, "loadPeriodSelector called")
        val exampleItems = listOf(
            PeriodSelector(1, "Today"),
            PeriodSelector(2, "This Week"),
            PeriodSelector(3, "This Month"),
            PeriodSelector(4, "Last 6 Months"),
            PeriodSelector(5, "This Year"),
            PeriodSelector(6, "All time"),
            PeriodSelector(7, "Select range"),
            PeriodSelector(9, "Estimate Rides") // Added new option
        )
        _periodSelector.value = exampleItems
        // Ensure a default period is selected if not "Select range" to trigger data load
        if (_selectedPeriod.value == null && exampleItems.isNotEmpty() && exampleItems[0].displayName != "Select range") {
            Log.d(
                TAG,
                "loadPeriodSelector: Setting initial period to ${exampleItems[0].displayName}"
            )
            setSelectedPeriod(exampleItems[0])
        } else if (_selectedPeriod.value == null) {
            // If the first item is "Select range" or list is empty, initialize to empty/N/A state
            Log.d(
                TAG,
                "loadPeriodSelector: No initial period or 'Select range' is first. Setting KPIs to N/A and graphs to empty."
            )
            updateKpiText(
                null,
                null,
                null,
                null,
                isError = false,
                errorMessage = null,
                isInitialEmpty = true
            )
            updateGraphData(emptyList()) // Update charts with empty data initially
        }
    }

    fun setSelectedPeriod(period: PeriodSelector) {
        Log.d(TAG, "setSelectedPeriod: User selected '${period.displayName}'")
        _selectedPeriod.value = period // Update the selected period LiveData

        if (period.displayName == "Estimate Rides") {
            Log.d(TAG, "setSelectedPeriod: 'Estimate Rides' chosen, triggering navigation event.")
            _navigateToFutureRidesEvent.value = Event(Unit)
            // Optionally, you might want to clear KPIs or charts here, or leave them as is
            // For now, let's assume the user navigates away, so current dashboard state can remain
        } else if (period.displayName == "Select range") {
            Log.d(TAG, "setSelectedPeriod: 'Select range' chosen, prompting for dates.")
            // Clear KPIs and Charts, then show date picker
            updateKpiText(
                null,
                null,
                null,
                null,
                isError = false,
                errorMessage = null,
                isSelectRange = true
            )
            updateGraphData(emptyList())
            _showDateRangePickerEvent.value = Event(Unit)
        } else {
            // For any other period, clear custom dates and update data
            customStartDateMillis = null
            customEndDateMillis = null
            Log.d(TAG, "setSelectedPeriod: Regular period selected. Fetching data.")
            updateKpiAndChartData()
        }
    }

    fun setCustomDateRangeAndFetchKpis(startDateMillis: Long, endDateMillis: Long) {
        Log.d(
            TAG,
            "setCustomDateRangeAndFetchKpis: StartDateMillis=$startDateMillis, EndDateMillis=$endDateMillis"
        )
        this.customStartDateMillis = startDateMillis
        this.customEndDateMillis = endDateMillis
        // Ensure _selectedPeriod reflects that a custom range is active
        // This is important if the user later re-selects "Select range" from spinner
        _periodSelector.value?.find { it.displayName == "Select range" }?.let {
            if (_selectedPeriod.value != it) {
                Log.d(
                    TAG,
                    "setCustomDateRangeAndFetchKpis: Updating _selectedPeriod to 'Select range' to reflect custom dates."
                )
                _selectedPeriod.value =
                    it // Silently update to keep state consistent, or choose to expose this change more explicitly
            }
        }
        Log.d(TAG, "setCustomDateRangeAndFetchKpis: Fetching data for custom range.")
        updateKpiAndChartData()
    }

    private fun calculateQueryDateStrings(
        period: PeriodSelector?,
        customStartMillis: Long?,
        customEndMillis: Long?
    ): Pair<String?, String?> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var startDateString: String? = null
        var endDateString: String? = null

        Log.d(
            TAG,
            "calculateQueryDateStrings: Input Period='${period?.displayName}', CustomStartMillis=$customStartMillis, CustomEndMillis=$customEndMillis"
        )

        if (period?.displayName == "Select range" && customStartMillis != null && customEndMillis != null) {
            startDateString = dateFormat.format(customStartMillis)
            endDateString = dateFormat.format(customEndMillis)
            Log.d(TAG, "calculateQueryDateStrings: Using custom date range.")
        } else if (period != null) {
//            val calendar = Calendar.getInstance() // Use a new instance for each calculation block
            Log.d(
                TAG,
                "calculateQueryDateStrings: Calculating for predefined period: ${period.displayName}"
            )
            when (period.displayName) {
                "Today" -> {
                    val todayStr = dateFormat.format(Calendar.getInstance().time)
                    startDateString = todayStr
                    endDateString = todayStr
                }

                "This Week" -> {
                    val cal = Calendar.getInstance()
                    cal.firstDayOfWeek = Calendar.MONDAY // Set first day to Monday
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    startDateString = dateFormat.format(cal.time)
                    cal.add(Calendar.DAY_OF_WEEK, 6) // Go to Sunday
                    endDateString = dateFormat.format(cal.time)
                }

                "This Month" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    startDateString = dateFormat.format(cal.time)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    endDateString = dateFormat.format(cal.time)
                }

                "Last 6 Months" -> {
                    val endCal = Calendar.getInstance() // Ends today
                    endDateString = dateFormat.format(endCal.time)
                    val startCal = Calendar.getInstance()
                    startCal.add(Calendar.MONTH, -6) // Go back 6 months
                    // Optional: adjust to start of that month or specific day logic
                    startDateString = dateFormat.format(startCal.time)
                }

                "This Year" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_YEAR, 1)
                    startDateString = dateFormat.format(cal.time)
                    endDateString =
                        dateFormat.format(Calendar.getInstance().time) // Up to current date
                }

                "All time" -> {
                    startDateString = null // No date filtering
                    endDateString = null
                }

                // "Estimate Rides" is handled by navigation event, no date calculation needed here for it.
                // else for other unknown periods:
                else -> {
                    if (period.displayName != "Estimate Rides") { // Only log warning if it's not the navigation one
                        Log.w(
                            TAG,
                            "calculateQueryDateStrings: Unknown period display name: ${period.displayName}"
                        )
                    }
                    startDateString = null
                    endDateString = null
                }
            }
        }
        Log.d(
            TAG,
            "calculateQueryDateStrings RESULT: StartDateStr='$startDateString', EndDateStr='$endDateString' for period '${period?.displayName}'"
        )
        return Pair(startDateString, endDateString)
    }

    private fun updateKpiText(
        rides: Int?,
        fuel: Double?,
        distance: Double?,
        efficiency: Double?,
        isError: Boolean,
        errorMessage: String?,
        isSelectRange: Boolean = false,
        isInitialEmpty: Boolean = false,
        isInvalidPeriod: Boolean = false
    ) {
        // This function updates KPI LiveData. Logs here can be added if KPIs also show issues.
        // For now, focusing on chart data.
        if (isError) {
            val errorMsg = "Error: ${errorMessage ?: "Unknown"}"
            _totalRidesText.value = "Total Rides: $errorMsg"
            _totalFuelSpentText.value = "Total Fuel: $errorMsg"
            _totalDistanceText.value = "Total Distance: $errorMsg"
            _avgFuelEfficiencyText.value = "Avg. Efficiency: $errorMsg"
        } else if (isSelectRange) {
            _totalRidesText.value = "Total Rides: Select date range"
            _totalFuelSpentText.value = "Total Fuel: Select date range"
            _totalDistanceText.value = "Total Distance: Select date range"
            _avgFuelEfficiencyText.value = "Avg. Efficiency: Select date range"
        } else if (isInitialEmpty) {
            _totalRidesText.value = "Total Rides: N/A"
            _totalFuelSpentText.value = "Total Fuel: N/A"
            _totalDistanceText.value = "Total Distance: N/A"
            _avgFuelEfficiencyText.value = "Avg. Efficiency: N/A"
        } else if (isInvalidPeriod) {
            _totalRidesText.value = "Total Rides: Invalid Period"
            _totalFuelSpentText.value = "Total Fuel: Invalid Period"
            _totalDistanceText.value = "Total Distance: Invalid Period"
            _avgFuelEfficiencyText.value = "Avg. Efficiency: Invalid Period"
        } else {
            _totalRidesText.value = "Total Rides: ${rides ?: "0"}"
            _totalFuelSpentText.value =
                "Total Fuel: ${if (fuel != null) "%.2f".format(fuel) else "0.00"} L"
            _totalDistanceText.value =
                "Total Distance: ${if (distance != null) "%.1f".format(distance) else "0.0"} km"
            _avgFuelEfficiencyText.value = "Avg. Efficiency: ${
                if (efficiency != null && efficiency > 0) "%.1f".format(efficiency) else "N/A"
            } km/L"
        }
    }

    private fun updateKpiAndChartData() {
        val currentPeriod = _selectedPeriod.value
        Log.d(
            TAG,
            "updateKpiAndChartData: Triggered for period: ${currentPeriod?.displayName}, CustomStart: $customStartDateMillis, CustomEnd: $customEndDateMillis"
        )

        // If "Estimate Rides" is selected, do not proceed with KPI/Chart update for dashboard
        if (currentPeriod?.displayName == "Estimate Rides") {
            Log.d(TAG, "updateKpiAndChartData: 'Estimate Rides' is selected, skipping KPI and chart data update for dashboard.")
            return
        }

        // Crucial: ensure currentPeriod is not null if not using custom dates.
        // If currentPeriod is null and custom dates are also null, it's an invalid state.
        if (currentPeriod == null && (customStartDateMillis == null || customEndDateMillis == null)) {
            Log.w(
                TAG,
                "updateKpiAndChartData: currentPeriod is null and no custom dates set. Aborting."
            )
            updateKpiText(
                null,
                null,
                null,
                null,
                isError = false,
                errorMessage = null,
                isInvalidPeriod = true
            )
            updateGraphData(emptyList())
            return
        }


        val (startDateQueryString, endDateQueryString) = calculateQueryDateStrings(
            currentPeriod, // This could be "Select range" if custom dates are set
            customStartDateMillis,
            customEndDateMillis
        )
        val isToday =
            currentPeriod?.displayName == "Today" && customStartDateMillis == null // isToday only if not custom range

        Log.d(
            TAG,
            "updateKpiAndChartData: Querying Repository with StartDate='$startDateQueryString', EndDate='$endDateQueryString', isToday=$isToday"
        )

        // Validate that if a specific period (not "All time" or "Select range") is chosen, dates are generated.
        if (currentPeriod?.displayName != "All time" && currentPeriod?.displayName != "Select range" && (startDateQueryString == null || endDateQueryString == null)) {
            Log.e(
                TAG,
                "updateKpiAndChartData: Date strings are null for a period that requires them ('${currentPeriod?.displayName}'). This is a bug in calculateQueryDateStrings."
            )
            updateKpiText(
                null,
                null,
                null,
                null,
                isError = false,
                errorMessage = null,
                isInvalidPeriod = true
            )
            updateGraphData(emptyList())
            return
        }
        // If "Select range" is the currentPeriod but custom dates are not set (e.g. user selected it from spinner)
        if (currentPeriod?.displayName == "Select range" && (customStartDateMillis == null || customEndDateMillis == null)) {
            Log.d(
                TAG,
                "updateKpiAndChartData: 'Select range' is active, but custom dates are not set. Waiting for date picker."
            )
            // KPIs and charts should already be cleared by setSelectedPeriod, but being safe:
            updateKpiText(
                null,
                null,
                null,
                null,
                isError = false,
                errorMessage = null,
                isSelectRange = true
            )
            updateGraphData(emptyList())
            return
        }


        viewModelScope.launch {
            try {
                Log.d(TAG, "updateKpiAndChartData Coroutine: Fetching KPIs...")
                val totalRides = repository.getTotalRidesForPeriod(
                    startDateQueryString,
                    endDateQueryString,
                    isToday
                )
                val totalFuel = repository.getTotalFuelSpentForPeriod(
                    startDateQueryString,
                    endDateQueryString,
                    isToday
                ) ?: 0.0
                val totalDistance = repository.getTotalDistanceForPeriod(
                    startDateQueryString,
                    endDateQueryString,
                    isToday
                ) ?: 0.0
                val avgEfficiency = repository.calculateAverageFuelEfficiency(
                    startDateQueryString,
                    endDateQueryString,
                    isToday
                ) ?: 0.0
                Log.d(
                    TAG,
                    "updateKpiAndChartData Coroutine: KPIs fetched - Rides=$totalRides, Fuel=$totalFuel, Dist=$totalDistance, AvgEff=$avgEfficiency"
                )
                updateKpiText(
                    totalRides,
                    totalFuel,
                    totalDistance,
                    avgEfficiency,
                    isError = false,
                    errorMessage = null
                )

                Log.d(TAG, "updateKpiAndChartData Coroutine: Fetching ridesList for charts...")
                val ridesList = repository.getRidesListForPeriod(
                    startDateQueryString,
                    endDateQueryString,
                    isToday
                )
                Log.d(
                    TAG,
                    "updateKpiAndChartData Coroutine: ridesList fetched, size = ${ridesList.size}"
                )
                updateGraphData(ridesList)

            } catch (e: Exception) {
                Log.e(TAG, "updateKpiAndChartData Coroutine: Exception while fetching data", e)
                updateKpiText(null, null, null, null, isError = true, errorMessage = e.message)
                updateGraphData(emptyList()) // Clear charts on error
            }
        }
    }

    // New function to handle graph data updates
    private fun updateGraphData(ridesList: List<RideData>) {
        Log.d(TAG, "updateGraphData: Received ridesList of size ${ridesList.size} for processing.")
        if (ridesList.isNotEmpty()) {
            val barData = prepareBarChartData(ridesList)
            val lineData = prepareLineChartData(ridesList)
            val pieData = preparePieChartData(ridesList)

            Log.d(
                TAG,
                "updateGraphData: Prepared Data -> BarData isNull=${barData == null}, BubbleData isNull=${lineData == null}, pieData isNull=${pieData == null}"
            )
            if (barData != null) Log.d(
                TAG,
                "updateGraphData: BarData details - Entries=${barData.entryCount}, DataSets=${barData.dataSetCount}"
            )
            if (lineData != null) Log.d(
                TAG,
                "updateGraphData: BubbleData details - Entries=${lineData.entryCount}, DataSets=${lineData.dataSetCount}"
            )
            if (pieData != null) Log.d(
                TAG,
                "updateGraphData: pieData details - Entries=${pieData.entryCount}, DataSets=${pieData.dataSetCount}"
            )

            _barChartDataLiveData.postValue(barData)
            _lineChartDataLiveData.postValue(lineData)
            _pieChartDataLiveData.postValue(pieData)
        } else {
            Log.d(TAG, "updateGraphData: ridesList is empty. Posting null to all chart LiveData.")
            _barChartDataLiveData.postValue(null)
            _lineChartDataLiveData.postValue(null)
            _pieChartDataLiveData.postValue(null)
        }
    }

    private fun prepareBarChartData(rides: List<RideData>): BarData? {
        Log.d(TAG, "prepareBarChartData: Processing ${rides.size} rides.")
        if (rides.isEmpty()) {
            Log.d(TAG, "prepareBarChartData: No rides to process, returning null.")
            return null
        }
        // Efficiency (Y) vs. Avg Speed Buckets (X)
        // Buckets: <30, 30-60, 60-90, >90 km/h
        val speedBuckets =
            mapOf(
                0 to "0-5", 1 to "5-10", 2 to "10-15", 3 to "15-20",
                4 to "20-25", 5 to "25-30", 6 to "30-35", 7 to "35-40",
                8 to "40-45", 9 to "45-50", 10 to "50-55", 11 to "55-60",
                12 to "60-65", 13 to "65-70", 14 to "70-75", 15 to ">75"
            ) // Labels for X-axis
        val efficienciesPerBucket = MutableList(speedBuckets.size) { mutableListOf<Float>() }

        for (ride in rides) {
            val avgSpeed = ride.tripAvgSpeedKmh
            val efficiency = ride.tripRealMileageKmL
            if (efficiency > 0) { // Only consider valid efficiency readings
                when {
                    avgSpeed < 5 -> efficienciesPerBucket[0].add(efficiency)          // 0-5
                    avgSpeed < 10 -> efficienciesPerBucket[1].add(efficiency)         // 5-10
                    avgSpeed < 15 -> efficienciesPerBucket[2].add(efficiency)         // 10-15
                    avgSpeed < 20 -> efficienciesPerBucket[3].add(efficiency)         // 15-20
                    avgSpeed < 25 -> efficienciesPerBucket[4].add(efficiency)         // 20-25
                    avgSpeed < 30 -> efficienciesPerBucket[5].add(efficiency)         // 25-30
                    avgSpeed < 35 -> efficienciesPerBucket[6].add(efficiency)         // 30-35
                    avgSpeed < 40 -> efficienciesPerBucket[7].add(efficiency)         // 35-40
                    avgSpeed < 45 -> efficienciesPerBucket[8].add(efficiency)         // 40-45
                    avgSpeed < 50 -> efficienciesPerBucket[9].add(efficiency)         // 45-50
                    avgSpeed < 55 -> efficienciesPerBucket[10].add(efficiency)        // 50-55
                    avgSpeed < 60 -> efficienciesPerBucket[11].add(efficiency)        // 55-60
                    avgSpeed < 65 -> efficienciesPerBucket[12].add(efficiency)        // 60-65
                    avgSpeed < 70 -> efficienciesPerBucket[13].add(efficiency)        // 65-70
                    avgSpeed < 75 -> efficienciesPerBucket[14].add(efficiency)        // 70-75
                    avgSpeed >= 75 -> efficienciesPerBucket[15].add(efficiency)
                    // Ensure all speeds are covered if your data can go outside these ranges
                }
            }
        }

        val entries = ArrayList<BarEntry>()
        var validEntriesMade = 0
        for (i in efficienciesPerBucket.indices) {
            val avgEfficiencyInBucket = if (efficienciesPerBucket[i].isNotEmpty()) {
                efficienciesPerBucket[i].average().toFloat()
            } else {
                0f // No data for this bucket, show as 0 on chart
            }
            entries.add(BarEntry(i.toFloat(), avgEfficiencyInBucket))
            if (avgEfficiencyInBucket > 0f) validEntriesMade++
        }

        Log.d(
            TAG,
            "prepareBarChartData: Created ${entries.size} bar entries. $validEntriesMade have Y > 0."
        )

        // If no entries have any efficiency data (all are 0), it means no meaningful chart.
        if (validEntriesMade == 0 && entries.isNotEmpty()) { // Check if there are entries, but all are zero
            Log.d(
                TAG,
                "prepareBarChartData: All bar entries have Y=0 (no efficiency data in any bucket), returning null to hide chart."
            )
            return null
        }
        if (entries.isEmpty() && rides.isNotEmpty()) { // Should not happen if rides.isNotEmpty()
            Log.w(
                TAG,
                "prepareBarChartData: entries list is empty but rides list was not. Problem in bucketing logic. Returning null."
            )
            return null
        }
        if (entries.isEmpty()) { // rides list was empty, so entries is empty
            Log.d(
                TAG,
                "prepareBarChartData: entries list is empty because rides list was empty. Returning null."
            )
            return null
        }


        val dataSet = BarDataSet(entries, "Avg Efficiency by Speed (km/L)")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 10f
        dataSet.setDrawValues(false)
        return BarData(dataSet)
    }

    private fun prepareLineChartData(rides: List<RideData>): LineData? {
        Log.d(TAG, "prepareLineChartData: Processing ${rides.size} rides.")
        if (rides.isEmpty()) {
            Log.d(TAG, "prepareLineChartData: No rides to process, returning null.")
            return null
        }

        val entries = ArrayList<Entry>()
        for (ride in rides) {
            // Ensure duration and distance are positive for a meaningful plot point
            if (ride.tripDistance > 0 && ride.tripDurationSeconds > 0) {
                entries.add(
                    Entry(
                        ride.tripDistance,                      // X value (Distance)
                        ride.tripDurationSeconds.toFloat() / 60f  // Y value (Duration in minutes)
                    )
                )
            }
        }

        // IMPORTANT: Sort entries by X-value for LineChart
        entries.sortBy { it.x }

        Log.d(TAG, "prepareLineChartData: Created ${entries.size} valid line chart entries.")
        if (entries.isEmpty()) {
            Log.d(TAG, "prepareLineChartData: No valid line chart entries created, returning null.")
            return null
        }

        val dataSet = LineDataSet(entries, "Duration vs Distance")
        dataSet.color = ColorTemplate.getHoloBlue()
        dataSet.valueTextColor = android.graphics.Color.WHITE // For dark theme
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 4f
        dataSet.setCircleColor(ColorTemplate.getHoloBlue())
        dataSet.setDrawValues(true) // Show values on data points
        dataSet.valueTextSize = 9f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Optional: for a smoother line
        dataSet.setDrawValues(false)

        return LineData(dataSet)
    }
//    private fun prepareLineChartData(rides: List<RideData>): LineData? {
//        Log.d(TAG, "prepareBubbleChartData: Processing ${rides.size} rides.")
//        if (rides.isEmpty()) {
//            Log.d(TAG, "prepareBubbleChartData: No rides to process, returning null.")
//            return null
//        }
//        // X: Avg Speed, Y: Max Speed, Size: Duration
//        val entries = ArrayList<BubbleEntry>()
//        for (ride in rides) {
//            // Ensure duration is positive for meaningful bubble size
//            if (ride.tripDistance > 0 && ride.tripDurationSeconds > 0 && ride.tripFuelConsumedLiters > 0) {
//                entries.add(
//                    BubbleEntry(
//                        ride.tripDistance,      // x value
//                        ride.tripDurationSeconds.toFloat() / 60f,      // y value
//                        ride.tripFuelConsumedLiters
//                    )
//                )
//            }
//        }
//        Log.d(TAG, "prepareBubbleChartData: Created ${entries.size} valid bubble entries.")
//        if (entries.isEmpty()) { // If no valid rides for bubble chart
//            Log.d(
//                TAG,
//                "prepareBubbleChartData: No valid bubble entries created (all rides had 0 duration/speed?), returning null."
//            )
//            return null
//        }
//        val dataSet = BubbleDataSet(entries, "Distance vs Duration (Size: Fuel used)")
//        dataSet.setColors(*ColorTemplate.COLORFUL_COLORS)
//        dataSet.valueTextSize = 10f
//        return LineData(dataSet)
//    }

    // In DashboardViewModel.kt

    private fun preparePieChartData(rides: List<RideData>): PieData? {
        Log.d(TAG, "preparePieChartData: Processing ${rides.size} rides to count trips per time slot.")
        if (rides.isEmpty()) {
            Log.d(TAG, "preparePieChartData: No rides to process, returning null.")
            return null
        }

        val timeSlotLabels = listOf("Morning", "Midday", "Evening", "Night")
        // Stores the count of trips for each time slot
        val tripsPerSlot = MutableList(timeSlotLabels.size) { 0 } // Initialize counts to 0

        for (ride in rides) {
            try {
                val hour = ride.tripStartTime.split(":")[0].toInt() // Assumes tripStartTime is "HH:mm:ss"
                // No longer need efficiency for this version, just categorize by hour
                when (hour) {
                    in 6..9 -> tripsPerSlot[0]++   // Morning (6 AM - 9 AM)
                    in 10..15 -> tripsPerSlot[1]++ // Midday (10 AM - 3 PM)
                    in 16..19 -> tripsPerSlot[2]++ // Evening (4 PM - 7 PM)
                    else -> tripsPerSlot[3]++      // Night (all other hours including 8PM - 5AM)
                }
            } catch (e: Exception) {
                Log.e(TAG, "preparePieChartData: Error parsing tripStartTime for ride: $ride", e)
                // Continue with next ride if one has bad format
            }
        }

        val entries = ArrayList<PieEntry>()
        for (i in timeSlotLabels.indices) {
            val countInSlot = tripsPerSlot[i]
            if (countInSlot > 0) { // Only add slices if there are trips in that slot
                // Use the count as the value for the PieEntry
                entries.add(PieEntry(countInSlot.toFloat(), timeSlotLabels[i]))
            }
        }

        Log.d(TAG, "preparePieChartData: Created ${entries.size} pie entries based on trip counts.")

        if (entries.isEmpty()) { // If no trips fell into any slot (e.g., all had parsing errors or rides list was effectively empty for bucketing)
            Log.d(TAG, "preparePieChartData: No valid pie entries created (no trips in any time slot), returning null.")
            return null
        }

        val dataSet = PieDataSet(entries, "") // Label for the dataset (e.g., "Trips by Time of Day")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList() // Changed to COLORFUL_COLORS for variety, or keep MATERIAL_COLORS
        dataSet.yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        dataSet.valueLinePart1OffsetPercentage = 80f
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.4f
        // dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE); // if you want labels (categories) outside slices

        val pieData = PieData(dataSet)
        // If you want to show actual counts on slices:
        pieData.setValueFormatter(DefaultValueFormatter(0)) // 0 decimal places for counts
        // If you want to show percentages:
        // pieData.setValueFormatter(PercentFormatter(pieChart)) // Use pieChart instance if enabling percent values on chart itself
        // pieChart.setUsePercentValues(true) would be set in Fragment for PercentFormatter to work best.
        // For now, let's stick to DefaultValueFormatter for actual counts.

        pieData.setValueTextSize(12f) // Increased text size slightly
        pieData.setValueTextColor(android.graphics.Color.BLACK)
        dataSet.setDrawValues(true)


        return pieData
    }




//    private fun prepareRadarChartData(rides: List<RideData>): pieData? {
//        Log.d(TAG, "prepareRadarChartData: Processing ${rides.size} rides.")
//        if (rides.isEmpty()) {
//            Log.d(TAG, "prepareRadarChartData: No rides to process, returning null.")
//            return null
//        }
//        // Avg Efficiency (Value) by Time of Day (Categories)
//        val timeSlots = listOf(
//            "Morning (6-9)",
//            "Midday (10-15)",
//            "Evening (16-19)",
//            "Night (20-5)"
//        ) // Ensure labels match logic
//        val efficienciesPerSlot = MutableList(timeSlots.size) { mutableListOf<Float>() }
//        val timeFormat = SimpleDateFormat("HH", Locale.US) // To get hour of day
//
//        for (ride in rides) {
//            try {
//                // Assuming tripStartTime is "HH:mm:ss" or similar parseable by "HH"
//                val startTimeHour = timeFormat.parse(ride.tripStartTime)?.hours ?: continue
//                val efficiency = ride.tripRealMileageKmL
//                if (efficiency > 0) {
//                    when (startTimeHour) {
//                        in 6..9 -> efficienciesPerSlot[0].add(efficiency)
//                        in 10..15 -> efficienciesPerSlot[1].add(efficiency)
//                        in 16..19 -> efficienciesPerSlot[2].add(efficiency)
//                        in 20..23, in 0..5 -> efficienciesPerSlot[3].add(efficiency) // Night slot
//                    }
//                }
//            } catch (e: Exception) {
//                Log.w(
//                    TAG,
//                    "prepareRadarChartData: Error parsing tripStartTime '${ride.tripStartTime}' for ride ID ${ride.tripID}",
//                    e
//                )
//            }
//        }
//
//        val entries = ArrayList<RadarEntry>()
//        var validEntriesMade = 0
//        for (slotEfficiencies in efficienciesPerSlot) {
//            val avgEfficiency =
//                if (slotEfficiencies.isNotEmpty()) slotEfficiencies.average().toFloat() else 0f
//            entries.add(RadarEntry(avgEfficiency))
//            if (avgEfficiency > 0f) validEntriesMade++
//        }
//        Log.d(
//            TAG,
//            "prepareRadarChartData: Created ${entries.size} radar entries. ${validEntriesMade} have value > 0."
//        )
//
//        if (validEntriesMade == 0 && entries.isNotEmpty()) {
//            Log.d(TAG, "prepareRadarChartData: All radar entries have value=0, returning null.")
//            return null
//        }
//        if (entries.isEmpty() && rides.isNotEmpty()) {
//            Log.w(
//                TAG,
//                "prepareRadarChartData: entries list is empty but rides list was not. Problem in slotting logic. Returning null."
//            )
//            return null
//        }
//        if (entries.isEmpty()) {
//            Log.d(TAG, "prepareRadarChartData: entries list is empty. Returning null.")
//            return null
//        }
//
//
//        val dataSet = pieDataSet(entries, "Avg Efficiency by Time of Day (km/L)")
//        dataSet.color = ColorTemplate.rgb("#FF5722")
//        dataSet.fillColor = ColorTemplate.rgb("#FFCCBC")
//        dataSet.setDrawFilled(true)
//        dataSet.valueTextSize = 10f
//        dataSet.lineWidth = 2f
//        return pieData(dataSet)
//    }

    // This function goes inside your DashboardViewModel class

    fun insertFakeRideDataForTesting() {
        Log.d(TAG, "Attempting to insert fake ride data from provider...")
        viewModelScope.launch {
            try {
                // Get the list of fake rides from the provider
                val fakeRides = FakeRideDataProvider.getFakeRideDataList()

                if (fakeRides.isEmpty()) {
                    Log.d(TAG, "No fake rides provided by FakeRideDataProvider. Skipping insertion.")
                    return@launch
                }

                fakeRides.forEach { ride ->
                    repository.insertRide(ride) // Assuming your repository has an insertRide method that calls the DAO
                    Log.d(TAG, "Inserted fake ride for date: ${ride.tripDate}, start time: ${ride.tripStartTime}")
                }
                Log.d(TAG, "Finished inserting ${fakeRides.size} fake rides from provider.")

                // Optional: You might want to refresh data after insertion to see immediate effect.
                // If you want to refresh, consider calling updateKpiAndChartData().
                 updateKpiAndChartData()

            } catch (e: Exception) {
                Log.e(TAG, "Error inserting fake ride data from provider", e)
            }
        }
    }

}
