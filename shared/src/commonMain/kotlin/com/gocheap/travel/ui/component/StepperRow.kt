package com.gocheap.travel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.theme.FlightColors
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.add_named
import flighttp.shared.generated.resources.remove_named

/**
 * Icon, label, one-line explanation and a +/- pair. Shared by the counting pages — guests and
 * rooms on the hotel side, travellers on the flight side.
 *
 * The buttons are disabled rather than hidden at the limits, so the row does not reflow as the
 * count reaches either end.
 */
@Composable
fun StepperRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlightColors.TextTertiary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
            )
        }
        StepButton(symbol = "−", enabled = canDecrease, description = stringResource(Res.string.remove_named, title), onClick = onDecrease)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = FlightColors.TextPrimary,
            textAlign = TextAlign.Center,
            // Fixed width so the buttons do not shuffle when the count reaches two digits.
            modifier = Modifier.width(44.dp),
        )
        StepButton(symbol = "+", enabled = canIncrease, description = stringResource(Res.string.add_named, title), onClick = onIncrease)
    }
}

@Composable
private fun StepButton(
    symbol: String,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) FlightColors.PrimarySoft else FlightColors.SurfaceMuted)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) FlightColors.Primary else FlightColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
