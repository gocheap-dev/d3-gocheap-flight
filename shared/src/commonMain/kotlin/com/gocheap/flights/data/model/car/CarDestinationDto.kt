package com.gocheap.flights.data.model.car

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the rental autocomplete response, exactly as the supplier sends it.
 *
 * The car endpoint is a different version from the stay one and answers with its own shape — an
 * `iata` code and a `deeplink_name` the stay rows never carry — so this is kept apart from
 * [com.gocheap.flights.data.model.hotel.HotelDestinationDto] rather than shared with it.
 *
 * Every field is nullable with a default because the shape varies by [type] — a city carries no
 * `iata`, an airport carries no `city_id` of its own — and a missing key must not fail the whole
 * response.
 */
@Serializable
internal data class CarDestinationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("city_id") val cityId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("iata") val iata: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("deeplink_name") val deeplinkName: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("type") val type: String? = null,
)

/**
 * Narrows the response to what the rental picker can actually show.
 *
 * A row with no name has nothing to render or hand back, so it is dropped rather than listed blank.
 * Properties go the same way: the endpoint answers with hotels mixed in, and a rental is collected
 * from a city depot or an airport desk, never from someone's lobby.
 */
internal fun CarDestinationDto.toCarLocation(): CarLocation? {
    val label = name?.takeIf(String::isNotBlank) ?: return null
    val kind = CarLocationType.fromApi(type) ?: return null
    val supplierId = id?.takeIf(String::isNotBlank)
    val city = cityId?.takeIf(String::isNotBlank)
    return CarLocation(
        // Joined rather than picked: two rows can share an id across id_types, and a collision here
        // is a duplicate-key crash in the list rather than a wrong row.
        id = (listOfNotNull(supplierId, city, iata?.takeIf(String::isNotBlank)) + label)
            .joinToString(separator = "-"),
        name = label,
        location = location.orEmpty(),
        type = kind,
        supplierId = supplierId,
        idType = idType?.takeIf(String::isNotBlank),
        cityId = city,
        iata = iata?.takeIf(String::isNotBlank),
        deeplinkName = deeplinkName?.takeIf(String::isNotBlank),
        apiType = type?.takeIf(String::isNotBlank),
        imageUrl = image?.takeIf(String::isNotBlank),
    )
}
