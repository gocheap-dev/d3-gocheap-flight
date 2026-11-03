package com.gocheap.flights.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.gocheap.flights.navigation.NavMotion.popEnter
import com.gocheap.flights.navigation.NavMotion.popExit
import com.gocheap.flights.navigation.NavMotion.predictivePopEnter
import com.gocheap.flights.navigation.NavMotion.predictivePopExit
import com.gocheap.flights.navigation.NavMotion.pushEnter
import com.gocheap.flights.navigation.NavMotion.pushExit
import com.gocheap.flights.navigation.NavMotion.tabEnter
import com.gocheap.flights.navigation.NavMotion.tabExit
import com.gocheap.flights.ui.calendar.CalendarMode
import com.gocheap.flights.ui.calendar.CalendarRequest
import com.gocheap.flights.ui.calendar.CalendarScreen
import com.gocheap.flights.ui.calendar.DateSelection
import com.gocheap.flights.ui.car.CarsScreen
import com.gocheap.flights.ui.car.location.CarLocationScreen
import com.gocheap.flights.ui.car.location.CarLocationSelection
import com.gocheap.flights.ui.car.location.decodeCarLocation
import com.gocheap.flights.ui.car.location.encodeSelection
import com.gocheap.flights.ui.favorites.FavoritesScreen
import com.gocheap.flights.data.model.hotel.HotelDestination
import com.gocheap.flights.ui.hotel.destination.HotelDestinationScreen
import com.gocheap.flights.ui.hotel.destination.decodeHotelDestination
import com.gocheap.flights.ui.hotel.destination.encodeSelection
import com.gocheap.flights.ui.hotel.guests.GuestSelection
import com.gocheap.flights.ui.hotel.guests.GuestSelectionScreen
import com.gocheap.flights.ui.hotel.guests.decodeStayRooms
import com.gocheap.flights.ui.hotel.guests.encodeRooms
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.flights.ui.home.location.LocationTargets
import com.gocheap.flights.ui.home.location.toAirport
import com.gocheap.flights.ui.home.results.api.FlightApiResultsViewModel
import com.gocheap.flights.ui.home.location.LocationKind
import com.gocheap.flights.ui.home.location.LocationPickerScreen
import com.gocheap.flights.ui.home.location.LocationRequest
import com.gocheap.flights.ui.home.location.LocationSelection
import com.gocheap.flights.ui.home.location.decodeFlightDestination
import com.gocheap.flights.ui.home.location.encodeSelection
import com.gocheap.flights.ui.home.HomeScreen
import com.gocheap.flights.ui.hotel.HotelsScreen
import com.gocheap.flights.data.onboarding.OnboardingStore
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.FlightAnalytics
import com.gocheap.flights.analytics.ga4.TrackNavigationScreenViews
import com.gocheap.flights.data.controller.AppController
import com.gocheap.flights.data.controller.FlightSearchMode
import com.gocheap.flights.data.recent.RecentSearchStore
import com.gocheap.flights.push.PushNotifications
import com.gocheap.flights.data.user.SessionTracking
import com.gocheap.flights.sdk.rememberFlightResultsLauncher
import com.gocheap.flights.ui.car.results.CarResultsScreen
import com.gocheap.flights.ui.home.results.FlightResultsScreen
import com.gocheap.flights.ui.home.results.api.FlightApiResultsScreen
import com.gocheap.flights.ui.home.results.api.FlightDealScreen
import com.gocheap.flights.ui.home.results.api.SelectedFlightDeal
import com.gocheap.flights.ui.hotel.results.HotelResultsScreen
import com.gocheap.flights.ui.onboarding.OnboardingScreen
import com.gocheap.flights.ui.profile.AboutAppScreen
import com.gocheap.flights.ui.profile.PriceDisplayScreen
import com.gocheap.flights.ui.profile.ProfileScreen
import com.gocheap.flights.ui.profile.RegionKind
import com.gocheap.flights.ui.profile.RegionPickerScreen
import com.gocheap.flights.ui.splash.SplashDestination
import com.gocheap.flights.ui.splash.SplashScreen
import com.gocheap.flights.ui.home.travellers.TravellerSelection
import com.gocheap.flights.ui.home.travellers.TravellerSelectionScreen
import com.gocheap.flights.ui.home.travellers.decodeTravellerSelection
import com.gocheap.flights.ui.home.travellers.encodeTravellers
import com.gocheap.flights.ui.theme.FlightColors
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.flying_from
import flighttp.shared.generated.resources.flying_to

