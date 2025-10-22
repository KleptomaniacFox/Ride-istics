package com.example.rideistics.ui.futurerides

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import com.example.rideistics.data.db.AppDatabase
import com.example.rideistics.data.repository.RideRepository
import com.example.rideistics.databinding.FragmentFutureRidesBinding

class FutureRidesFragment : Fragment() {

    private var _binding: FragmentFutureRidesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FutureRidesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // --- ViewModel Initialization ---
        val rideDao = AppDatabase.getDatabase(requireContext().applicationContext).rideDao()
        val rideRepository = RideRepository(rideDao)
        val factory = FutureRidesViewModelFactory(rideRepository)
        viewModel = ViewModelProvider(this, factory)[FutureRidesViewModel::class.java]

        _binding = FragmentFutureRidesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        setupObservers()

        // Start the prediction calculation
        viewModel.calculateNextMonthPrediction()
    }

    private fun setupObservers() {


        viewModel.monthPrediction.observe(viewLifecycleOwner) { prediction ->
            binding.futureMonthKpi1.text = prediction.rides
            binding.futureMonthKpi2.text = prediction.fuel
            binding.futureMonthKpi3.text = prediction.distance
            binding.futureMonthKpi4.text = prediction.efficiency
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}