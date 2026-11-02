package com.gocheap.travel.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.component.AppLogoMark
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.SkyHeader
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightTheme
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.utils.Constants
import kotlinx.coroutines.launch
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.board_with_your_phone
import flighttp.shared.generated.resources.your_boarding_pass_lives_in_ready_offline
import flighttp.shared.generated.resources.choose_a_window_an_aisle_or_extra
import flighttp.shared.generated.resources.compare_every_airline_on_your_route_and
import flighttp.shared.generated.resources.find_the_right_fare
import flighttp.shared.generated.resources.pick_your_seat
import flighttp.shared.generated.resources.skip
import flighttp.shared.generated.resources.get_started
import flighttp.shared.generated.resources.next

/**
 * One onboarding page. Carries its resources rather than its text, for the same reason
 * [com.gocheap.travel.navigation.BottomTab] does: this list is built at class-load time, long
 * before there is a composition to read the device's language from.
 */
private data class OnboardingPage(
    val icon: ImageVector,
    val iconRotation: Float,
    val title: StringResource,
    val message: StringResource,
)

private val OnboardingPages = listOf(
    OnboardingPage(
        icon = FlightIcons.Search,
        iconRotation = 0f,
        title = Res.string.find_the_right_fare,
        message = Res.string.compare_every_airline_on_your_route_and,
    ),
    OnboardingPage(
        icon = FlightIcons.Seat,
        iconRotation = 0f,
        title = Res.string.pick_your_seat,
        message = Res.string.choose_a_window_an_aisle_or_extra,
    ),
    OnboardingPage(
        icon = FlightIcons.TicketFilled,
        iconRotation = 0f,
        title = Res.string.board_with_your_phone,
        message = Res.string.your_boarding_pass_lives_in_ready_offline,
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == OnboardingPages.lastIndex

    Box(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box {
                SkyHeader(height = 380.dp, showPlane = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogoMark(size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = Constants.App.DisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.skip),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                // Reported here rather than inside [onFinished], which the host
                                // also calls from the last page: the whole value of the event is
                                // that it separates the two.
                                FlightAnalytics.onboardingComplete(skipped = true)
                                onFinished()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageContent(page = OnboardingPages[page])
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = FlightSpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingPages.indices.forEach { index ->
                    PageIndicator(selected = index == pagerState.currentPage)
                    if (index != OnboardingPages.lastIndex) Spacer(Modifier.width(7.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (isLastPage) stringResource(Res.string.get_started) else stringResource(Res.string.next),
                onClick = {
                    if (isLastPage) {
                        FlightAnalytics.onboardingComplete(skipped = false)
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
            )

            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(FlightColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = FlightColors.Primary,
                modifier = Modifier.size(42.dp).rotate(page.iconRotation),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.displaySmall,
            color = FlightColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            // The app's name is passed as a format argument rather than written into the
            // string: a translator should not have to retype a brand, and a rename would
            // otherwise have to be made in all 29 languages.
            text = stringResource(page.message, Constants.App.DisplayName),
            style = MaterialTheme.typography.bodyLarge,
            color = FlightColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(selected: Boolean) {
    val width by animateDpAsState(if (selected) 26.dp else 8.dp)
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(CircleShape)
            .background(if (selected) FlightColors.Primary else FlightColors.BorderStrong),
    )
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    FlightTheme {
        OnboardingScreen()
    }
}