private const val TicketSuccessKey = "ticket_success"

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppRoute.Splash.route,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomBarRoutes

    // Every destination's `screen_view`, reported from the one place that sees all of them. A
    // screen added to the graph below is then measured without anyone having to remember to
    // measure it — see TrackNavigationScreenViews, and TrackSheetView for the sheets, which are
    // not destinations and so never reach here.
    TrackNavigationScreenViews(navController)

    // The pages, captured as they are drawn. The glass bar cannot sample the screen itself, so the
    // only way it gets something to blur is for the content to hand over a copy on its way out.
    // Recorded here rather than inside the graph because the bar is the graph's sibling: were it
    // inside what it records, blurring it would feed the bar its own previous frame.
    val backdrop = rememberGraphicsLayer()

    // Opaque container behind the graph: push/pop slide a screen clear of the container edge,
    // and without this the window background shows through the gap while they do.
    Box(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        AppNavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                },
        )

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(
                animationSpec = tween(NavMotion.BarDurationMillis, easing = NavMotion.BarEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(NavMotion.BarDurationMillis)),
            exit = slideOutVertically(
                animationSpec = tween(NavMotion.BarDurationMillis, easing = NavMotion.BarEasing),
                targetOffsetY = { it },
            ) + fadeOut(tween(NavMotion.BarDurationMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // The bar handles its own gesture inset: it is flush to the bottom edge, so the
            // white has to run behind the handle rather than stopping above it.
            FlightBottomBar(
                currentRoute = currentRoute,
                onTabSelected = { tab -> navController.navigateToTab(tab) },
                backdrop = backdrop,
            )
        }
    }
}

/**
 * Tab switches reuse the saved state of each tab rather than stacking duplicates, so returning to
 * Home keeps its scroll position.
 *
 * Home stays at the base of the stack and is never popped inclusively: popping the very entry we
 * are about to navigate to while `saveState`/`restoreState` are on empties the back stack mid-
 * transition, and the host then renders a blank frame until the next tap rebuilds the entry.
 */
