package com.gocheap.flights.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.GlassSurface
import com.gocheap.flights.ui.component.bottomBarInset
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.car_rental
import flighttp.shared.generated.resources.cars
import flighttp.shared.generated.resources.flights
import flighttp.shared.generated.resources.hotels
import flighttp.shared.generated.resources.profile

/** Cast upwards by the bar, which the reference draws in white against the page colour. */
private val BarShadowColor = Color(0xFF143C6B)

/** The four tabs carried by the bottom bar: flights, stays, cars, account. */
enum class BottomTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /**
     * Drawn under the icon; kept to one short word so four of them fit a narrow screen.
     *
     * The resource rather than the resolved text: an enum entry is built once, before any
     * composition exists to read a locale from, so resolving here would freeze whichever language
     * happened to be set when the class first loaded.
     */
    val label: StringResource,
    val contentDescription: StringResource,
) {
    Home(
        route = AppRoute.Home.route,
        selectedIcon = FlightIcons.Flight,
        unselectedIcon = FlightIcons.FlightOutlined,
        label = Res.string.flights,
        contentDescription = Res.string.flights,
    ),
    Hotels(
        route = AppRoute.Hotels.route,
        selectedIcon = FlightIcons.HotelFilled,
        unselectedIcon = FlightIcons.HotelOutlined,
        label = Res.string.hotels,
        contentDescription = Res.string.hotels,
    ),
    Cars(
        route = AppRoute.Cars.route,
        selectedIcon = FlightIcons.CarFilled,
        unselectedIcon = FlightIcons.CarOutlined,
        label = Res.string.cars,
        contentDescription = Res.string.car_rental,
    ),
    Profile(
        route = AppRoute.Profile.route,
        selectedIcon = FlightIcons.PersonFilled,
        unselectedIcon = FlightIcons.PersonOutlined,
        label = Res.string.profile,
        contentDescription = Res.string.profile,
    ),
}

/**
 * The bar is anchored to the bottom edge and runs the full width, as the reference draws it —
 * rounded across the top two corners only, with the gesture inset carried inside the panel so it
 * runs behind the handle rather than stopping above it. How much of that inset the tabs stand off
 * by is [bottomBarInset], which differs per platform.
 *
 * The panel behind the tabs comes from [GlassSurface]: solid white on Android, frosted glass on
 * iOS. Only the backing differs — the tabs themselves are the same on both. [backdrop] carries the
 * page content the glass blurs; Android ignores it.
 */
@Composable
fun FlightBottomBar(
    currentRoute: String?,
    onTabSelected: (BottomTab) -> Unit,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        topCornerRadius = FlightShapes.BottomBarCorner,
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = FlightShapes.BottomBar,
                ambientColor = BarShadowColor,
                spotColor = BarShadowColor,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomBarInset())
                .height(FlightSpacing.BottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Equal weights rather than fixed padding, so the tabs stay evenly set on any width.
            BottomTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The live tab goes near-black — the same ink as the card's action button — and sits on a soft
    // tinted pill. Colour alone is a weak mark on a white bar with four outlines on it; the pill is
    // what makes which tab you are on readable at a glance.
    val switch = tween<Color>(
        durationMillis = NavMotion.BarDurationMillis,
        easing = NavMotion.BarEasing,
    )
    val tint by animateColorAsState(
        targetValue = if (selected) FlightColors.Action else FlightColors.TextTertiary,
        animationSpec = switch,
    )
    val indicator by animateColorAsState(
        targetValue = if (selected) {
            FlightColors.Action.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = switch,
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(FlightShapes.Pill)
                .background(indicator)
                .padding(horizontal = 18.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                // The label beneath carries the name; describing the icon as well would have a
                // screen reader say it twice.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(tab.label),
            // Weight carries the selection, not size — a larger live label would shift the row.
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
