package com.gocheap.travel.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.settings.AppSettingsState
import com.gocheap.travel.ui.component.BottomBarSpacer
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.InnerPageScaffold
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightTheme
import com.gocheap.travel.utils.Constants
import com.gocheap.travel.utils.SupportLinks
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.about_the_app
import flighttp.shared.generated.resources.contact_us
import flighttp.shared.generated.resources.currency
import flighttp.shared.generated.resources.display_prices
import flighttp.shared.generated.resources.language
import flighttp.shared.generated.resources.other
import flighttp.shared.generated.resources.personal_info
import flighttp.shared.generated.resources.privacy
import flighttp.shared.generated.resources.profile
import flighttp.shared.generated.resources.rate_this_app
import flighttp.shared.generated.resources.region
import flighttp.shared.generated.resources.share_this_app

/**
 * The Profile tab, built as the Travelpayouts SDK builds its own.
 *
 * The SDK ships this screen but exposes no way to open it — `TravelSdkUnlimited` hands out its two
 * results screens and nothing else — so this is its list rebuilt natively: the same two groups, the
 * same rows in the same order, drawn in this app's theme and backed by this app's settings store.
 *
 * What was here before — a mock passenger, trip counters and seven rows that each answered "not
 * wired up in this build" — is gone. Every row below does something.
 */
@Composable
fun ProfileScreen(
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
    onFavorites: () -> Unit = {},
    onPriceDisplay: () -> Unit = {},
    /**
     * One per list, rather than one page holding all three.
     *
     * The reference app puts Region, Currency and Language straight on this page, and it is the
     * better arrangement: each is a single choice with its current value already shown beside it,
     * so the page they used to live behind was a menu of three rows that said nothing this one
     * does not.
     */
    onRegion: () -> Unit = {},
    onCurrency: () -> Unit = {},
    onLanguage: () -> Unit = {},
    onAboutApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberProfileActions()

    ProfileContent(
        settings = settings,
        appVersion = actions.appVersion,
        onFavorites = onFavorites,
        onPriceDisplay = onPriceDisplay,
        onRegion = onRegion,
        onCurrency = onCurrency,
        onLanguage = onLanguage,
        onAboutApp = onAboutApp,
        onRateApp = { actions.openUrl(actions.storeUrl) },
        // The store link travels with the invitation; a share that only names the app leaves the
        // other person searching for it.
        onShareApp = { actions.share("${Constants.Support.ShareMessage} ${actions.storeUrl}") },
        onContactUs = { actions.openUrl(Constants.Support.mailtoUrl()) },
        onPrivacy = { actions.openUrl(SupportLinks.PrivacyPolicyUrl) },
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    settings: AppSettingsState,
    appVersion: String,
    onFavorites: () -> Unit,
    onPriceDisplay: () -> Unit,
    onRegion: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onAboutApp: () -> Unit,
    onRateApp: () -> Unit,
    onShareApp: () -> Unit,
    onContactUs: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InnerPageScaffold(
        modifier = modifier,
        header = {
            Text(
                // Matches the bottom bar's label for this tab, which is what the user tapped to
                // get here — a page whose heading disagrees with the tab reads as a wrong turn.
                text = stringResource(Res.string.profile),
                style = MaterialTheme.typography.displaySmall,
                color = FlightColors.TextPrimary,
                modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 14.dp),
            )
        },
    ) {
        // The list scrolls inside the sheet rather than the sheet scrolling with it, so the band
        // above stays put the way it does on the other tabs.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Column(modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal)) {
                SettingsSectionHeader(title = stringResource(Res.string.personal_info))

               /* SettingsRow(
                    icon = FlightIcons.HeartOutlined,
                    title = "Favorites",
                    onClick = onFavorites,
                )
                Spacer(Modifier.height(10.dp))*/
                SettingsRow(
                    icon = FlightIcons.TicketOutlined,
                    title = stringResource(Res.string.display_prices),
                    preview = stringResource(settings.flightPrices.summaryLabel) +
                        " · " + stringResource(settings.stayPrices.summaryLabel),
                    onClick = onPriceDisplay,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.PlaceOutlined,
                    title = stringResource(Res.string.region),
                    // The flag as well as the name: it is what the picker leads each row with, so
                    // the value shown here and the row that is selected there look like each other.
                    preview = settings.country.let { country ->
                        country.flag.takeIf(String::isNotBlank)?.let { "$it  ${country.name}" }
                            ?: country.name
                    },
                    onClick = onRegion,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.CreditCard,
                    title = stringResource(Res.string.currency),
                    preview = "${settings.currency.code}  ${settings.currency.symbol}",
                    onClick = onCurrency,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.Globe,
                    title = stringResource(Res.string.language),
                    // The endonym, which is how someone looking for their own language finds it.
                    preview = settings.language.endonym,
                    onClick = onLanguage,
                )

                Spacer(Modifier.height(18.dp))
                SettingsSectionHeader(title = stringResource(Res.string.other))

                // These four leave the app, so they carry no chevron: nothing here opens a page behind
                // the one being looked at.
                SettingsRow(
                    icon = FlightIcons.Star,
                    title = stringResource(Res.string.rate_this_app),
                    onClick = onRateApp,
                    showChevron = false,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.Share,
                    title = stringResource(Res.string.share_this_app),
                    onClick = onShareApp,
                    showChevron = false,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.Mail,
                    title = stringResource(Res.string.contact_us),
                    onClick = onContactUs,
                    showChevron = false,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.Shield,
                    title = stringResource(Res.string.privacy),
                    onClick = onPrivacy,
                    showChevron = false,
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = FlightIcons.Help,
                    title = stringResource(Res.string.about_the_app),
                    preview = appVersion,
                    onClick = onAboutApp,
                )
            }

            BottomBarSpacer()
        }
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    FlightTheme {
        ProfileContent(
            settings = AppSettingsState(),
            appVersion = "1.0",
            onFavorites = {},
            onPriceDisplay = {},
            onRegion = {},
            onCurrency = {},
            onLanguage = {},
            onAboutApp = {},
            onRateApp = {},
            onShareApp = {},
            onContactUs = {},
            onPrivacy = {},
        )
    }
}
