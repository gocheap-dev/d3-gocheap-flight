package com.gocheap.travel.data.model.hotel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of the autocomplete response, exactly as the supplier sends it.
 *
 * Every field is nullable with a default because the shape varies by [type] — a city carries no
 * `hotel_id`, a property carries no `city_id` of its own — and a missing key must not fail the whole
 * response.
 */
@Serializable
internal data class HotelDestinationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("hotel_id") val hotelId: String? = null,
    @SerialName("city_id") val cityId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("type") val type: String? = null,
    /**
     * Only ever sent on the shipped suggestion list, never by autocomplete — which is why it is
     * nullable like everything else here rather than a reason for a second type.
     */
    @SerialName("destination_images") val images: HotelDestinationImagesDto? = null,
)

/**
 * The same photograph twice, in the two formats the supplier encodes it as. Unlike the rental list,
 * which sends one flat `image` URL, the stay list makes the caller choose.
 */
@Serializable
internal data class HotelDestinationImagesDto(
    @SerialName("image_webp") val webp: String? = null,
    @SerialName("image_jpeg") val jpeg: String? = null,
) {
    /** WebP for the bytes, JPEG only if the supplier left the WebP off a row. */
    val preferred: String? get() = webp?.takeIf(String::isNotBlank) ?: jpeg?.takeIf(String::isNotBlank)
}

/**
 * Narrows the response to what the picker can actually show.
 *
 * A row with no name has nothing to render or hand back, so it is dropped rather than listed blank —
 * which is why this returns null instead of a destination with an empty title.
 */
internal fun HotelDestinationDto.toDestination(): HotelDestination? {
    val label = name?.takeIf(String::isNotBlank) ?: return null
    val supplierId = id?.takeIf(String::isNotBlank)
    val hotel = hotelId?.takeIf(String::isNotBlank)
    val city = cityId?.takeIf(String::isNotBlank)
    return HotelDestination(
        // Joined rather than picked: two rows can share an id across id_types, and a collision here
        // is a duplicate-key crash in the list rather than a wrong row.
        id = (listOfNotNull(supplierId, hotel, city) + label).joinToString(separator = "-"),
        name = label,
        location = location.orEmpty(),
        type = HotelDestinationType.fromApi(type),
        parentId = parentId?.takeIf(String::isNotBlank),
        supplierId = supplierId,
        hotelId = hotel,
        cityId = city,
        idType = idType?.takeIf(String::isNotBlank),
        imageUrl = images?.preferred,
    )
}