private fun NavHostController.navigateToTab(tab: BottomTab) {
    // Re-tapping the active tab (or tapping mid-transition) would otherwise queue a second
    // navigation against a half-torn-down entry.
    if (currentDestination?.route == tab.route) return
    navigate(tab.route) {
        popUpTo(AppRoute.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
        // Without these two the system back gesture keeps the library default, which scales the
        // outgoing page down to 70% instead of sliding it away.
        predictivePopEnterTransition = { swipeEdge -> predictivePopEnter(swipeEdge) },
        predictivePopExitTransition = { swipeEdge -> predictivePopExit(swipeEdge) },
    ) {
        composable(AppRoute.Splash.route) {
            SplashScreen(
                onSplashFinished = { destination ->
                    val route = when (destination) {
                        SplashDestination.Onboarding -> AppRoute.Onboarding.route
                        SplashDestination.Home -> AppRoute.Home.route
                    }
                    navController.navigate(route) {
                        popUpTo(AppRoute.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppRoute.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    // Recorded on the way out rather than on the way in, so a launch that never
                    // reaches the end — killed mid-page — still shows the pages next time.
                    OnboardingStore.markCompleted()
                    navController.navigate(AppRoute.Home.route) {
                        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // The four tabs slide sideways under a bottom bar that never moves, so the bar reads as
        // a fixed shell the pages travel behind.
        tabComposable(AppRoute.Home.route) { entry ->
            // The one place this app asks for notification permission, and the only line that has
            // to move to ask somewhere else.
            //
            // Here because it is the first screen that is the app rather than a preamble: the
            // splash is behind it and the intro pages, on a first launch, are behind that. Asking
            // during either would be asking before the user has seen what they are being asked
            // for. The call itself is a no-op after the first install that reaches this point —
            // see PushNotifications.requestPermissionOnce.
            LaunchedEffect(Unit) { PushNotifications.requestPermissionOnce() }

            // Flights are searched through the Travelpayouts SDK on both platforms now: an .aar
            // registered from the Application on Android, a CocoaPods framework registered from
            // iOSApp.swift on iOS. The card above it is untouched either way — this only changes
            // where its answer goes.
            val openSdkResults = rememberFlightResultsLauncher()
            HomeScreen(
                onSearch = { search ->
                    // Which of the two a flight search takes is the backend's call, not this
                    // build's: `new_tab` is KYK_DL for the supplier's own page and TPT_SDK for the
                    // SDK's screen. Read per search rather than once, because the controller lands
                    // after the first frame does.
                    val mode = AppController.flightSearchMode
                    if (mode == FlightSearchMode.Api) {
                        // Results fetched and drawn in-app. The screen reports its own session the
                        // moment it opens the search, so nothing is recorded here — see
                        // FlightApiResultsViewModel.
                        navController.navigate(AppRoute.FlightApiResults.route)
                    } else if (mode == FlightSearchMode.Deeplink) {
                        // The screen reports its own session once it has built the URL, so nothing
                        // is recorded here — see FlightResultsViewModel.
                        navController.navigate(AppRoute.FlightResults.route)
                    } else if (openSdkResults(search)) {
                        // Only once something has taken it: a search the SDK refused never left for
                        // a supplier, and recording it would count an exit that did not happen.
                        SessionTracking.flightSearch(search)
                        // Beside the backend's own record of the same handoff, never apart from
                        // it: GA4 counts the exit for the dashboard and `users/session` counts it
                        // for the payout, and the two have to agree about what an exit is.
                        FlightAnalytics.exitClick(
                            vertical = AnalyticsEvent.FLIGHT,
                            exit = AnalyticsEvent.EXIT_SDK,
                            screenName = AnalyticsEvent.HOME_SCREEN,
                        )
                    } else {
                        // Nothing took it: the SDK is not configured on this build, or it refused
                        // the search. There is no in-app flight results page to fall back to, so
                        // the route is only taken if one is ever registered again — navigating to
                        // a destination that is not in the graph would take the app down.
                        val route = AppRoute.SearchResults.createRoute(
                            fromCode = search.from.code,
                            toCode = search.to.code,
                            date = search.departureDate,
                        )
                        if (navController.graph.findNode(route) != null) {
                            navController.navigate(route)
                        }
                    }
                },
                onOpenCalendar = navController::openCalendar,
                pickedDates = entry.rememberDateSelection(),
                onPickedDatesHandled = entry::clearDateSelection,
                onOpenLocationPicker = navController::openLocationPicker,
                pickedLocation = entry.rememberLocationSelection(),
                onPickedLocationHandled = entry::clearLocationSelection,
                onOpenTravellerPicker = { party ->
                    navController.navigate(
                        AppRoute.TravellerPicker.createRoute(party.encodeTravellers()),
                    )
                },
                pickedTravellers = entry.rememberTravellerSelection(),
                onPickedTravellersHandled = entry::clearTravellerSelection,
            )
        }

        tabComposable(AppRoute.Hotels.route) { entry ->
            HotelsScreen(
                onSearch = { navController.navigate(AppRoute.HotelResults.route) },
                onOpenCalendar = navController::openCalendar,
                pickedDates = entry.rememberDateSelection(),
                onPickedDatesHandled = entry::clearDateSelection,
                onOpenGuestPicker = { rooms ->
                    navController.navigate(AppRoute.GuestPicker.createRoute(rooms.encodeRooms()))
                },
                pickedGuests = entry.rememberGuestSelection(),
                onPickedGuestsHandled = entry::clearGuestSelection,
                onOpenDestinationPicker = {
                    navController.navigate(AppRoute.HotelDestinationPicker.route)
                },
                pickedDestination = entry.rememberHotelDestination(),
                onPickedDestinationHandled = entry::clearHotelDestination,
            )
        }

        tabComposable(AppRoute.Cars.route) { entry ->
            CarsScreen(
                onSearch = { navController.navigate(AppRoute.CarResults.route) },
                onOpenCalendar = navController::openCalendar,
                pickedDates = entry.rememberDateSelection(),
                onPickedDatesHandled = entry::clearDateSelection,
                onOpenLocationPicker = { target ->
                    navController.navigate(AppRoute.CarLocationPicker.createRoute(target))
                },
                pickedLocation = entry.rememberCarLocationSelection(),
                onPickedLocationHandled = entry::clearCarLocationSelection,
            )
        }

        tabComposable(AppRoute.Profile.route) {
            ProfileScreen(
                onFavorites = { navController.navigate(AppRoute.Favorites.route) },
                onPriceDisplay = { navController.navigate(AppRoute.PriceDisplay.route) },
                // Straight to each list — the page that used to sit between them is gone.
                onRegion = { navController.navigate(AppRoute.RegionPicker.createRoute(RegionKind.Country.name)) },
                onCurrency = { navController.navigate(AppRoute.RegionPicker.createRoute(RegionKind.Currency.name)) },
                onLanguage = { navController.navigate(AppRoute.RegionPicker.createRoute(RegionKind.Language.name)) },
                onAboutApp = { navController.navigate(AppRoute.AboutApp.route) },
            )
        }

        composable(AppRoute.PriceDisplay.route) {
            PriceDisplayScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = AppRoute.RegionPicker.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.RegionPicker.KindArg) { type = NavType.StringType },
            ),
        ) { entry ->
            RegionPickerScreen(
                kind = RegionKind.fromRoute(entry.stringArg(AppRoute.RegionPicker.KindArg)),
                onBack = { navController.navigateUp() },
            )
        }

        composable(AppRoute.AboutApp.route) {
            AboutAppScreen(onBack = { navController.navigateUp() })
        }

        // Saved flights moved off the bottom bar; it is now pushed from Profile.
        composable(AppRoute.Favorites.route) {
            FavoritesScreen(
                onBack = { navController.navigateUp() },
                onFlightClick = { },
            )
        }


        composable(
            route = AppRoute.Calendar.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.Calendar.ModeArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.TitleArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.StartArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.EndArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.WithTimeArg) { type = NavType.BoolType },
                navArgument(AppRoute.Calendar.StartTimeArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.EndTimeArg) { type = NavType.StringType },
                navArgument(AppRoute.Calendar.SpanUnitArg) { type = NavType.StringType },
            ),
        ) { entry ->
            CalendarScreen(
                title = entry.stringArg(AppRoute.Calendar.TitleArg).orEmpty().decodeRouteArg(),
                mode = CalendarMode.fromRoute(entry.stringArg(AppRoute.Calendar.ModeArg)),
                initialStart = AppRoute.Calendar.readDate(entry.stringArg(AppRoute.Calendar.StartArg)),
                initialEnd = AppRoute.Calendar.readDate(entry.stringArg(AppRoute.Calendar.EndArg)),
                withTime = entry.boolArg(AppRoute.Calendar.WithTimeArg),
                initialStartTime = AppRoute.Calendar.readDate(
                    entry.stringArg(AppRoute.Calendar.StartTimeArg),
                ),
                initialEndTime = AppRoute.Calendar.readDate(
                    entry.stringArg(AppRoute.Calendar.EndTimeArg),
                ),
                spanUnit = entry.stringArg(AppRoute.Calendar.SpanUnitArg).orEmpty()
                    .ifBlank { "nights" },
                onBack = { navController.navigateUp() },
                onConfirm = { selection ->
                    // Arguments only travel forwards, so the answer goes into the caller's saved
                    // state, which is where the search tab is watching for it.
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.Calendar.ResultStartKey] = selection.start
                        handle[AppRoute.Calendar.ResultEndKey] = selection.end
                        handle[AppRoute.Calendar.ResultStartTimeKey] = selection.startTime
                        handle[AppRoute.Calendar.ResultEndTimeKey] = selection.endTime
                    }
                    navController.navigateUp()
                },
            )
        }

        composable(
            route = AppRoute.LocationPicker.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.LocationPicker.KindArg) { type = NavType.StringType },
                navArgument(AppRoute.LocationPicker.TitleArg) { type = NavType.StringType },
                navArgument(AppRoute.LocationPicker.TargetArg) { type = NavType.StringType },
                navArgument(AppRoute.LocationPicker.ExcludeArg) { type = NavType.StringType },
            ),
        ) { entry ->
            LocationPickerScreen(
                title = entry.stringArg(AppRoute.LocationPicker.TitleArg).orEmpty().decodeRouteArg(),
                kind = LocationKind.fromRoute(entry.stringArg(AppRoute.LocationPicker.KindArg)),
                target = entry.stringArg(AppRoute.LocationPicker.TargetArg).orEmpty(),
                excludeId = AppRoute.LocationPicker.readId(
                    entry.stringArg(AppRoute.LocationPicker.ExcludeArg),
                ),
                onBack = { navController.navigateUp() },
                onSelect = { selection ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.LocationPicker.ResultTargetKey] = selection.target
                        handle[AppRoute.LocationPicker.ResultDestinationKey] =
                            selection.destination.encodeSelection()
                    }
                    navController.navigateUp()
                },
            )
        }

        composable(AppRoute.HotelDestinationPicker.route) {
            HotelDestinationScreen(
                onBack = { navController.navigateUp() },
                onSelect = { destination ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.HotelDestinationPicker.ResultDestinationKey] =
                            destination.encodeSelection()
                    }
                    navController.navigateUp()
                },
            )
        }

        composable(
            route = AppRoute.CarLocationPicker.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.CarLocationPicker.TargetArg) { type = NavType.StringType },
            ),
        ) { entry ->
            val target = entry.stringArg(AppRoute.CarLocationPicker.TargetArg).orEmpty()
            CarLocationScreen(
                target = target,
                onBack = { navController.navigateUp() },
                onSelect = { location ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.CarLocationPicker.ResultTargetKey] = target
                        handle[AppRoute.CarLocationPicker.ResultLocationKey] =
                            location.encodeSelection()
                    }
                    navController.navigateUp()
                },
            )
        }

        composable(AppRoute.FlightResults.route) {
            FlightResultsScreen(onBack = { navController.navigateUp() })
        }

        composable(AppRoute.FlightApiResults.route) { entry ->
            // The SDK is the fallback when the backend will not run an API search for this build,
            // and it has to be remembered inside the destination that uses it.
            val openSdkResults = rememberFlightResultsLauncher()

            // The same three pickers the home search card uses, answering through this entry's
            // saved state exactly as they answer through the tab's. Applied into the Modify
            // Search draft and then cleared, so returning here later does not re-apply a stale
            // pick — the identical contract the search card follows.
            val viewModel: FlightApiResultsViewModel = viewModel { FlightApiResultsViewModel() }

            // Resolved in the destination's own composition: the picker callbacks below are
            // ordinary lambdas, where `stringResource` cannot be called.
            val flyingFromTitle = stringResource(Res.string.flying_from)
            val flyingToTitle = stringResource(Res.string.flying_to)

            entry.rememberLocationSelection()?.let { picked ->
                LaunchedEffect(picked) {
                    val airport = picked.destination.toAirport()
                    when (picked.target) {
                        LocationTargets.Origin -> viewModel.draftOrigin(airport)
                        LocationTargets.Destination -> viewModel.draftDestination(airport)
                    }
                    entry.clearLocationSelection()
                }
            }
            entry.rememberDateSelection()?.let { picked ->
                LaunchedEffect(picked) {
                    viewModel.draftDates(picked.start, picked.end)
                    entry.clearDateSelection()
                }
            }
            entry.rememberTravellerSelection()?.let { picked ->
                LaunchedEffect(picked) {
                    viewModel.draftParty(
                        adults = picked.adults,
                        children = picked.children,
                        infants = picked.infants,
                        cabinClass = picked.cabinClass,
                    )
                    entry.clearTravellerSelection()
                }
            }

            FlightApiResultsScreen(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenDeal = { deal ->
                    // Handed over rather than encoded into the route — see SelectedFlightDeal.
                    SelectedFlightDeal.set(deal)
                    navController.navigate(AppRoute.FlightDeal.route)
                },
                // An ad goes to the same web view a deal does: both are a supplier's own page,
                // and the reference app's separate ad screen exists only because its deal page is
                // not one. The URL arrives already resolved — see FlightApiResultsViewModel.
                onOpenAd = { url, advertiser ->
                    SelectedFlightDeal.set(url, advertiser)
                    navController.navigate(AppRoute.FlightDeal.route)
                },
                onPickOrigin = { draft ->
                    navController.openLocationPicker(
                        LocationRequest(
                            title = flyingFromTitle,
                            kind = LocationKind.Airport,
                            target = LocationTargets.Origin,
                            // Each end hides the other, so a trip cannot be made to start and end
                            // in one place — the same guard the search card applies.
                            excludeId = draft.to.code,
                        ),
                    )
                },
                onPickDestination = { draft ->
                    navController.openLocationPicker(
                        LocationRequest(
                            title = flyingToTitle,
                            kind = LocationKind.Airport,
                            target = LocationTargets.Destination,
                            excludeId = draft.from.code,
                        ),
                    )
                },
                onPickDates = { draft ->
                    navController.openCalendar(
                        CalendarRequest(
                            title = if (draft.isRoundTrip) "Travel dates" else "Departure date",
                            mode = if (draft.isRoundTrip) CalendarMode.Range else CalendarMode.Single,
                            start = draft.departureDate,
                            end = draft.returnDate.takeIf { draft.isRoundTrip },
                            // A return flight's gap is counted in days; only a stay is in nights.
                            spanUnit = "days",
                        ),
                    )
                },
                onPickTravellers = { draft ->
                    navController.navigate(
                        AppRoute.TravellerPicker.createRoute(
                            TravellerSelection(
                                adults = draft.adults,
                                children = draft.children,
                                infants = draft.infants,
                                cabinClass = draft.cabinClass,
                            ).encodeTravellers(),
                        ),
                    )
                },
                onUnavailable = {
                    // The controller put this build on the API tab, but the search endpoint
                    // refuses it — the provider's key is disabled, or none is configured for this
                    // country. Nothing here can fix that, and leaving the user on an error page
                    // they cannot act on would be the worst of the options. So the search goes to
                    // the SDK, which does not depend on that configuration at all.
                    //
                    // Popped first, so the back button returns to the search card rather than to
                    // the dead results screen the user was just bounced off.
                    val search = RecentSearchStore.flights.value.firstOrNull()
                    navController.popBackStack()
                    if (search != null && openSdkResults(search)) {
                        SessionTracking.flightSearch(search)
                        // Reported from the results screen the user was bounced off rather than
                        // from Home, which is where they will land: the exit happened here.
                        FlightAnalytics.exitClick(
                            vertical = AnalyticsEvent.FLIGHT,
                            exit = AnalyticsEvent.EXIT_SDK,
                            screenName = AnalyticsEvent.RESULT_SCREEN,
                        )
                    }
                },
            )
        }

        composable(AppRoute.FlightDeal.route) {
            FlightDealScreen(
                url = SelectedFlightDeal.url,
                title = SelectedFlightDeal.title,
                onBack = { navController.navigateUp() },
            )
        }

        composable(AppRoute.HotelResults.route) {
            HotelResultsScreen(onBack = { navController.navigateUp() })
        }

        composable(AppRoute.CarResults.route) {
            CarResultsScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = AppRoute.GuestPicker.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.GuestPicker.RoomsArg) { type = NavType.StringType },
            ),
        ) { entry ->
            GuestSelectionScreen(
                initialRooms = decodeStayRooms(entry.stringArg(AppRoute.GuestPicker.RoomsArg)),
                onBack = { navController.navigateUp() },
                onConfirm = { selection ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.GuestPicker.ResultRoomsKey] = selection.rooms.encodeRooms()
                    }
                    navController.navigateUp()
                },
            )
        }

        composable(
            route = AppRoute.TravellerPicker.RouteWithArg,
            arguments = listOf(
                navArgument(AppRoute.TravellerPicker.PartyArg) { type = NavType.StringType },
            ),
        ) { entry ->
            TravellerSelectionScreen(
                initial = decodeTravellerSelection(
                    entry.stringArg(AppRoute.TravellerPicker.PartyArg),
                ),
                onBack = { navController.navigateUp() },
                onConfirm = { selection ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[AppRoute.TravellerPicker.ResultPartyKey] =
                            selection.encodeTravellers()
                    }
                    navController.navigateUp()
                },
            )
        }
    }
}

