package com.gocheap.flights.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.flights.data.settings.AppSettingsState
import com.gocheap.flights.data.settings.Regions
import com.gocheap.flights.ui.component.EmptyState
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.try_changing_your_search
import flighttp.shared.generated.resources.clear_search
import flighttp.shared.generated.resources.selected
import flighttp.shared.generated.resources.app_language
import flighttp.shared.generated.resources.country_not_found
import flighttp.shared.generated.resources.country_region
import flighttp.shared.generated.resources.currency_2
import flighttp.shared.generated.resources.currency_not_found
import flighttp.shared.generated.resources.language_not_found
import flighttp.shared.generated.resources.search_country
import flighttp.shared.generated.resources.search_currencies
import flighttp.shared.generated.resources.search_languages

/** Which of the three regional lists a picker is showing. */
enum class RegionKind {
    Country,
    Currency,
    Language,
    ;

    /**
     * Resolved by the screen, not here: an enum property is reachable from outside a composition,
     * and `stringResource` is not.
     */
    val title: StringResource
        get() = when (this) {
            Country -> Res.string.country_region
            Currency -> Res.string.currency_2
            Language -> Res.string.app_language
        }

    val searchHint: StringResource
        get() = when (this) {
            Country -> Res.string.search_country
            Currency -> Res.string.search_currencies
            Language -> Res.string.search_languages
        }

    val notFound: StringResource
        get() = when (this) {
            Country -> Res.string.country_not_found
            Currency -> Res.string.currency_not_found
            Language -> Res.string.language_not_found
        }

    companion object {
        /** Route arguments arrive as text; anything unrecognised opens the country list. */
        fun fromRoute(raw: String?): RegionKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Country
    }
}

/** One row of whichever list is open: what it is called, and the code that is actually stored. */
private data class RegionRow(val code: String, val title: String, val subtitle: String)

/**
 * The searchable list behind each regional setting.
 *
 * One screen for all three rather than three near-identical ones: they differ in the rows they
 * hold and nothing else, and the SDK's own country and currency pages are the same page twice.
 * Picking writes straight through and pops — there is no confirm, because there is nothing to
 * confirm.
 */
@Composable
fun RegionPickerScreen(
    kind: RegionKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    RegionPickerContent(
        kind = kind,
        settings = settings,
        onBack = onBack,
        onPick = { code ->
            when (kind) {
                RegionKind.Country -> viewModel.onCountryPicked(code)
                RegionKind.Currency -> viewModel.onCurrencyPicked(code)
                RegionKind.Language -> viewModel.onLanguagePicked(code)
            }
            onBack()
        },
        modifier = modifier,
    )
}

@Composable
private fun RegionPickerContent(
    kind: RegionKind,
    settings: AppSettingsState,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val rows = remember(kind, query) { kind.rows().matching(query) }
    val selectedCode = when (kind) {
        RegionKind.Country -> settings.countryCode
        RegionKind.Currency -> settings.currencyCode
        RegionKind.Language -> settings.languageCode
    }

    InnerPage(
        title = stringResource(kind.title),
        onBack = onBack,
        modifier = modifier,
    ) {

        SearchField(
            query = query,
            placeholder = stringResource(kind.searchHint),
            onQueryChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
        )

        Spacer(Modifier.height(14.dp))

        if (rows.isEmpty()) {
            EmptyState(
                icon = FlightIcons.Search,
                title = stringResource(kind.notFound),
                message = stringResource(Res.string.try_changing_your_search),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = FlightSpacing.ScreenHorizontal, vertical = 2.dp)) {
                items(rows, key = { it.code }) { row ->
                    RegionRowItem(
                        row = row,
                        isSelected = row.code.equals(selectedCode, ignoreCase = true),
                        onClick = { onPick(row.code) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RegionRowItem(
    row: RegionRow,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
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
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            // Omitted entirely rather than drawn empty: a currency whose only name is its code
            // has nothing to put here, and a blank line would leave the row taller than its
            // neighbours for no reason.
            if (row.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.subtitle,
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

/** The same field the location pickers use, so every search box in the app looks the same. */
@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Field)
            .border(1.dp, FlightColors.Border, FlightShapes.Field)
            .background(FlightColors.SurfaceMuted)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = FlightIcons.Search,
            contentDescription = null,
            tint = FlightColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // BasicTextField has no placeholder slot, so it sits underneath the empty field.
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FlightColors.TextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = FlightColors.TextPrimary),
                ),
                cursorBrush = SolidColor(FlightColors.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(FlightColors.BorderStrong)
                    .clickable(onClickLabel = stringResource(Res.string.clear_search), onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FlightIcons.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun RegionKind.rows(): List<RegionRow> = when (this) {
    RegionKind.Country -> Regions.countries.map { country ->
        // The flag leads the name, which is how the reference app's list reads and what makes a
        // 235-row list scannable at all — the eye finds a flag long before it reads a name.
        val title = country.flag.takeIf(String::isNotBlank)
            ?.let { "$it  ${country.name}" }
            ?: country.name
        // The currency rides on the second line because picking a country is mostly a decision
        // about what prices will be quoted in, and otherwise that is invisible until you look.
        val subtitle = country.currencyCode.takeIf(String::isNotBlank)
            ?.let { "${country.code} · $it" }
            ?: country.code
        RegionRow(country.code, title, subtitle)
    }

    RegionKind.Currency -> Regions.currencies.map { currency ->
        // A currency with no confirmed name is titled by its code, and then the second line would
        // only repeat it — so it carries the symbol alone, or nothing where the catalogue's
        // "symbol" is the code over again.
        val named = currency.name != currency.code
        val subtitle = when {
            named && currency.symbol != currency.code -> "${currency.code} · ${currency.symbol}"
            named -> currency.code
            currency.symbol != currency.code -> currency.symbol
            else -> ""
        }
        RegionRow(currency.code, currency.name, subtitle)
    }

    RegionKind.Language -> Regions.languages.map { RegionRow(it.code, it.name, it.endonym) }
}

/**
 * Matched on the code as well as both lines, so "GBP", "pound" and "£" all find the same row —
 * people search a currency list by whichever of the three they happen to know.
 */
private fun List<RegionRow>.matching(query: String): List<RegionRow> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { row ->
        row.title.contains(trimmed, ignoreCase = true) ||
            row.subtitle.contains(trimmed, ignoreCase = true) ||
            row.code.contains(trimmed, ignoreCase = true)
    }
}

@Preview
@Composable
private fun RegionPickerScreenPreview() {
    FlightTheme {
        RegionPickerContent(
            kind = RegionKind.Currency,
            settings = AppSettingsState(),
            onBack = {},
            onPick = {},
        )
    }
}
