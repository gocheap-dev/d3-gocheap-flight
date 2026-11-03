package com.gocheap.flights.ui.car.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.flights.data.model.CarSearch
import com.gocheap.flights.ui.component.EmptyState
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.ResultsWebView
import androidx.compose.ui.unit.dp
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.component.SearchLoader
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.utils.Constants
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.cars
import flighttp.shared.generated.resources.comparing_prices_across_rental_suppliers
import flighttp.shared.generated.resources.no_search_to_show
import flighttp.shared.generated.resources.start_a_car_search_and_the_results
import flighttp.shared.generated.resources.searching_cars_in
import flighttp.shared.generated.resources.searching_cars_plain

/**
 * Rental results.
 *
 * The same shape as [com.gocheap.flights.ui.hotel.results.HotelResultsScreen], because the supplier
 * is the same kind of thing: no results API, a results page, so this is a web view over the deeplink
 * the ViewModel builds with a loader held over the top until the page is worth looking at.
 *
 * The web view is mounted and loading from the moment the URL exists, just drawn at zero alpha
 * underneath the loader: hiding it rather than withholding it means the page is already rendered
 * when the loader lifts, instead of starting from blank at that point.
 */
@Composable
fun CarResultsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarResultsViewModel = viewModel { CarResultsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarResultsContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun CarResultsContent(
    state: CarResultsUiState,
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
            state.hasNoSearch -> InnerPage(title = stringResource(Res.string.cars), onBack = onBack) {
                EmptyState(
                    icon = FlightIcons.CarOutlined,
                    title = stringResource(Res.string.no_search_to_show),
                    message = stringResource(Res.string.start_a_car_search_and_the_results),
                )
            }

            !isPageReady -> SearchLoader(
                // The city is the ViewModel's; the sentence around it is translated here.
                label = state.destinationCity.takeIf(String::isNotEmpty)
                    ?.let { stringResource(Res.string.searching_cars_in, it) }
                    ?: stringResource(Res.string.searching_cars_plain),
                caption = stringResource(Res.string.comparing_prices_across_rental_suppliers),
                icon = FlightIcons.CarOutlined,
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


@Preview
@Composable
private fun CarResultsScreenLoadingPreview() {
    FlightTheme {
        CarResultsContent(
            state = CarResultsUiState(
                search = CarSearch(
                    city = "San Francisco",
                    pickupPoint = "SFO International Airport",
                    pickupDate = "Oct 15",
                    dropoffDate = "Oct 18",
                    days = 3,
                    pickupTime = "10:00",
                    dropoffTime = "10:00",
                    dropoffCity = null,
                    dropoffPoint = null,
                ),
                resultUrl = "https://example.com",
            ),
            onBack = {},
            initialProgress = 0.4f,
        )
    }
}

@Preview
@Composable
private fun CarResultsScreenNoSearchPreview() {
    FlightTheme {
        CarResultsContent(
            state = CarResultsUiState(hasNoSearch = true),
            onBack = {},
        )
    }
}
