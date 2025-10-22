package com.example.rideistics.ui.dashboard

import android.R
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.rideistics.data.db.AppDatabase
import com.example.rideistics.data.repository.RideRepository
import com.example.rideistics.databinding.FragmentDashboardBinding
import com.example.rideistics.utils.EventObserver
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BubbleChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBubbleDataSet
import java.util.Calendar

// Keep your existing TAG_FRAGMENT
private const val TAG_FRAGMENT = "DashboardFragment_Debug"

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This is line 31 (the getter)
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    // Declare chart view variables
    private lateinit var groupedBarChart: BarChart
    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart

    private var selectedStartDateMillis: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG_FRAGMENT, "onCreateView")

        // 1. Initialize ViewModel (can be done before binding)
        val rideDao = AppDatabase.getDatabase(requireActivity().applicationContext).rideDao()
        val rideRepository = RideRepository(rideDao)
        val viewModelFactory = DashboardViewModelFactory(rideRepository)
        dashboardViewModel =
            ViewModelProvider(this, viewModelFactory)[DashboardViewModel::class.java]

        // 2. Initialize _binding
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        // 3. Return the root of the binding
        // Any other direct use of 'binding.someView' that was previously at line 48
        // should be moved to onViewCreated.
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG_FRAGMENT, "onViewCreated")

        // Initialize chart views from binding HERE, in onViewCreated
        // Now it's safe because binding.root (the view) is created.
        groupedBarChart = binding.groupedBarChart
        lineChart = binding.lineChart
        pieChart = binding.pieChart

        setupPeriodSelector()
        setupKpiObservers()
        setupNavigationObservers()
        setupDatePickerEventObserver()
        setupChartObservers()
        configureCharts()
    }

    private fun setupNavigationObservers() { // Added this new method
        dashboardViewModel.navigateToFutureRidesEvent.observe(viewLifecycleOwner, EventObserver {
            Log.d(TAG_FRAGMENT, "navigateToFutureRidesEvent observed, navigating to future rides.")
            // Ensure you have the correct action ID in your navigation graph
            findNavController().navigate(com.example.rideistics.R.id.action_dashboard_to_future_rides)
        })
    }

    private fun setupPeriodSelector() {
        Log.d(TAG_FRAGMENT, "setupPeriodSelector")
        dashboardViewModel.periodSelector.observe(viewLifecycleOwner) { periodSelectorList ->
            Log.d(TAG_FRAGMENT, "Period selector list updated, size: ${periodSelectorList?.size}")
            if (!periodSelectorList.isNullOrEmpty()) {
                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.simple_spinner_item,
                    periodSelectorList
                )
                adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
                binding.periodSelector.adapter = adapter

                binding.periodSelector.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>,
                            selectedView: View?,
                            position: Int,
                            id: Long
                        ) {
                            val selectedPeriod =
                                parent.getItemAtPosition(position) as PeriodSelector
                            Log.d(
                                TAG_FRAGMENT,
                                "Spinner item selected: ${selectedPeriod.displayName}"
                            )
                            dashboardViewModel.setSelectedPeriod(selectedPeriod)
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) { /* Optional */
                        }
                    }
            } else {
                binding.periodSelector.adapter = null
            }
        }
    }

    private fun setupKpiObservers() {
        dashboardViewModel.totalRidesText.observe(viewLifecycleOwner) {
            binding.kpi1.text = it
        } // Assuming kpi1Text is the ID in XML
        dashboardViewModel.totalFuelSpentText.observe(viewLifecycleOwner) {
            binding.kpi2.text = it
        } // Assuming kpi2Text is the ID
        dashboardViewModel.totalDistanceText.observe(viewLifecycleOwner) {
            binding.kpi3.text = it
        } // Assuming kpi3Text is the ID
        dashboardViewModel.avgFuelEfficiencyText.observe(viewLifecycleOwner) {
            binding.kpi4.text = it
        } // Assuming kpi4Text is the ID
    }

    private fun setupDatePickerEventObserver() {
        dashboardViewModel.showDateRangePickerEvent.observe(viewLifecycleOwner, EventObserver {
            Log.d(TAG_FRAGMENT, "ShowDatePickerEvent observed")
            promptForStartDate()
        })
    }



    private fun setupChartObservers() {
        Log.d(TAG_FRAGMENT, "setupChartObservers")
        val whiteColor = android.graphics.Color.WHITE

        // Bar Chart Observer
        dashboardViewModel.barChartDataLiveData.observe(viewLifecycleOwner) { barData ->
            Log.d(TAG_FRAGMENT, "BarChart LiveData received: barData isNull=${barData == null}")
            groupedBarChart.post {
                if (barData != null && barData.dataSetCount > 0 && barData.getDataSetByIndex(0).entryCount > 0) {
                    Log.d(TAG_FRAGMENT, "BarChart (post): Setting data with ${barData.entryCount} entries, ${barData.dataSetCount} datasets.")

                    // Set value text color for the data set
                    barData.dataSets.forEach { dataSet ->
                        dataSet.valueTextColor = whiteColor // Color for the text on top of bars
                        // dataSet.valueTextSize = 10f // Already set in ViewModel, but can override
                    }

                    groupedBarChart.data = barData

                    val speedBucketLabels = listOf(
                        "0-5", "5-10", "10-15", "15-20", "20-25", "25-30", "30-35", "35-40",
                        "40-45", "45-50", "50-55", "55-60", "60-65", "65-70", "70-75", ">75"
                    )
                    groupedBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(speedBucketLabels)
                    groupedBarChart.xAxis.granularity = 1f
                    groupedBarChart.xAxis.isGranularityEnabled = true

                    groupedBarChart.invalidate()
                    groupedBarChart.visibility = View.VISIBLE
                } else {
                    Log.d(TAG_FRAGMENT, "BarChart (post): Clearing chart or no data.")
                    groupedBarChart.clear()
                    groupedBarChart.setNoDataText("No data available for selected period.")
                    groupedBarChart.invalidate()
//                    groupedBarChart.visibility = View.GONE
                }
            }
        }

        // Bubble Chart Observer (existing)
        dashboardViewModel.lineChartDataLiveData.observe(viewLifecycleOwner) { lineData ->
            Log.d(TAG_FRAGMENT, "BubbleChart LiveData received: lineData isNull=${lineData == null}. Chart: $lineChart")
            // ... (existing line chart data handling) ...
            // Add similar dataSet.valueTextColor = whiteColor if BubbleChart shows values
            if (lineData != null) {
                lineData.dataSets.forEach { dataSet ->
                    if (dataSet is IBubbleDataSet) { // Ensure it's the correct type for line specific properties
                        dataSet.valueTextColor = whiteColor
                    }
                }
            }
            // ... (rest of line chart data handling) ...
            if (!this::lineChart.isInitialized) {
                Log.e(TAG_FRAGMENT, "BubbleChart is not initialized when LiveData observed!")
                return@observe
            }

            lineChart.post {
                if (lineChart.width <= 0 || lineChart.height <= 0) {
                    Log.w(TAG_FRAGMENT, "BubbleChart (post): Chart not laid out yet (Width: ${lineChart.width}, Height: ${lineChart.height}). Skipping update for now.")
                    return@post
                }
                Log.d(TAG_FRAGMENT, "BubbleChart (post): Chart is laid out (Width: ${lineChart.width}, Height: ${lineChart.height}). Processing LiveData.")

                if (lineData != null && lineData.dataSetCount > 0 && lineData.getDataSetByIndex(0).entryCount > 0) {
                    Log.d(TAG_FRAGMENT, "BubbleChart (post): Valid new lineData received. Setting data with ${lineData.entryCount} entries.")
                    lineData.dataSets.forEach { dataSet ->
                        if (dataSet is IBubbleDataSet) {
                            dataSet.valueTextColor = whiteColor
                        }
                    }
                    lineChart.data = lineData
                    lineChart.fitScreen()
                    lineChart.invalidate()
                    lineChart.visibility = View.VISIBLE
                    Log.d(TAG_FRAGMENT, "BubbleChart (post): New data set, screen fitted, and invalidated.")
                } else {
                    Log.d(TAG_FRAGMENT, "BubbleChart (post): LiveData brought null or empty lineData. Clearing chart.")
                    lineChart.clear()
                    lineChart.setNoDataText("No data available for selected period.")
//                    lineChart.visibility = View.GONE
                    Log.d(TAG_FRAGMENT, "BubbleChart (post): Chart cleared.")
                }
            }
        }


        // Pie Chart Observer (existing)
        dashboardViewModel.pieChartDataLiveData.observe(viewLifecycleOwner) { pieData ->
            Log.d(TAG_FRAGMENT, "PieChart LiveData received: pieData isNull=${pieData == null}")
            // ... (existing pie chart data handling) ...
            // PieChart value text color is usually set on the PieDataSet in the ViewModel or PieData object itself
            // e.g., pieData.setValueTextColor(android.graphics.Color.BLACK) was in your ViewModel.
            // If you want to ensure it's white here, you can iterate dataSets:
            if (pieData != null) {
                pieData.dataSets.forEach { dataSet ->
                    dataSet.valueTextColor = whiteColor // Or Color.BLACK if values are on light slices
                }
            }
            // ... (rest of pie chart data handling) ...
            if (!this::pieChart.isInitialized) {
                Log.e(TAG_FRAGMENT, "PieChart is not initialized when LiveData for PieChart observed!")
                return@observe
            }
            pieChart.post {
                if (pieChart.width <= 0 || pieChart.height <= 0) {
                    Log.w(TAG_FRAGMENT, "PieChart (post): Chart not laid out yet. Skipping update.")
                    return@post
                }
                Log.d(TAG_FRAGMENT, "PieChart (post): Chart is laid out. Processing LiveData.")

                if (pieData != null && pieData.entryCount > 0) {
                    Log.d(TAG_FRAGMENT, "PieChart (post): Valid new pieData received. Setting data with ${pieData.entryCount} entries.")
                    pieData.dataSets.forEach { dataSet ->
                        dataSet.valueTextColor = whiteColor // Make sure values on pie slices are visible
                    }
                    pieChart.data = pieData
                    pieChart.highlightValues(null)
                    pieChart.invalidate()
                    pieChart.visibility = View.VISIBLE
                } else {
                    Log.d(TAG_FRAGMENT, "PieChart (post): LiveData brought null or empty pieData. Clearing chart.")
                    pieChart.clear()
                    pieChart.setNoDataText("No data available for selected period.")
//                    pieChart.visibility = View.GONE
                }
            }
        }
    }
