package com.gocheap.flights.data.model

/**
 * A place the user can search in, rendered by the "Popular Locations" row on every search tab.
 *
 * [id] is whatever the owning repository keys its searches on — an airport code for flights, a city
 * name for stays and rentals — so a tap can be resolved back into a real search without the row
 * having to know which tab it is sitting on.
 */
data class LocationSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    /**
     * A photograph of the place, when whatever supplied the row also supplied one. Null leaves the
     * card on its icon tile, which is what the flight and stay rows still draw.
     */
    val imageUrl: String? = null,
)
