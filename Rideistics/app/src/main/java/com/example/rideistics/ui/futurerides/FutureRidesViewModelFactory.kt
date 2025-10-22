package com.example.rideistics.ui.futurerides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rideistics.data.repository.RideRepository

class FutureRidesViewModelFactory(private val repository: RideRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FutureRidesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FutureRidesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}