/**
 * Tab destinations travel sideways rather than push: they are siblings, so the motion follows the
 * bar — tapping a tab further right brings its page in from the right. Pushing *off* a tab
 * (Home -> search results) is a hierarchy change and still slides the full width, so the
 * graph-level spec is only overridden when the other side of the transition is a tab too.
 */
private fun NavGraphBuilder.tabComposable(
    route: String,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        enterTransition = {
            if (initialState.isTab) tabEnter(movingRight()) else pushEnter()
        },
        exitTransition = {
            if (targetState.isTab) tabExit(movingRight()) else pushExit()
        },
        popEnterTransition = {
            if (initialState.isTab) tabEnter(movingRight()) else popEnter()
        },
        popExitTransition = {
            if (targetState.isTab) tabExit(movingRight()) else popExit()
        },
        content = { entry -> content(entry) },
    )
}

/**
 * Which way along the bar this switch is going. Read off the tab order rather than the back stack:
 * tab navigation reuses saved entries, so whether the library calls a switch a push or a pop says
 * nothing about the direction the user sees.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.movingRight(): Boolean =
    targetState.tabIndex > initialState.tabIndex

/** Position in the bottom bar, left to right; -1 for anything the bar does not carry. */
private val NavBackStackEntry.tabIndex: Int
    get() = BottomTab.entries.indexOfFirst { it.route == destination.route }

