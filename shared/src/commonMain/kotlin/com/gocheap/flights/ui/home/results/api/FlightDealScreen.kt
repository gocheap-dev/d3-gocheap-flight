package com.gocheap.flights.ui.home.results.api

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
import androidx.compose.ui.unit.dp
import com.gocheap.flights.ui.component.EmptyState
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.component.ResultsWebView
import com.gocheap.flights.ui.component.SearchLoader
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.utils.Constants
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.taking_you_to_the_booking_site
import flighttp.shared.generated.resources.the_booking_site_didn_t_provide_a
import flighttp.shared.generated.resources.this_deal_can_t_be_opened
import flighttp.shared.generated.resources.opening_seller

/**
 * The booking site's own checkout, for a deal picked off the in-app results.
 *
 * Where the API mode hands back over to the web. The itineraries and their prices are this app's
 * to draw; the booking is not — the money, the passenger details and whatever the seller needs to
 * take them belong on the seller's page, under the seller's name.
 *
 * The same web view the deeplink and stay results use, with the same loader held over the top
 * until the page is worth looking at.
 *
 * The toolbar carries the app's own name rather than the seller's. The page underneath belongs to
 * the seller and is covered in their branding; the bar above it is the one strip of screen that is
 * still this app, and repeating their name there says nothing the page does not already say.
 *
 * [title] is the seller's name, used only by the loader — "Opening Mytrip" is worth saying while
 * there is nothing else on screen to say whose checkout is on its way.
 */
@Composable
internal fun FlightDealScreen(
    url: String?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val isPageReady = progress >= 1f

    Box(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        if (!url.isNullOrBlank()) {
            // Flush under the title bar, as the other web results are: the seller's page brings
            // its own margins and clearance here would only stack on top of them.
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
            // The deal carried no link. The sheet filters these out, so this is only reachable if
            // the screen is opened directly — there is nothing to retry, only a way back.
            url.isNullOrBlank() -> InnerPage(title = Constants.App.DisplayName, onBack = onBack) {
                EmptyState(
                    icon = FlightIcons.Globe,
                    title = stringResource(Res.string.this_deal_can_t_be_opened),
                    message = stringResource(Res.string.the_booking_site_didn_t_provide_a),
                )
            }

            !isPageReady -> SearchLoader(
                label = stringResource(Res.string.opening_seller, title.ifBlank { Constants.App.DisplayName }),
                caption = stringResource(Res.string.taking_you_to_the_booking_site),
                icon = FlightIcons.TicketOutlined,
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
