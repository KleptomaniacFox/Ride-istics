// Fragment

package com.example.rideistics.ui.home



import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.rideistics.R
import com.example.rideistics.RideisticsApplication
import com.example.rideistics.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Suppress the lint warning for this specific call, as 'isGranted' ensures permission.
                @SuppressLint("MissingPermission")
                homeViewModel.requestStartTrackingAfterPermission()
            } else {
                Toast.makeText(
                    context,
                    "Location permission denied. Tracking will not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Get the repository from the Application class
        val application = requireActivity().application as RideisticsApplication
        val repository = application.rideRepository
        val viewModelFactory = HomeViewModelFactory(repository)

        homeViewModel = ViewModelProvider(this, viewModelFactory)[HomeViewModel::class.java]
        // Initialize ViewModel with Application context
        homeViewModel.initialize(application)


        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupObservers()
        setupClickListeners()

        binding.mileageInputGroup.visibility = View.GONE

        return root
    }

    private fun setupObservers() {
        homeViewModel.timerDisplay.observe(viewLifecycleOwner) { time ->
            binding.textTimerDisplay.text = time
        }

        homeViewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            if (isRunning) {
                binding.buttonPlayPause.setImageResource(R.drawable.ic_pause_30dp)
            } else {
                binding.buttonPlayPause.setImageResource(R.drawable.ic_play_arrow_30dp)
            }
        }

        homeViewModel.currentSpeedKmh.observe(viewLifecycleOwner) { speed ->
            binding.speedText.text = speed
        }

        homeViewModel.totalDistanceKm.observe(viewLifecycleOwner) { distance ->
            binding.distanceText.text = distance
        }

        homeViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                homeViewModel.onToastMessageShown()
            }
        }

        homeViewModel.customMileage.observe(viewLifecycleOwner) { mileageString ->
            if (mileageString == null) {
                binding.editTextMileage.text.clear()
            }
        }

        homeViewModel.totalFuelConsumedLitersDisplay.observe(viewLifecycleOwner) { fuelConsumed ->
            binding.fuelUsedText.text = fuelConsumed
        }
    }

    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            when {
                requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        requireContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                    homeViewModel.onPlayPauseClicked()
                }

                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    showPermissionRationaleDialog()
                }

                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }

        binding.buttonRecordReset.setOnClickListener {
            homeViewModel.onRecordAndResetClicked()
            // binding.mileageInputGroup.visibility = View.VISIBLE // Optional: show mileage input
            // binding.editTextMileage.requestFocus()
        }

        binding.setMileage.setOnClickListener {
            binding.mileageInputGroup.isVisible = !binding.mileageInputGroup.isVisible
            if (binding.mileageInputGroup.isVisible) {
                binding.editTextMileage.requestFocus()
            }
        }

        binding.buttonApplyMileage.setOnClickListener {
            val mileageText = binding.editTextMileage.text.toString()
            if (mileageText.isNotBlank()) {
                homeViewModel.setCustomMileage(mileageText)
                binding.mileageInputGroup.visibility = View.GONE
            } else {
                Toast.makeText(context, "Mileage cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location Permission Needed")
            .setMessage("This app needs Location permission to track your ride. Please grant the permission.")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}