private val NavBackStackEntry.isTab: Boolean
    get() = destination.route in BottomBarRoutes

private fun NavHostController.openCalendar(request: CalendarRequest) {
    navigate(
        AppRoute.Calendar.createRoute(
            mode = request.mode.name,
            title = request.title,
            start = request.start,
            end = request.end,
            withTime = request.withTime,
            startTime = request.startTime,
            endTime = request.endTime,
            spanUnit = request.spanUnit,
        ),
    )
}

/**
 * Watches this entry's saved state for an answer from the calendar page. Null until the picker
 * confirms; the caller clears it with [clearDateSelection] once applied, so returning to the tab
 * later does not re-apply a stale pick.
 */
@Composable
private fun NavBackStackEntry.rememberDateSelection(): DateSelection? {
    val start by savedStateHandle
        .getStateFlow<String?>(AppRoute.Calendar.ResultStartKey, null)
        .collectAsStateWithLifecycle()
    val end by savedStateHandle
        .getStateFlow<String?>(AppRoute.Calendar.ResultEndKey, null)
        .collectAsStateWithLifecycle()
    val startTime by savedStateHandle
        .getStateFlow<String?>(AppRoute.Calendar.ResultStartTimeKey, null)
        .collectAsStateWithLifecycle()
    val endTime by savedStateHandle
        .getStateFlow<String?>(AppRoute.Calendar.ResultEndTimeKey, null)
        .collectAsStateWithLifecycle()
    return start?.let {
        DateSelection(start = it, end = end, startTime = startTime, endTime = endTime)
    }
}

