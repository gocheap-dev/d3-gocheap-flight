package com.gocheap.flights.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gocheap.flights.ui.component.AppLogoMark
import com.gocheap.flights.ui.component.PrimaryButton
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightTheme
import com.gocheap.flights.utils.Constants
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.about_the_app
import flighttp.shared.generated.resources.app_version_value
import flighttp.shared.generated.resources.about_body

/**
 * The SDK's About page: what this is, which build it is, and one way out to the website.
 *
 * The version comes from the platform rather than from a constant, so it cannot drift from what the
 * store actually shipped — which is the only thing this page is really for when a report comes in.
 */
@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = rememberProfileActions()

    AboutAppContent(
        appVersion = actions.appVersion,
        onBack = onBack,
        onVisitWebsite = { actions.openUrl(Constants.Support.WebsiteUrl) },
        modifier = modifier,
    )
}

@Composable
private fun AboutAppContent(
    appVersion: String,
    onBack: () -> Unit,
    onVisitWebsite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InnerPage(
        title = stringResource(Res.string.about_the_app),
        onBack = onBack,
        modifier = modifier,
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The one place the mark is not on white: the About page's own surface is white, so
            // a white tile on it would have no edge at all.
            AppLogoMark(
                size = 88.dp,
                background = FlightColors.PrimarySoft,
                shape = FlightShapes.Sheet,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = Constants.App.DisplayName,
                style = MaterialTheme.typography.displaySmall,
                color = FlightColors.TextPrimary,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.app_version_value, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
            )

            Spacer(Modifier.height(18.dp))

            Text(
                // The reference app's About copy, rewritten rather than reworded: same four
                // beats — travel is hard work, everything in one place, one search instead of
                // many, and a vision of serving travellers everywhere — in English that reads
                // like a person wrote it. Its own version came through a spinner ("confounding
                // locales", "give the best proposals to flights all over") and could not be
                // lifted as-is.
                //
                // It claims only what the app does. There is no eSIM feature in this build, so
                // nothing here promises one.
                // One resource, not sentences glued together: a translator needs the whole
                // paragraph to order it naturally, and concatenating fragments would force every
                // language into English word order.
                text = stringResource(Res.string.about_body, Constants.App.DisplayName),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = FlightColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun AboutAppScreenPreview() {
    FlightTheme {
        AboutAppContent(appVersion = "1.0", onBack = {}, onVisitWebsite = {})
    }
}
