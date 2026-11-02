package com.gocheap.travel.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.model.Flight
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.component.DashedFlightPath
import com.gocheap.travel.ui.component.EmptyState
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.InfoChip
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightShapes
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.no_saved_flights
import flighttp.shared.generated.resources.remove_from_favorites
import flighttp.shared.generated.resources.saved_flights
import flighttp.shared.generated.resources.tap_the_heart_on_any_flight_to

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = viewModel { FavoritesViewModel() },
    onBack: () -> Unit = {},
    onFlightClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FavoritesContent(
        state = state,
        onBack = onBack,
        onFlightClick = onFlightClick,
        onRemoveFavorite = viewModel::onRemoveFavorite,
        modifier = modifier,
    )
}

@Composable
private fun FavoritesContent(
    state: FavoritesUiState,
    onBack: () -> Unit,
    onFlightClick: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    InnerPage(
        title = stringResource(Res.string.saved_flights),
        onBack = onBack,
        modifier = modifier,
    ) {

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = FlightColors.Primary, strokeWidth = 3.dp)
            }

            state.flights.isEmpty() -> EmptyState(
                icon = FlightIcons.HeartOutlined,
                title = stringResource(Res.string.no_saved_flights),
                message = stringResource(Res.string.tap_the_heart_on_any_flight_to),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = FlightSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.flights, key = { it.id }) { flight ->
                    FavoriteCard(
                        flight = flight,
                        onClick = { onFlightClick(flight.id) },
                        onRemove = { onRemoveFavorite(flight.id) },
                    )
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    flight: Flight,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .border(1.dp, FlightColors.Border, FlightShapes.Card)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(FlightColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FlightIcons.Flight,
                    contentDescription = null,
                    tint = FlightColors.Primary,
                    modifier = Modifier.size(18.dp).rotate(45f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flight.airline,
                    style = MaterialTheme.typography.titleMedium,
                    color = FlightColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${flight.flightNumber} · ${flight.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
            }
            Icon(
                imageVector = FlightIcons.HeartFilled,
                contentDescription = stringResource(Res.string.remove_from_favorites),
                tint = FlightColors.Primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(6.dp)
                    .size(22.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flight.from.code,
                style = MaterialTheme.typography.headlineMedium,
                color = FlightColors.TextPrimary,
            )
            DashedFlightPath(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(
                text = flight.to.code,
                style = MaterialTheme.typography.headlineMedium,
                color = FlightColors.TextPrimary,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoChip(text = flight.durationLabel)
            InfoChip(
                text = flight.stopsLabel,
                containerColor = FlightColors.SurfaceMuted,
                contentColor = FlightColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$${flight.priceUsd}",
                style = MaterialTheme.typography.headlineSmall,
                color = FlightColors.Primary,
            )
        }
    }
}
