package com.gocheap.travel.analytics.ga4

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue

/**
 * Reports a `screen_view` once, when this composable enters composition.
 *
 * For the sheets, which the navigation graph never sees: the filter and sort sheets over the
 * results list, the deals list, and the search form reopened over results. Every actual
 * destination is covered by [TrackNavigationScreenViews] instead, so this should not be called
 * from a screen that has a route — that would report it twice.
 *
 * Keyed on the name rather than on `Unit` so a sheet that is reused for several filters — which is
 * how [com.gocheap.travel.ui.home.results.api.FlightFilterSheets] is built — reports each one.
 */
@Composable
internal fun TrackSheetView(screenName: String) {
    LaunchedEffect(screenName) {
        FlightAnalytics.sheetView(screenName)
    }
}

/**
 * Reports a `screen_view` for every destination the graph arrives at.
 *
 * One observer for the whole app rather than a call in each of the twenty-odd screens: a screen
 * added later is then reported without anyone having to remember to add it, and there is no way
 * for two screens to report themselves under names that disagree. The route is translated by
 * [screenNameForRoute], which is also what strips the arguments off it.
 *
 * Keyed on the resolved name and not on the entry: `currentBackStackEntryAsState` emits a new entry
 * on a configuration change and on a tab being restored, and keying on it would report the same
 * screen again each time the user rotated the phone.
 */
@Composable
internal fun TrackNavigationScreenViews(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val screenName = screenNameForRoute(entry?.destination?.route)
    LaunchedEffect(screenName) {
        screenName?.let(FlightAnalytics::screenView)
    }
}
