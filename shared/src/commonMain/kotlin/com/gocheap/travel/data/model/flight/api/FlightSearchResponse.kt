package com.gocheap.travel.data.model.flight.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `POST api/v3/search/` answers with: an id to poll on, and how the results it will produce
 * are going to be denominated.
 *
 * The results themselves are not here — the endpoint only opens the search. [searchId] is the
 * whole point of the call and everything downstream keys on it; a response without one is a
 * search the backend declined to run, which the ViewModel reads as "no flights" rather than as an
 * error, because that is what it is.
 */
@Serializable
internal data class FlightSearchResponse(
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("mode") val mode: Int? = null,
    @SerialName("currency_info") val currencyInfo: CurrencyInfo? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("currency") val currency: String? = null,
    @SerialName("provider") val provider: String? = null,
)

/**
 * How to render a price in the currency this search was run in.
 *
 * Every field matters to the output: "$1,234.50" and "1 234,50 €" differ in the symbol, which side
 * it sits on, whether a space separates it, and both separators. Formatting by hand off these is
 * what [com.gocheap.travel.ui.home.results.api.formatPrice] does — common code has no locale-aware
 * number formatter, and taking the platform's would ignore everything the backend just said.
 *
 * Non-null fields: the backend sends this object whole or not at all, and a half-populated one
 * would format worse than the fallback the UI uses when it is absent.
 */
@Serializable
internal data class CurrencyInfo(
    @SerialName("code") val code: String,
    @SerialName("symbol") val symbol: String,
    @SerialName("thousands_separator") val thousandsSeparator: String,
    @SerialName("decimal_separator") val decimalSeparator: String,
    @SerialName("symbol_on_left") val symbolOnLeft: Boolean,
    @SerialName("space_between_amount_and_symbol") val spaceBetweenAmountAndSymbol: Boolean,
    @SerialName("decimal_digits") val decimalDigits: Int,
)

/** What `DELETE api/search/delete/` answers with. Nothing downstream reads it; the call is the point. */
@Serializable
internal data class DeleteSearchResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("message") val message: String? = null,
)
