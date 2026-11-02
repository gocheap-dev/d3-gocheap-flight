package com.gocheap.travel.data.model.flight

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the flight autocomplete response, exactly as the supplier sends it.
 *
 * Every field is nullable with a default because the shape varies by [type] — a city row carries no
 * `parent_iata` — and a missing key must not fail the whole response.
 *
 * The same shape backs the shipped suggestion list, which is a saved response: that file carries no
 * `country_code` and spells its picture [images], a jpeg-then-webp pair, where the live endpoint
 * sends a single [image]. Both are read, so one DTO covers the two sources.
 */
@Serializable
internal data class FlightDestinationDto(
    @SerialName("iata") val iata: String? = null,
    @SerialName("parent_iata") val parentIata: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("images") val images: List<String> = emptyList(),
)

/**
 * Narrows the response to what the picker can actually show.
 *
 * A row with no code or no name is dropped: the first cannot be searched on and the second has
 * nothing to render, and neither is worth listing blank.
 */
internal fun FlightDestinationDto.toDestination(): FlightDestination? {
    val code = iata?.takeIf(String::isNotBlank) ?: return null
    val label = name?.takeIf(String::isNotBlank) ?: return null
    return FlightDestination(
        iata = code.uppercase(),
        name = label,
        location = location.orEmpty(),
        type = FlightDestinationType.fromApi(type),
        parentIata = parentIata?.takeIf(String::isNotBlank)?.uppercase(),
        countryCode = countryCode?.takeIf(String::isNotBlank)?.uppercase(),
        imageUrl = imageUrl(),
    )
}

/**
 * The one picture to draw, out of however many the source offered.
 *
 * The pair in the shipped file is the same photograph twice, jpeg first and webp second; the webp
 * is the smaller download and both platforms decode it, so the last wins — which is also the one
 * the reference app picks.
 */
private fun FlightDestinationDto.imageUrl(): String? = image?.takeIf(String::isNotBlank)
    ?: images.lastOrNull { it.isNotBlank() }