//    private fun setupChartObservers() {
//        Log.d(TAG_FRAGMENT, "setupChartObservers")
//        dashboardViewModel.barChartDataLiveData.observe(viewLifecycleOwner) { barData ->
//            Log.d(TAG_FRAGMENT, "BarChart LiveData received: barData isNull=${barData == null}")
//            if (barData != null && barData.dataSetCount > 0 && barData.getDataSetByIndex(0).entryCount > 0) {
//                Log.d(
//                    TAG_FRAGMENT,
//                    "BarChart: Setting data with ${barData.entryCount} entries, ${barData.dataSetCount} datasets."
//                )
//                groupedBarChart.data = barData
//                val speedBucketLabels = listOf(
//                    "0-5", "5-10", "10-15", "15-20", "20-25", "25-30", "30-35", "35-40",
//                    "40-45", "45-50", "50-55", "55-60", "60-65", "65-70", "70-75", ">75"
//                ) // Match ViewModel
//                groupedBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(speedBucketLabels)
//                groupedBarChart.xAxis.granularity = 1f
//                groupedBarChart.xAxis.isGranularityEnabled = true
//                // groupedBarChart.description.text = "Avg Efficiency by Speed Bucket" // Already in configureCharts or VM
//                groupedBarChart.invalidate()
//                groupedBarChart.visibility = View.VISIBLE
//            } else {
//                Log.d(TAG_FRAGMENT, "BarChart: Clearing chart or no data.")
//                groupedBarChart.clear()
//                groupedBarChart.setNoDataText("No data available for selected period.")
//                groupedBarChart.invalidate()
//            }
//        }
//
//        dashboardViewModel.lineChartDataLiveData.observe(viewLifecycleOwner) { lineData ->
//            Log.d(
//                TAG_FRAGMENT,
//                "BubbleChart LiveData received: lineData isNull=${lineData == null}. Chart: $lineChart"
//            )
//            // Ensure lineChart is not null (it shouldn't be if onViewCreated ran)
//            if (this::lineChart.isInitialized) {
//                lineChart.post {
//                    // Defensive check: ensure chart has been laid out with positive dimensions
//                    if (lineChart.width <= 0 || lineChart.height <= 0) {
//                        Log.w(
//                            TAG_FRAGMENT,
//                            "BubbleChart (post): Chart not laid out yet (width/height <= 0). Will try again on next data update or layout pass."
//                        )
//                        // Optionally, you could re-post this action, but it might lead to loops.
//                        // For now, just log and skip. The next LiveData update might catch it when ready.
//                        return@post
//                    }
//
//                    Log.d(
//                        TAG_FRAGMENT,
//                        "BubbleChart (post): Chart is laid out (Width: ${lineChart.width}, Height: ${lineChart.height}). Processing data."
//                    )
//
//                    if (lineData != null && lineData.dataSetCount > 0 && lineData.getDataSetByIndex(
//                            0
//                        ).entryCount > 0
//                    ) {
//                        Log.d(
//                            TAG_FRAGMENT,
//                            "BubbleChart (post): Valid data received. Setting data with ${lineData.entryCount} entries."
//                        )
//
//                        // Clear previous data and reset viewport aggressively
////                        lineChart.clearValues() // Clears values but keeps chart state
////                        lineChart.clear()       // Clears everything including data and resets state
//
//                        // Set new data
//                        lineChart.data = lineData
//
//                        // Adjust viewport to the new data
//                        lineChart.fitScreen() // This is important AFTER data is set
//
//                        // Redraw
//                        lineChart.invalidate()
//                        lineChart.visibility = View.VISIBLE
//                        Log.d(
//                            TAG_FRAGMENT,
//                            "BubbleChart (post): Data set, screen fitted, and invalidated."
//                        )
//                    } else {
//                        Log.d(
//                            TAG_FRAGMENT,
//                            "BubbleChart (post): Clearing chart due to null or empty data."
//                        )
//                        lineChart.clear()
//                        lineChart.setNoDataText("No data available for selected period.")
////                        lineChart.visibility = View.GONE
//                    }
//                }
//            } else {
//                Log.e(TAG_FRAGMENT, "BubbleChart is not initialized when LiveData observed!")
//            }
//        }
//
////        dashboardViewModel.lineChartDataLiveData.observe(viewLifecycleOwner) { lineData ->
////            Log.d(
////                TAG_FRAGMENT,
////                "BubbleChart LiveData received: lineData isNull=${lineData == null}"
////            )
////            if (lineData != null && lineData.dataSetCount > 0 && lineData.getDataSetByIndex(0).entryCount > 0) {
////                Log.d(
////                    TAG_FRAGMENT,
////                    "BubbleChart: Setting data with ${lineData.entryCount} entries, ${lineData.dataSetCount} datasets."
////                )
////                lineChart.data = lineData
////
////                lineChart.fitScreen()
////                lineChart.invalidate()
////                lineChart.visibility = View.VISIBLE
////
////                Log.d(TAG_FRAGMENT, "BubbleChart (post): Data set, screen fitted, and invalidated.")
////
////            } else {
////                Log.d(TAG_FRAGMENT, "BubbleChart: Clearing chart or no data.")
////                lineChart.clear()
////                lineChart.setNoDataText("No data available for selected period.")
////                lineChart.invalidate()
////            }
////        }
//
//        // In DashboardFragment.kt -> setupChartObservers()
//
//        dashboardViewModel.pieChartDataLiveData.observe(viewLifecycleOwner) { pieData ->
//            Log.d(TAG_FRAGMENT, "PieChart LiveData received: pieData isNull=${pieData == null}")
//            if (!this::pieChart.isInitialized) {
//                Log.e(
//                    TAG_FRAGMENT,
//                    "PieChart is not initialized when LiveData for PieChart observed!"
//                )
//                return@observe
//            }
//            pieChart.post {
//                if (pieChart.width <= 0 || pieChart.height <= 0) {
//                    Log.w(TAG_FRAGMENT, "PieChart (post): Chart not laid out yet. Skipping update.")
//                    return@post
//                }
//                Log.d(TAG_FRAGMENT, "PieChart (post): Chart is laid out. Processing LiveData.")
//
//                if (pieData != null && pieData.entryCount > 0) { // Check pieData.entryCount directly
//                    Log.d(
//                        TAG_FRAGMENT,
//                        "PieChart (post): Valid new pieData received. Setting data with ${pieData.entryCount} entries."
//                    )
//
//                    pieChart.data =
//                        pieData // The labels are already in the PieData from the ViewModel
//
//                    // No need to set xAxis.valueFormatter for PieChart
//                    // val timeSlotLabels = listOf("Morning", "Midday", "Evening", "Night") // This is not needed here
//                    // pieChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeSlotLabels) // REMOVE THIS LINE
//
//                    pieChart.highlightValues(null) // Reset any highlights
//                    pieChart.invalidate()          // Refresh the chart
//                    pieChart.visibility = View.VISIBLE
//                } else {
//                    Log.d(
//                        TAG_FRAGMENT,
//                        "PieChart (post): LiveData brought null or empty pieData. Clearing chart."
//                    )
//                    pieChart.clear()
//                    pieChart.setNoDataText("No data available for selected period.")
//                    // pieChart.invalidate() // clear() already invalidates
////                    pieChart.visibility = View.GONE
//                }
//            }
//        }
//
//    }



        // In DashboardFragment.kt

    // In DashboardFragment.kt

    private fun configureCharts() {
        Log.d(TAG_FRAGMENT, "configureCharts")
        val whiteColor = android.graphics.Color.WHITE
        val lightGrayColor = android.graphics.Color.LTGRAY // For grid lines, less prominent

        // Bar Chart
        groupedBarChart.setNoDataText("Select a period to view data.")
        groupedBarChart.description.isEnabled = false
        // groupedBarChart.animateY(1000) // Keep Y animation if it works

        // X-Axis configuration
        groupedBarChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = whiteColor // Color for X-axis labels
            gridColor = lightGrayColor // Color for X-axis grid lines
            axisLineColor = whiteColor // Color for the X-axis line itself
            setLabelCount(6, true) // Example, adjust if needed based on new bucket count
            // granularity = 1f // Already set in observer, good to keep
            // isGranularityEnabled = true // Already set in observer
        }

        // Left Y-Axis configuration
        groupedBarChart.axisLeft.apply {
            textColor = whiteColor // Color for Left Y-axis labels
            gridColor = lightGrayColor // Color for Left Y-axis grid lines
            axisLineColor = whiteColor // Color for the Left Y-axis line itself
            axisMinimum = 0f // Usually good to start Y-axis at 0 for bar charts
        }

        // Right Y-Axis configuration
        groupedBarChart.axisRight.apply {
            isEnabled = false // Keep it disabled as per your existing config
            // If you were to enable it:
            // textColor = whiteColor
            // gridColor = lightGrayColor
            // axisLineColor = whiteColor
        }

        groupedBarChart.legend.textColor = whiteColor // Color for the legend text

        // Bubble Chart (existing configurations)
        // ... ensure you apply similar color changes to lineChart axes and legend ...
        lineChart.setNoDataText("Select a period to view data.")
        lineChart.description.isEnabled = false
        lineChart.xAxis.apply {
            axisMinimum = 0f
            axisMaximum = 120f
            position = XAxis.XAxisPosition.BOTTOM
            setLabelCount(6, true)
            textColor = whiteColor
            gridColor = lightGrayColor
            axisLineColor = whiteColor
            // setVisibleXRangeMinimum(20f) // Keep if needed for zoom control
            // setVisibleXRangeMaximum(80f) // Keep if needed for zoom control
        }
        lineChart.axisLeft.apply {
            axisMinimum = 0f
            setLabelCount(6, true)
            textColor = whiteColor
            gridColor = lightGrayColor
            axisLineColor = whiteColor
        }
        lineChart.axisRight.isEnabled = false
        lineChart.legend.textColor = whiteColor

        // PieChart configuration (existing configurations)
        if (this::pieChart.isInitialized) {
            pieChart.setNoDataText("Select a period to view data.")
            pieChart.description.isEnabled = false
            pieChart.isRotationEnabled = true
            pieChart.setUsePercentValues(true)
            pieChart.legend.isEnabled = true
            pieChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            pieChart.legend.orientation = Legend.LegendOrientation.VERTICAL
            pieChart.legend.setDrawInside(false)
            pieChart.setEntryLabelColor(whiteColor) // Pie slice labels (Morning, Midday etc.)
            pieChart.setEntryLabelTextSize(12f)
            pieChart.legend.textColor = whiteColor
        } else {
            Log.e(TAG_FRAGMENT, "configureCharts: pieChart is not initialized!")
        }
    }

