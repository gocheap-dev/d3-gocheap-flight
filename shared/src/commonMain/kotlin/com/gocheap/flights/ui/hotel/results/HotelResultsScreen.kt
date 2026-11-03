package com.gocheap.flights.ui.hotel.results

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
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.StayRoom
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
import flighttp.shared.generated.resources.comparing_prices_across_booking_sites
import flighttp.shared.generated.resources.no_search_to_show
import flighttp.shared.generated.resources.start_a_stay_search_and_the_results
import flighttp.shared.generated.resources.stays
import flighttp.shared.generated.resources.searching_stays_in
import flighttp.shared.generated.resources.searching_stays_plain

/**
 * Stay results.
 *
 * The supplier has no results API — it has a results page — so this screen is a web view over the
 * deeplink the ViewModel builds, with a loader held over the top until the page is worth looking
 * at. That is the same shape as the reference app's StayResultScreen.
 *
 * The web view is mounted and loading from the moment the URL exists, just drawn at zero alpha
 * underneath the loader: hiding it rather than withholding it means the page is already rendered
 * when the loader lifts, instead of starting from blank at that point.
 */
@Composable
fun HotelResultsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelResultsViewModel = viewModel { HotelResultsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HotelResultsContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun HotelResultsContent(
    state: HotelResultsUiState,
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
            state.hasNoSearch -> InnerPage(title = stringResource(Res.string.stays), onBack = onBack) {
                EmptyState(
                    icon = FlightIcons.HotelOutlined,
                    title = stringResource(Res.string.no_search_to_show),
                    message = stringResource(Res.string.start_a_stay_search_and_the_results),
                )
            }

            !isPageReady -> SearchLoader(
                // The city is the ViewModel's; the sentence around it is translated here.
                label = state.destinationCity.takeIf(String::isNotEmpty)
                    ?.let { stringResource(Res.string.searching_stays_in, it) }
                    ?: stringResource(Res.string.searching_stays_plain),
                caption = stringResource(Res.string.comparing_prices_across_booking_sites),
                icon = FlightIcons.HotelOutlined,
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview
@Composable
private fun HotelResultsScreenLoadingPreview() {
    FlightTheme {
        HotelResultsContent(
            state = HotelResultsUiState(
                search = HotelSearch(
                    city = "Paris",
                    checkInDate = "Oct 15",
                    checkOutDate = "Oct 20",
                    nights = 5,
                    rooms = listOf(StayRoom(adults = 2)),
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
private fun HotelResultsScreenNoSearchPreview() {
    FlightTheme {
        HotelResultsContent(
            state = HotelResultsUiState(hasNoSearch = true),
            onBack = {},
        )
    }
}
