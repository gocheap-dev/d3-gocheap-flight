package com.gocheap.travel.ui.home.results.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.still_finding_fares
import flighttp.shared.generated.resources.searching_label
import flighttp.shared.generated.resources.filters_count
import org.jetbrains.compose.resources.pluralStringResource

/** Which sheet the filter bar has been asked to open. */
internal enum class FlightFilterSheet {
    Sort,
    Stops,
    Times,
    Airlines,
    Agencies,
    Price,
    Duration,
}

/**
 * The line under the bar: how many results match, and whether more are still arriving.
 *
 * The count is the backend's total across every page, not the number of cards loaded — it is what
 * tells the user whether a filter did anything, and `options.size` would only ever say "20".
 */
@Composable
internal fun FlightResultsSummary(
    totalCount: Int,
    isCollecting: Boolean,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = resultCountLabel(totalCount).ifEmpty { stringResource(Res.string.searching_label) },
            style = MaterialTheme.typography.titleSmall,
            color = FlightColors.TextPrimary,
        )
        if (activeFilterCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· " + pluralStringResource(Res.plurals.filters_count, activeFilterCount, activeFilterCount),
                style = MaterialTheme.typography.labelSmall,
                color = FlightColors.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isCollecting) {
            // A pulse rather than a spinner: the list under it is usable and already filling, and
            // a spinner on a working screen reads as something being waited for.
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(FlightColors.Primary),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.still_finding_fares),
                style = MaterialTheme.typography.labelSmall,
                color = FlightColors.TextSecondary,
            )
        }
    }
}
