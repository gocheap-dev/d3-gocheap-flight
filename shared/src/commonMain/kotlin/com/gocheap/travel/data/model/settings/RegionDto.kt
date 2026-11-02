package com.gocheap.travel.data.model.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the bundled region catalogue, in the shape the file actually has.
 *
 * The reference app's `assets/Countries.json`, carried over unchanged so that refreshing it later
 * is a file copy rather than a transcription — the same arrangement the popular-location files
 * already use.
 *
 * Everything is nullable because the file says so: a third of its rows carry no flag, most carry
 * no `domain`, and `supported_languages` is sometimes `null` and sometimes `[]` for the same
 * meaning. A row that cannot name a country or a currency is dropped on the way in.
 */
@Serializable
internal data class RegionDto(
    @SerialName("name") val name: String? = null,
    @SerialName("code") val code: String? = null,
    @SerialName("currency") val currency: RegionCurrencyDto? = null,
    @SerialName("flag") val flag: String? = null,
    /** The supplier's own site for this country. Unused today; kept so a refresh does not lose it. */
    @SerialName("domain") val domain: String? = null,
    @SerialName("supported_languages") val supportedLanguages: List<String>? = null,
)

/**
 * How a country's prices are written.
 *
 * The same seven fields the poll endpoint sends as `currency_info`, which is not a coincidence —
 * both come from the supplier. Only [code] and [symbol] are read today; the rest are carried so
 * that formatting a price off the catalogue, rather than off whatever the last response happened
 * to say, is a change of mind rather than a change of file.
 */
@Serializable
internal data class RegionCurrencyDto(
    @SerialName("code") val code: String? = null,
    @SerialName("symbol") val symbol: String? = null,
    @SerialName("thousands_separator") val thousandsSeparator: String? = null,
    @SerialName("decimal_separator") val decimalSeparator: String? = null,
    @SerialName("symbol_on_left") val symbolOnLeft: Boolean? = null,
    @SerialName("space_between_amount_and_symbol") val spaceBetweenAmountAndSymbol: Boolean? = null,
    @SerialName("decimal_digits") val decimalDigits: Int? = null,
)
