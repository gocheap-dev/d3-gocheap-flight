package com.gocheap.travel.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightShapes
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.selected

/**
 * The parts the Profile pages are built from: a group heading, a row that opens something, and
 * a row that is one of a set of choices.
 *
 * The SDK draws these as a flat list with grey captions between groups; this app has cards and a
 * soft-tinted icon tile, so the structure is the SDK's and the surface is this app's.
 */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = FlightColors.TextSecondary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/**
 * [preview] is the setting's current value, shown greyed at the end of the row the way the SDK's
 * regional settings list reads back what is chosen without having to be opened.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: String? = null,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .border(1.dp, FlightColors.Border, FlightShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(FlightColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FlightColors.Primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (preview != null) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Capped so a long region line cannot push the title out of the row.
                modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = FlightIcons.ChevronRight,
                contentDescription = null,
                tint = FlightColors.TextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One of a set: a title, an optional second line, and a tick when it is the one in force. */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .background(if (isSelected) FlightColors.PrimarySoft else FlightColors.Surface)
            .border(
                width = 1.dp,
                color = if (isSelected) FlightColors.Primary else FlightColors.Border,
                shape = FlightShapes.Card,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = FlightIcons.Check,
                contentDescription = stringResource(Res.string.selected),
                tint = FlightColors.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