private fun NavBackStackEntry.clearDateSelection() {
    savedStateHandle.remove<String>(AppRoute.Calendar.ResultStartKey)
    savedStateHandle.remove<String>(AppRoute.Calendar.ResultEndKey)
    savedStateHandle.remove<String>(AppRoute.Calendar.ResultStartTimeKey)
    savedStateHandle.remove<String>(AppRoute.Calendar.ResultEndTimeKey)
}

private fun NavHostController.openLocationPicker(request: LocationRequest) {
    navigate(
        AppRoute.LocationPicker.createRoute(
            kind = request.kind.name,
            title = request.title,
            target = request.target,
            excludeId = request.excludeId,
        ),
    )
}

/** The location counterpart of [rememberDateSelection]; same clear-once-applied contract. */
@Composable
private fun NavBackStackEntry.rememberLocationSelection(): LocationSelection? {
    val target by savedStateHandle
        .getStateFlow<String?>(AppRoute.LocationPicker.ResultTargetKey, null)
        .collectAsStateWithLifecycle()
    val encoded by savedStateHandle
        .getStateFlow<String?>(AppRoute.LocationPicker.ResultDestinationKey, null)
        .collectAsStateWithLifecycle()
    val pickedTarget = target ?: return null
    val pickedDestination = decodeFlightDestination(encoded) ?: return null
    return LocationSelection(target = pickedTarget, destination = pickedDestination)
}

