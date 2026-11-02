package com.gocheap.travel.ui.home.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.model.Airport
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.ui.component.EmptyState
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.component.ResultsWebView
import com.gocheap.travel.ui.component.SearchLoader
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.utils.Constants
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.comparing_prices_across_booking_sites
import flighttp.shared.generated.resources.flights
import flighttp.shared.generated.resources.no_search_to_show
import flighttp.shared.generated.resources.start_a_flight_search_and_the_results
import flighttp.shared.generated.resources.searching_flights_to
import flighttp.shared.generated.resources.searching_flights_plain

/**
 * Flight results, for the deeplink half of the controller's choice.
 *
 * The same screen as the stay and rental results and for the same reason: the supplier has a
 * results page rather than a results API, so this is a web view over the URL the ViewModel builds,
 * with a loader held over the top until the page is worth looking at.
 *
 * Only reachable when `new_tab` says `KYK_DL`. On `TPT_SDK` the search never gets here — the SDK
 * opens its own screen, and this app is not in the loop after that.
 */
@Composable
fun FlightResultsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlightResultsViewModel = viewModel { FlightResultsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FlightResultsContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun FlightResultsContent(
    state: FlightResultsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialProgress: Float = 0f,
) {
    var progress by remember { mutableFloatStateOf(initialProgress) }
    val url = state.resultUrl
    val isPageReady = progress >= 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlightColors.Background),
    ) {
        if (url != null) {
            // The supplier's own page rides the sheet, flush under its lip: it brings its own
            // margins, so clearance here would only stack on top of them.
            InnerPage(
                title = Constants.App.DisplayName,
                onBack = onBack,
                modifier = Modifier.alpha(if (isPageReady) 1f else 0f),
                contentTop = 0.dp,
            ) {
                ResultsWebView(
                    url = url,
                    onProgress = { progress = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        when {
            // Nothing was recorded to search for — reachable only if this screen is opened without
            // a search having been run, so there is nothing to retry, only somewhere to go back to.
            state.hasNoSearch -> InnerPage(title = stringResource(Res.string.flights), onBack = onBack) {
                EmptyState(
                    icon = FlightIcons.FlightOutlined,
                    title = stringResource(Res.string.no_search_to_show),
                    message = stringResource(Res.string.start_a_flight_search_and_the_results),
                )
            }

            !isPageReady -> SearchLoader(
                // The city is the ViewModel's; the sentence around it is translated here.
                label = state.destinationCity.takeIf(String::isNotEmpty)
                    ?.let { stringResource(Res.string.searching_flights_to, it) }
                    ?: stringResource(Res.string.searching_flights_plain),
                caption = stringResource(Res.string.comparing_prices_across_booking_sites),
                icon = FlightIcons.FlightOutlined,
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


@Preview
@Composable
private fun FlightResultsScreenLoadingPreview() {
    FlightTheme {
        FlightResultsContent(
            state = FlightResultsUiState(
                search = TripSearch(
                    from = Airport(code = "LON", city = "London", name = "London"),
                    to = Airport(code = "NYC", city = "New York", name = "New York"),
                    departureDate = "Oct 15",
                    returnDate = "Oct 20",
                    isRoundTrip = true,
                ),
                resultUrl = "https://example.com",
            ),
            onBack = {},
            initialProgress = 0.4f,
        )
    }
}
