package com.gocheap.flights.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gocheap.flights.data.model.TripSearch

/**
 * The context comes from the composition rather than from the Application, because the SDK starts
 * an Activity with whatever it is handed: an application context would need NEW_TASK and would open
 * the results in a task of their own, outside this app's back stack.
 */
@Composable
actual fun rememberFlightResultsLauncher(): (TripSearch) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { search -> FlightResultsHandoff.handler?.show(context, search) == true }
    }
}