private fun NavBackStackEntry.clearLocationSelection() {
    savedStateHandle.remove<String>(AppRoute.LocationPicker.ResultTargetKey)
    savedStateHandle.remove<String>(AppRoute.LocationPicker.ResultDestinationKey)
}

/**
 * The stays counterpart of [rememberLocationSelection]; same clear-once-applied contract. There is
 * only the one destination field on that card, so the answer is the picked row on its own.
 */
@Composable
private fun NavBackStackEntry.rememberHotelDestination(): HotelDestination? {
    val encoded by savedStateHandle
        .getStateFlow<String?>(AppRoute.HotelDestinationPicker.ResultDestinationKey, null)
        .collectAsStateWithLifecycle()
    return decodeHotelDestination(encoded)
}

private fun NavBackStackEntry.clearHotelDestination() {
    savedStateHandle.remove<String>(AppRoute.HotelDestinationPicker.ResultDestinationKey)
}

/**
 * The rental counterpart of [rememberLocationSelection]; same clear-once-applied contract, but the
 * answer carries the whole picked row rather than an id — see [AppRoute.CarLocationPicker].
 */
@Composable
private fun NavBackStackEntry.rememberCarLocationSelection(): CarLocationSelection? {
    val target by savedStateHandle
        .getStateFlow<String?>(AppRoute.CarLocationPicker.ResultTargetKey, null)
        .collectAsStateWithLifecycle()
    val encoded by savedStateHandle
        .getStateFlow<String?>(AppRoute.CarLocationPicker.ResultLocationKey, null)
        .collectAsStateWithLifecycle()
    val pickedTarget = target ?: return null
    val pickedLocation = decodeCarLocation(encoded) ?: return null
    return CarLocationSelection(target = pickedTarget, location = pickedLocation)
}

