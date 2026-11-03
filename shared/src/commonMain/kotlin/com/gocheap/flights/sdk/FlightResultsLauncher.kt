package com.gocheap.flights.sdk

import androidx.compose.runtime.Composable
import com.gocheap.flights.data.model.TripSearch

/**
 * Hands a finished flight search to whatever shows flight results on this platform.
 *
 * On Android that is the Travelpayouts SDK, which answers with its own results Activity; the search
 * card stays exactly as it is and simply stops navigating to the in-app results page. On iOS, where
 * the SDK is a separate integration that has not been done yet, this returns false and the caller
 * falls back to the page this app draws itself.
 *
 * Phrased as "did something handle it" rather than as a navigation call so the fallback lives at
 * the one place that knows where else a search could go — see `AppNavHost`.
 */
@Composable
expect fun rememberFlightResultsLauncher(): (TripSearch) -> Boolean
