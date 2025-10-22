package com.example.rideistics.ui.dashboard

data class PeriodSelector(
    val id: Int,
    val displayName: String,
    val someOtherDetail: String? = null // Add or remove properties as needed
) {
    // This makes the Spinner show the 'displayName'
    override fun toString(): String {
        return displayName
    }
}