private fun NavBackStackEntry.clearCarLocationSelection() {
    savedStateHandle.remove<String>(AppRoute.CarLocationPicker.ResultTargetKey)
    savedStateHandle.remove<String>(AppRoute.CarLocationPicker.ResultLocationKey)
}

/** The guests counterpart of [rememberDateSelection]; same clear-once-applied contract. */
@Composable
private fun NavBackStackEntry.rememberGuestSelection(): GuestSelection? {
    val encoded by savedStateHandle
        .getStateFlow<String?>(AppRoute.GuestPicker.ResultRoomsKey, null)
        .collectAsStateWithLifecycle()
    val picked = encoded ?: return null
    return GuestSelection.of(decodeStayRooms(picked))
}

/** The travellers counterpart of [rememberDateSelection]; same clear-once-applied contract. */
@Composable
private fun NavBackStackEntry.rememberTravellerSelection(): TravellerSelection? {
    val encoded by savedStateHandle
        .getStateFlow<String?>(AppRoute.TravellerPicker.ResultPartyKey, null)
        .collectAsStateWithLifecycle()
    return encoded?.let { decodeTravellerSelection(it) }
}

private fun NavBackStackEntry.clearTravellerSelection() {
    savedStateHandle.remove<String>(AppRoute.TravellerPicker.ResultPartyKey)
}

private fun NavBackStackEntry.clearGuestSelection() {
    savedStateHandle.remove<String>(AppRoute.GuestPicker.ResultRoomsKey)
}

private fun NavBackStackEntry.stringArg(key: String): String? {
    return arguments?.read { getStringOrNull(key) }
}

private fun NavBackStackEntry.boolArg(key: String): Boolean {
    return arguments?.read { if (contains(key)) getBoolean(key) else false } ?: false
}