//    private fun configureCharts() {
//        Log.d(TAG_FRAGMENT, "configureCharts")
//        // Bar Chart
//        groupedBarChart.setNoDataText("Select a period to view data.")
//        groupedBarChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
//        groupedBarChart.axisRight.isEnabled = false
//        groupedBarChart.description.isEnabled = false
//        // groupedBarChart.animateY(1000)
//
//        // Bubble Chart
//        lineChart.setNoDataText("Select a period to view data.")
//        // lineChart.animateXY(1000, 1000) // Keep animations commented out for stability testing
//        lineChart.description.isEnabled = false
//
//        // Enable touch gestures
//        lineChart.setTouchEnabled(true)
//        lineChart.isDragEnabled = true
//        lineChart.setScaleEnabled(true)
//        lineChart.setPinchZoom(true) // Allows zooming on both axes simultaneously
//
//        lineChart.xAxis.apply {
//            axisMinimum = 0f       // Overall min X value (e.g., avgSpeed)
//            axisMaximum = 120f     // Overall max X value
//            position = XAxis.XAxisPosition.BOTTOM
//            setLabelCount(6, true)
//
//            // Control zoom levels for X-axis
//            // setVisibleXRangeMinimum(20f)  // Smallest visible window of X values (e.g., can't zoom in tighter than a 20 units wide view)
//            // setVisibleXRangeMaximum(80f) // Largest visible window of X values (effectively limits zooming out too far)
//            // Adjust these values based on your data's typical spread and desired zoom behavior
//        }
//
//        lineChart.axisLeft.apply {
//            axisMinimum = 0f       // Overall min Y value (e.g., maxSpeed)
//            // axisMaximum = 180f  // Optional: Overall max Y value
//            setLabelCount(6, true)
//
//            // You can also set visible range for Y-axis if needed, though X-axis is often more critical for this specific crash
//            // setVisibleYRangeMinimum(30f, YAxis.AxisDependency.LEFT)
//            // setVisibleYRangeMaximum(100f, YAxis.AxisDependency.LEFT)
//        }
//        lineChart.axisRight.isEnabled = false
//
//        // Pie Chart
//        pieChart.setNoDataText("Select a period to view data.")
//        pieChart.description.isEnabled = false
//        pieChart.isRotationEnabled = true
//        pieChart.setUsePercentValues(true) // Important if using PercentFormatter
//        pieChart.legend.isEnabled = true // Show legend for "Morning", "Midday", etc.
//        pieChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
//        pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
//        pieChart.legend.orientation = Legend.LegendOrientation.VERTICAL
//        pieChart.legend.setDrawInside(false)
//        pieChart.setEntryLabelColor(android.graphics.Color.BLACK)
//        pieChart.setEntryLabelTextSize(12f)
//        // pieChart.animateXY(1000, 1000)
//    }






    private fun promptForStartDate() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
                selectedStartDateMillis = selectedCalendar.timeInMillis
                Toast.makeText(
                    requireContext(),
                    "Start date selected, select end date",
                    Toast.LENGTH_SHORT
                ).show()
                promptForEndDate()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun promptForEndDate() {
        val calendar = Calendar.getInstance()
        val startDateForPicker = selectedStartDateMillis ?: calendar.timeInMillis

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth, 23, 59, 59)
                selectedCalendar.set(Calendar.MILLISECOND, 999)
                val endDateMillis = selectedCalendar.timeInMillis

                if (selectedStartDateMillis != null) {
                    Log.d(
                        TAG_FRAGMENT,
                        "Custom date range selected: Start=$selectedStartDateMillis, End=$endDateMillis"
                    )
                    dashboardViewModel.setCustomDateRangeAndFetchKpis(
                        selectedStartDateMillis!!,
                        endDateMillis
                    )
                } else {
                    Toast.makeText(requireContext(), "Start date was not set.", Toast.LENGTH_SHORT)
                        .show()
                }
                selectedStartDateMillis = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = startDateForPicker
        datePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG_FRAGMENT, "onDestroyView")
        _binding = null // Crucial for preventing memory leaks
    }
}